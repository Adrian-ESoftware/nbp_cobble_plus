package com.nbp.cobbleplus.feature.impl

import com.cobblemon.mod.common.api.battles.model.actor.ActorType
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.pokemon.Natures
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.reactive.ObservableSubscription
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.pokemon.Pokemon
import com.nbp.cobbleplus.config.MissionsConfigFile
import com.nbp.cobbleplus.config.NbpConfig
import com.nbp.cobbleplus.feature.FeatureModule
import com.nbp.cobbleplus.i18n.PlayerLanguage
import com.nbp.cobbleplus.mission.MissionAction
import com.nbp.cobbleplus.mission.MissionCycle
import com.nbp.cobbleplus.mission.MissionGenerator
import com.nbp.cobbleplus.mission.MissionInstance
import com.nbp.cobbleplus.mission.MissionMatcher
import com.nbp.cobbleplus.mission.MissionRewardRoller
import com.nbp.cobbleplus.mission.RewardRollResult
import com.nbp.cobbleplus.network.MissionViewRow
import com.nbp.cobbleplus.network.RewardViewRow
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID

/**
 * Sistema de missões diárias (por jogador) e semanais (server-wide, a 1ª conclusão trava
 * e concede as recompensas aos demais). Gera missões das definições de [MissionsConfigFile],
 * acompanha progresso nos eventos de captura/derrota e concede as recompensas da dificuldade.
 */
object MissionsFeature : FeatureModule {
    override val name = "Missions system"
    override val isEnabled get() = NbpConfig.data.missions.enabled

    private val subscriptions = mutableListOf<ObservableSubscription<*>>()
    private val store = MissionsStore()
    private var serverRef: MinecraftServer? = null
    private var nextWeeklyCheck = 0L

    /** Barra de HUD de recompensa (mesmo canal do PointsRewardSyncPayload). Ligado por cada plataforma. */
    var networkSender: (ServerPlayer, String, Int) -> Unit = { _, _, _ -> }

    /** Abre a tela de missões (idioma resolvido + linhas das abas Diárias e Semanais). */
    var viewNetworkSender: (ServerPlayer, Boolean, List<MissionViewRow>, List<MissionViewRow>) -> Unit = { _, _, _, _ -> }

    private val allTypes by lazy { ElementalTypes.all().map { it.name } }
    private val allNatures by lazy { Natures.all().map { it.name.path } }

    // ─── Ciclo de vida ──────────────────────────────────────────────────────────

    override fun onEnable() {
        if (subscriptions.isNotEmpty()) return
        subscriptions += CobblemonEvents.POKEMON_CAPTURED.subscribe { event ->
            if (!isEnabled) return@subscribe
            val player = serverRef?.playerList?.getPlayer(event.player.uuid) ?: return@subscribe
            track(player, MissionAction.CAPTURE, event.pokemon)
        }
        subscriptions += CobblemonEvents.BATTLE_FAINTED.subscribe { event ->
            if (!isEnabled) return@subscribe
            if (!event.battle.isPvW || event.killed.actor.type != ActorType.WILD) return@subscribe
            if (isRaidBoss(event.killed)) return@subscribe
            val players = event.battle.players.distinctBy { it.uuid }
            if (players.isEmpty()) return@subscribe
            players.forEach { player -> track(player, MissionAction.DEFEAT, event.killed.effectedPokemon) }
        }
    }

    override fun onDisable() {
        subscriptions.forEach { it.unsubscribe() }
        subscriptions.clear()
    }

    fun bindServer(server: MinecraftServer) {
        store.bind(server)
        serverRef = server
    }

    fun unbindServer() {
        store.unbind()
        serverRef = null
    }

    /** Chamado a cada tick pelos hooks de plataforma; checa a janela semanal no máximo 1x/min. */
    fun tick(server: MinecraftServer) {
        if (!isEnabled) return
        val now = System.currentTimeMillis()
        if (now < nextWeeklyCheck) return
        nextWeeklyCheck = now + 60_000L
        ensureWeeklyIfStale(server)
    }

    // ─── Eventos / progresso ────────────────────────────────────────────────────

    private fun track(player: ServerPlayer, action: MissionAction, pokemon: Pokemon) {
        val server = serverRef ?: return
        runCatching {
            ensureDailyWindow(player, server)
            ensureWeeklyIfStale(server)
        }
        val speciesPath = pokemon.species.resourceIdentifier.path
        val types = pokemon.types.map { it.name }
        val naturePath = pokemon.effectiveNature.name.path
        val data = store.require()
        trackDaily(player, action, speciesPath, types, naturePath, data)
        trackWeekly(player, action, speciesPath, types, naturePath, data)
    }

    private fun trackDaily(
        player: ServerPlayer,
        action: MissionAction,
        speciesPath: String,
        types: List<String>,
        naturePath: String,
        data: MissionsSavedData
    ) {
        val instances = data.daily[player.uuid]?.values?.toList() ?: return
        var changed = false
        instances.forEach { inst ->
            if (inst.completed) return@forEach
            if (inst.action != action) {
                if (inst.sequence && inst.progress > 0) {
                    inst.progress = 0
                    changed = true
                }
                return@forEach
            }
            if (MissionMatcher.matches(action, inst.target, speciesPath, types, naturePath)) {
                inst.progress = minOf(inst.progress + 1, inst.quantity)
                changed = true
                if (inst.progress >= inst.quantity) {
                    inst.completed = true
                    completeDaily(player, inst)
                }
            } else if (inst.sequence && inst.progress > 0) {
                inst.progress = 0
                changed = true
            }
        }
        if (changed) data.setDirty()
    }

    private fun trackWeekly(
        player: ServerPlayer,
        action: MissionAction,
        speciesPath: String,
        types: List<String>,
        naturePath: String,
        data: MissionsSavedData
    ) {
        if (data.weekly.isEmpty()) return
        var changed = false
        data.weekly.values.forEach { inst ->
            if (inst.completed) return@forEach
            if (inst.action != action) {
                val progressMap = data.weeklyProgress[player.uuid]
                if (inst.sequence && (progressMap?.get(inst.instanceId) ?: 0) > 0) {
                    progressMap!![inst.instanceId] = 0
                    changed = true
                }
                return@forEach
            }
            val progressMap = data.weeklyProgress.getOrPut(player.uuid) { mutableMapOf() }
            var progress = progressMap[inst.instanceId] ?: 0
            if (MissionMatcher.matches(action, inst.target, speciesPath, types, naturePath)) {
                progress = minOf(progress + 1, inst.quantity)
                progressMap[inst.instanceId] = progress
                changed = true
                if (progress >= inst.quantity) {
                    inst.completed = true
                    inst.completedBy = player.uuid
                    inst.progress = inst.quantity
                    completeWeekly(player, inst)
                }
            } else if (inst.sequence && progress > 0) {
                progressMap[inst.instanceId] = 0
                changed = true
            }
        }
        if (changed) data.setDirty()
    }

    // ─── Geração e janelas ──────────────────────────────────────────────────────

    private fun ensureDailyWindow(player: ServerPlayer, server: MinecraftServer) {
        val cfg = NbpConfig.data.missions
        if (cfg.dailyCount <= 0) return
        val data = store.require()
        val now = System.currentTimeMillis()
        val window = dayWindow(now, cfg.dayResetHourUtc)
        val existing = data.daily[player.uuid]
        if ((data.dailyWindow[player.uuid] ?: 0L) == window && existing != null && existing.isNotEmpty()) return

        data.dailyWindow[player.uuid] = window
        val generated = MissionGenerator.generate(
            definitions = MissionsConfigFile.data.missions,
            difficulties = MissionsConfigFile.data.difficulties,
            cycle = MissionCycle.DAILY,
            count = cfg.dailyCount,
            speciesPool = ::speciesPoolFor,
            types = allTypes,
            natures = allNatures
        )
        data.daily[player.uuid] = generated.associateBy { it.instanceId }.toMutableMap()
        data.setDirty()
        player.sendSystemMessage(PlayerLanguage.text(player, "missions.daily_generated"))
    }

    private fun ensureWeeklyIfStale(server: MinecraftServer) {
        val cfg = NbpConfig.data.missions
        if (cfg.weeklyCount <= 0) return
        val data = store.require()
        val now = System.currentTimeMillis()
        val window = weekWindow(now, cfg.dayResetHourUtc, cfg.weekResetWeekday)
        if (data.weeklyWindow == window && data.weekly.isNotEmpty()) return

        data.weeklyWindow = window
        data.weekly.clear()
        data.weeklyProgress.clear()
        val generated = MissionGenerator.generate(
            definitions = MissionsConfigFile.data.missions,
            difficulties = MissionsConfigFile.data.difficulties,
            cycle = MissionCycle.WEEKLY,
            count = cfg.weeklyCount,
            speciesPool = ::speciesPoolFor,
            types = allTypes,
            natures = allNatures
        )
        generated.forEach { data.weekly[it.instanceId] = it }
        data.setDirty()
        server.playerList.players.forEach { it.sendSystemMessage(PlayerLanguage.text(it, "missions.weekly_generated")) }
    }

    private fun speciesPoolFor(difficultyId: String): List<String> {
        val cfg = MissionsConfigFile.data.difficulties[difficultyId] ?: return emptyList()
        return PokemonSpecies.implemented.asSequence()
            .filter { species ->
                val labels = species.labels + species.standardForm.labels
                if (cfg.requireLabels.isNotEmpty() && cfg.requireLabels.none { required ->
                        labels.any { it.equals(required, ignoreCase = true) }
                    }) {
                    return@filter false
                }
                if (cfg.excludeLabels.any { excluded -> labels.any { it.equals(excluded, ignoreCase = true) } }) {
                    return@filter false
                }
                if (cfg.maxPokedex > 0 && species.nationalPokedexNumber > cfg.maxPokedex) {
                    return@filter false
                }
                true
            }
            .map { it.resourceIdentifier.path }
            .toList()
    }

    // ─── Conclusão e recompensas ────────────────────────────────────────────────

    private fun completeDaily(player: ServerPlayer, inst: MissionInstance) {
        grantRewards(player, inst)
        val message = PlayerLanguage.string(
            player, "missions.completed",
            "mission" to describeMission(player, inst)
        )
        player.sendSystemMessage(Component.literal(message))
        networkSender(
            player,
            "§a[Missions] ${PlayerLanguage.string(player, "missions.completed_bar", "mission" to describeMission(player, inst))}",
            rewardBarTicks()
        )
    }

    private fun completeWeekly(player: ServerPlayer, inst: MissionInstance) {
        val server = serverRef ?: return
        grantRewards(player, inst)
        if (NbpConfig.data.missions.broadcastWeeklyCompletion) {
            val broadcast = PlayerLanguage.string(
                null, "missions.weekly_claimed",
                "player" to player.scoreboardName,
                "mission" to describeMission(player, inst)
            )
            server.playerList.players.forEach { it.sendSystemMessage(Component.literal(broadcast)) }
        }
        player.sendSystemMessage(PlayerLanguage.text(
            player, "missions.completed",
            "mission" to describeMission(player, inst)
        ))
    }

    private fun grantRewards(player: ServerPlayer, inst: MissionInstance): List<RewardRollResult> {
        val rewards = MissionsConfigFile.data.difficulties[inst.difficulty]?.rewards ?: emptyList()
        val rolled = MissionRewardRoller.roll(rewards, inst.rewardRolls)
        rolled.forEach { result ->
            runCatching {
                val item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(result.itemId)).orElse(null)
                    ?: return@runCatching
                if (item != Items.AIR) {
                    val stack = ItemStack(item)
                    stack.setCount(result.count.coerceIn(1, 64))
                    if (!player.addItem(stack)) {
                        player.drop(stack, false)
                    }
                }
            }
        }
        return rolled
    }

    private fun rewardBarTicks(): Int {
        val configured = NbpConfig.data.points.rewardBarDurationTicks
        return if (configured <= 0) 80 else configured
    }

    private fun describeMission(player: ServerPlayer, inst: MissionInstance): String {
        val count = inst.quantity
        val action = PlayerLanguage.string(
            player,
            if (inst.action == MissionAction.CAPTURE) "missions.action.capture" else "missions.action.defeat"
        )
        val target = when {
            inst.target == null -> PlayerLanguage.string(player, "missions.target.any")
            inst.target.species != null -> PlayerLanguage.string(
                player, "missions.target.species",
                "species" to inst.target.species.replaceFirstChar { it.uppercase() }
            )
            inst.target.type != null -> PlayerLanguage.string(
                player, "missions.target.type",
                "type" to inst.target.type.replaceFirstChar { it.uppercase() }
            )
            inst.target.nature != null -> PlayerLanguage.string(
                player, "missions.target.nature",
                "nature" to inst.target.nature.replaceFirstChar { it.uppercase() }
            )
            else -> PlayerLanguage.string(player, "missions.target.any")
        }
        val base = PlayerLanguage.string(
            player, "missions.desc",
            "action" to action, "count" to count, "target" to target
        )
        return if (inst.sequence) "$base ${PlayerLanguage.string(player, "missions.target.sequence")}" else base
    }

    private fun rewardPreview(inst: MissionInstance): List<RewardViewRow> =
        MissionsConfigFile.data.difficulties[inst.difficulty]?.rewards
            ?.map { RewardViewRow(it.item, it.max) }
            ?: emptyList()

    // ─── API pública (comandos / tela) ──────────────────────────────────────────

    fun openView(player: ServerPlayer) {
        if (!isEnabled) {
            player.sendSystemMessage(PlayerLanguage.text(player, "missions.disabled"))
            return
        }
        val server = serverRef ?: return
        ensureDailyWindow(player, server)
        ensureWeeklyIfStale(server)
        val data = store.require()
        val portuguese = PlayerLanguage.get(player) == "pt_br"
        val dailyRows = (data.daily[player.uuid]?.values ?: emptyList())
            .sortedBy { it.instanceId }
            .map { viewRow(player, it, null) }
        val weeklyRows = data.weekly.values
            .sortedBy { it.instanceId }
            .map { inst ->
                val progress = data.weeklyProgress[player.uuid]?.get(inst.instanceId) ?: 0
                viewRow(player, inst, progress)
            }
        viewNetworkSender(player, portuguese, dailyRows, weeklyRows)
    }

    private fun viewRow(player: ServerPlayer, inst: MissionInstance, weeklyProgress: Int?): MissionViewRow {
        val progress = if (inst.cycle == MissionCycle.WEEKLY) {
            (weeklyProgress ?: 0).coerceAtMost(inst.quantity)
        } else {
            inst.progress
        }
        val lockedBy = if (inst.completed) {
            inst.completedBy?.let { id ->
                serverRef?.playerList?.getPlayer(id)?.scoreboardName ?: id.toString().take(8)
            }
        } else null
        return MissionViewRow(
            instanceId = inst.instanceId,
            description = describeMission(player, inst),
            difficulty = inst.difficulty,
            progress = progress,
            quantity = inst.quantity,
            completed = inst.completed,
            lockedBy = lockedBy,
            rewards = rewardPreview(inst)
        )
    }

    fun listFor(player: ServerPlayer): List<String> {
        val server = serverRef ?: return emptyList()
        ensureDailyWindow(player, server)
        ensureWeeklyIfStale(server)
        val data = store.require()
        val lines = mutableListOf<String>()
        lines += PlayerLanguage.string(player, "missions.list_header")
        data.daily[player.uuid]?.values?.sortedBy { it.instanceId }?.forEach { inst ->
            lines += describeMission(player, inst) + " §7" + progressText(inst.progress, inst.quantity) + status(inst)
        }
        lines += PlayerLanguage.string(player, "missions.list_weekly")
        data.weekly.values.sortedBy { it.instanceId }.forEach { inst ->
            val progress = (data.weeklyProgress[player.uuid]?.get(inst.instanceId) ?: 0).coerceAtMost(inst.quantity)
            val suffix = when {
                inst.completed && inst.completedBy == player.uuid -> " §a✔"
                inst.completed -> " §c🔒"
                else -> ""
            }
            lines += describeMission(player, inst) + " §7" + progressText(progress, inst.quantity) + suffix
        }
        return lines
    }

    private fun progressText(progress: Int, quantity: Int): String = "($progress/$quantity)"

    private fun status(inst: MissionInstance): String = if (inst.completed) " §a✔" else ""

    fun resetDaily(target: ServerPlayer?, server: MinecraftServer) {
        val data = store.require()
        if (target == null) {
            server.playerList.players.forEach { player ->
                data.daily.remove(player.uuid)
                data.dailyWindow.remove(player.uuid)
                ensureDailyWindow(player, server)
            }
        } else {
            data.daily.remove(target.uuid)
            data.dailyWindow.remove(target.uuid)
            ensureDailyWindow(target, server)
        }
        server.playerList.players.forEach {
            it.sendSystemMessage(PlayerLanguage.text(
                it, "missions.reset_daily",
                "player" to (target?.scoreboardName ?: it.scoreboardName)
            ))
        }
    }

    fun resetWeekly(server: MinecraftServer) {
        val data = store.require()
        data.weeklyWindow = 0L
        data.weekly.clear()
        data.weeklyProgress.clear()
        data.setDirty()
        ensureWeeklyIfStale(server)
        server.playerList.players.forEach { it.sendSystemMessage(PlayerLanguage.text(it, "missions.reset_weekly")) }
    }

    /** Força a conclusão de uma missão ativa do jogador (diária ou semanal), aplicando a regra semanal de 1º. */
    fun forceComplete(player: ServerPlayer, instanceId: String): Boolean {
        val data = store.require()
        val dailyInst = data.daily[player.uuid]?.get(instanceId)
        if (dailyInst != null && !dailyInst.completed) {
            dailyInst.completed = true
            dailyInst.progress = dailyInst.quantity
            data.setDirty()
            completeDaily(player, dailyInst)
            return true
        }
        val weeklyInst = data.weekly[instanceId]
        if (weeklyInst != null && !weeklyInst.completed) {
            weeklyInst.completed = true
            weeklyInst.completedBy = player.uuid
            weeklyInst.progress = weeklyInst.quantity
            data.setDirty()
            completeWeekly(player, weeklyInst)
            return true
        }
        return false
    }

    fun testReward(player: ServerPlayer, difficultyId: String): List<RewardRollResult> {
        val rewards = MissionsConfigFile.data.difficulties[difficultyId]?.rewards ?: return emptyList()
        return MissionRewardRoller.roll(rewards, 3)
    }

    /** Completa todas as missões ativas de um ciclo para o jogador (comando admin). */
    fun forceCompleteCycle(player: ServerPlayer, cycle: MissionCycle): Int {
        val data = store.require()
        val ids = when (cycle) {
            MissionCycle.DAILY -> data.daily[player.uuid]?.keys?.toList() ?: emptyList()
            MissionCycle.WEEKLY -> data.weekly.keys.toList()
        }
        var count = 0
        ids.forEach { id -> if (forceComplete(player, id)) count++ }
        return count
    }

    // ─── util ───────────────────────────────────────────────────────────────────

    private fun isRaidBoss(battlePokemon: Any): Boolean = runCatching {
        battlePokemon.javaClass.methods.firstOrNull { it.name == "crd_isRaidBoss" && it.parameterCount == 0 }
            ?.invoke(battlePokemon) as? Boolean ?: false
    }.getOrDefault(false)

    private fun dayWindow(epochMillis: Long, resetHourUtc: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = epochMillis
        cal.set(Calendar.HOUR_OF_DAY, resetHourUtc.coerceIn(0, 23))
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun weekWindow(epochMillis: Long, resetHourUtc: Int, resetWeekdayUtc: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = epochMillis
        cal.set(Calendar.HOUR_OF_DAY, resetHourUtc.coerceIn(0, 23))
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        // config: 1 = Monday ... 7 = Sunday; Calendar.DAY_OF_WEEK: 1 = Sunday ... 7 = Saturday
        val targetDow = if (resetWeekdayUtc in 1..7) resetWeekdayUtc else 1
        val calTarget = if (targetDow == 7) 1 else targetDow + 1
        var diff = cal.get(Calendar.DAY_OF_WEEK) - calTarget
        if (diff < 0) diff += 7
        cal.add(Calendar.DAY_OF_YEAR, -diff)
        return cal.timeInMillis
    }
}