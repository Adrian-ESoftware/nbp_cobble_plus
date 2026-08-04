package com.nbp.cobbleplus.feature.impl

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.entity.SpawnEvent
import com.cobblemon.mod.common.api.events.pokemon.ExperienceGainedEvent
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.reactive.ObservableSubscription
import com.cobblemon.mod.common.api.spawning.SpawnBucket
import com.cobblemon.mod.common.api.spawning.SpawnCause
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail
import com.cobblemon.mod.common.api.spawning.influence.SpawningInfluence
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition
import com.cobblemon.mod.common.api.spawning.position.calculators.SpawnablePositionCalculator
import com.cobblemon.mod.common.api.spawning.spawner.SpawningZoneInput
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.spawner
import com.nbp.cobbleplus.config.CatchComboTier
import com.nbp.cobbleplus.config.NbpConfig
import com.nbp.cobbleplus.feature.FeatureModule
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.level.saveddata.SavedData
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.math.ceil
import kotlin.random.Random

/**
 * Combo de capturas no estilo "Let's Go": capturar a mesma espécie em sequência
 * garante IVs perfeitos, aumenta a chance de shiny, o EXP ganho e a chance de
 * spawns raros perto do jogador. Todos os números vêm de [NbpConfig] (catchCombo).
 */
object CatchComboFeature : FeatureModule {
    override val name: String = "Combo de Capturas"
    override val isEnabled: Boolean
        get() = NbpConfig.data.catchCombo.enabled

    private val logger = LoggerFactory.getLogger("NBP-CatchCombo")

    private const val PERFECT_IV_VALUE = 31

    private val PERFECT_IV_STATS: List<Stat> = listOf(
        Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED
    )

    private var captureSub: ObservableSubscription<*>? = null
    private var shinySub: ObservableSubscription<*>? = null
    private var expSub: ObservableSubscription<*>? = null
    private var pokemonSpawnSub: ObservableSubscription<*>? = null

    /** Envia as linhas do HUD para o cliente do jogador. Ligado por cada plataforma (Fabric/NeoForge). */
    var networkSender: (ServerPlayer, List<String>) -> Unit = { _, _ -> }

    private val influenceAttachedFor = mutableSetOf<UUID>()
    private val lastDiagnosticLogAt = mutableMapOf<UUID, Long>()
    private const val DIAGNOSTIC_LOG_COOLDOWN_MS = 3000L

    data class ComboState(
        var species: String = "",
        var count: Int = 0,
        var bestSpecies: String = "",
        var bestCount: Int = 0,
        var hudVisible: Boolean = true,
        var hasCaptured: Boolean = false
    )

    override fun onEnable() {
        captureSub = CobblemonEvents.POKEMON_CAPTURED.subscribe { event ->
            val player = event.player ?: return@subscribe
            handleCapture(player, event.pokemon)
        }

        shinySub = CobblemonEvents.SHINY_CHANCE_CALCULATION.subscribe { event ->
            event.addModificationFunction { chance, player, pokemon ->
                if (player == null) chance else applyShinyBonus(chance, player, pokemon)
            }
        }

        expSub = CobblemonEvents.EXPERIENCE_GAINED_EVENT_PRE.subscribe { event -> handleExperienceGained(event) }

        pokemonSpawnSub = CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe { event -> handlePokemonSpawn(event) }
    }

    override fun onDisable() {
        captureSub?.unsubscribe()
        shinySub?.unsubscribe()
        expSub?.unsubscribe()
        pokemonSpawnSub?.unsubscribe()
        captureSub = null
        shinySub = null
        expSub = null
        pokemonSpawnSub = null
        influenceAttachedFor.clear()
    }

    /**
     * Garante que o [PlayerSpawner][com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawner] do jogador
     * tenha nossa [SpawningInfluence] anexada. Isso é o que faz o bônus de spawns raros valer tanto para
     * spawns reais quanto para mods que consultam `spawner.influences` diretamente, como o Cobblenav/Pokénav.
     */
    fun attachSpawningInfluence(player: ServerPlayer) {
        if (player.uuid in influenceAttachedFor) return

        try {
            @Suppress("UNCHECKED_CAST")
            val influences = player.spawner.influences as? MutableList<SpawningInfluence> ?: return
            influences.add(CatchComboSpawningInfluence(player.uuid))
            // Só marca como anexado depois de confirmar sucesso: se o PlayerSpawner ainda não
            // estiver pronto (ex: chamado cedo demais no login), tentamos de novo na próxima captura
            // em vez de travar permanentemente sem nunca ter anexado nada.
            influenceAttachedFor.add(player.uuid)
            logger.info("Influência de combo anexada para ${player.name.string}.")
        } catch (e: Exception) {
            logger.warn("Não foi possível anexar a influência de combo para ${player.name.string}, tentando novamente mais tarde.", e)
        }
    }

    /** Reenvia o HUD com o combo salvo do jogador. Chamado ao entrar no servidor. */
    fun syncHud(player: ServerPlayer) {
        networkSender(player, buildHudLines(player, loadState(player), isNewRecord = false))
    }

    /**
     * Multiplica o peso dos buckets configurados (ex: "rare", "ultra-rare") conforme o combo do jogador.
     * Fica anexada ao Spawner do jogador, então tanto o spawn real do Cobblemon quanto qualquer mod que
     * leia `spawner.influences` (ex: Cobblenav) enxergam o mesmo bônus.
     */
    private class CatchComboSpawningInfluence(private val playerUuid: UUID) : SpawningInfluence {
        override fun affectBucketWeights(bucketWeights: MutableMap<SpawnBucket, Float>) {
            val config = NbpConfig.data.catchCombo
            if (!config.enableRareSpawnBonus || config.rareSpawnBucketNames.isEmpty()) return

            val state = CatchComboStore.data[playerUuid.toString()] ?: return
            if (state.count < 1) return

            val multiplier = rareSpawnMultiplierFor(state.count)
            if (multiplier <= 1.0) return

            val changed = mutableListOf<String>()
            for (bucket in bucketWeights.keys.toList()) {
                if (config.rareSpawnBucketNames.any { it.equals(bucket.name, ignoreCase = true) }) {
                    val before = bucketWeights.getValue(bucket)
                    val after = before * multiplier.toFloat()
                    bucketWeights[bucket] = after
                    changed += "${bucket.name}: $before -> $after"
                }
            }

            if (changed.isNotEmpty()) {
                logDiagnostic(playerUuid, "[RareSpawn] combo=${state.count} x$multiplier | ${changed.joinToString(", ")}")
            }
        }

        /**
         * Reforça especificamente a espécie que o jogador está encadeando: quanto mais Magikarp
         * capturado em sequência, maior o peso de spawn do próprio Magikarp (não só do bucket dele).
         */
        override fun affectWeight(detail: SpawnDetail, spawnablePosition: SpawnablePosition, weight: Float): Float {
            val config = NbpConfig.data.catchCombo
            if (!config.enableSpeciesSpawnBonus) return weight

            val state = CatchComboStore.data[playerUuid.toString()] ?: return weight
            if (state.count < 1 || state.species.isBlank()) return weight

            val species = (detail as? PokemonSpawnDetail)?.pokemon?.species ?: return weight
            if (!species.equals(state.species, ignoreCase = true)) return weight

            val multiplier = rareSpawnMultiplierFor(state.count)
            if (multiplier <= 1.0) return weight

            val boosted = weight * multiplier.toFloat()
            logDiagnostic(playerUuid, "[SpeciesSpawn] $species combo=${state.count} x$multiplier | weight $weight -> $boosted")
            return boosted
        }
    }

    /**
     * Chance real (%) de a espécie do combo nascer perto do jogador agora, já com o bônus do combo aplicado.
     * Refaz a mesma resolução de spawn que o Cobblemon usa de verdade (zona de spawn, posições válidas
     * perto do jogador, pesos com nossa [SpawningInfluence] já aplicada) — o mesmo caminho que o
     * Cobblenav/Pokénav consulta, então o valor mostrado aqui deve bater com o que aparece lá.
     */
    private fun speciesSpawnChancePercent(player: ServerPlayer, species: String): Double? {
        val cobblemonConfig = Cobblemon.config
        if (!cobblemonConfig.enableSpawning) return null

        return try {
            val spawner = player.spawner
            val cause = SpawnCause(spawner, player)
            val zone = Cobblemon.spawningZoneGenerator.generate(
                spawner,
                SpawningZoneInput(
                    cause, player.serverLevel(),
                    ceil(player.x - cobblemonConfig.spawningZoneDiameter / 2f).toInt(),
                    ceil(player.y - cobblemonConfig.spawningZoneHeight / 2f).toInt(),
                    ceil(player.z - cobblemonConfig.spawningZoneDiameter / 2f).toInt(),
                    cobblemonConfig.spawningZoneDiameter,
                    cobblemonConfig.spawningZoneHeight,
                    cobblemonConfig.spawningZoneDiameter
                )
            )
            val spawnablePositions = spawner.resolver.resolve(spawner, SpawnablePositionCalculator.prioritizedAreaCalculators, zone)

            val buckets = Cobblemon.bestSpawner.config.buckets
            val bucketWeights = buckets.associateWith { it.weight }.toMutableMap()
            (spawner.influences + zone.unconditionalInfluences).forEach { it.affectBucketWeights(bucketWeights) }
            val bucketWeightTotal = bucketWeights.values.sum()
            if (bucketWeightTotal <= 0f) return 0.0

            var speciesChance = 0.0
            for (bucket in buckets) {
                val bucketProbability = (bucketWeights[bucket] ?: 0f) / bucketWeightTotal
                if (bucketProbability <= 0f) continue

                // getProbabilities já devolve porcentagens (0-100), não frações (0-1).
                val detailProbabilities = spawner.selector.getProbabilities(spawner, bucket, spawnablePositions)
                val speciesPercentInBucket = detailProbabilities.entries
                    .filter { (detail, _) -> (detail as? PokemonSpawnDetail)?.pokemon?.species?.equals(species, ignoreCase = true) == true }
                    .sumOf { it.value.toDouble() }

                speciesChance += bucketProbability * speciesPercentInBucket
            }

            speciesChance
        } catch (e: Exception) {
            logger.warn("Não foi possível calcular a chance real de spawn para $species.", e)
            null
        }
    }

    /** Chance (%) de o próximo spawn da espécie do combo ser shiny, com o bônus da tier atual. */
    private fun speciesShinyChancePercent(count: Int): Double {
        val shinyRate = Cobblemon.config.shinyRate
        if (shinyRate <= 0f) return 0.0
        return (tierFor(count).shinyChanceMultiplier / shinyRate) * 100.0
    }

    private fun formatPercent(value: Double): String = "%.2f".format(value)

    private fun logDiagnostic(playerUuid: UUID, message: String) {
        val now = System.currentTimeMillis()
        val last = lastDiagnosticLogAt[playerUuid] ?: 0L
        if (now - last < DIAGNOSTIC_LOG_COOLDOWN_MS) return
        lastDiagnosticLogAt[playerUuid] = now
        logger.info(message)
    }

    // --- Comandos ---

    fun showStatus(player: ServerPlayer): Int {
        val config = NbpConfig.data.catchCombo
        val state = loadState(player)

        if (state.species.isBlank() || state.count <= 0) {
            tell(player, config.noComboMessage)
            return 1
        }

        val tier = tierFor(state.count)
        tell(player, "§b[Combo] §f${speciesTitle(state.species)} §ax${state.count}")
        tell(
            player,
            "§7IVs perfeitos: §f${tier.guaranteedPerfectIvs} §7| Shiny: §fx${format(tier.shinyChanceMultiplier)} " +
                "§7| Rare Spawn: §fx${format(rareSpawnMultiplierFor(state.count))} §7| EXP: §fx${format(xpMultiplierFor(state.count))}"
        )

        val spawnPercent = speciesSpawnChancePercent(player, state.species)
        val spawnText = if (spawnPercent != null) "§a${formatPercent(spawnPercent)}%" else "§c(indisponível)"
        tell(
            player,
            "§7Chance real de ${speciesTitle(state.species)}: $spawnText de spawn §7| §d${formatPercent(speciesShinyChancePercent(state.count))}% §7de shiny"
        )

        if (state.bestCount > 0) {
            tell(player, "§7Recorde: §f${speciesTitle(state.bestSpecies)} §ax${state.bestCount}")
        }

        return 1
    }

    fun resetCombo(player: ServerPlayer): Int {
        val config = NbpConfig.data.catchCombo
        val state = loadState(player)
        saveState(player, ComboState(bestSpecies = state.bestSpecies, bestCount = state.bestCount, hudVisible = state.hudVisible, hasCaptured = false))
        tell(player, config.resetMessage)
        networkSender(player, emptyList())
        return 1
    }

    /** Alterna se o HUD do combo aparece na tela desse jogador. */
    fun toggleHud(player: ServerPlayer): Int {
        val config = NbpConfig.data.catchCombo
        val state = loadState(player)
        state.hasCaptured = true
        state.hudVisible = !state.hudVisible
        saveState(player, state)

        tell(player, if (state.hudVisible) config.hudShownMessage else config.hudHiddenMessage)
        networkSender(player, buildHudLines(player, state, isNewRecord = false))
        return 1
    }

    // --- Lógica principal ---

    private fun handleCapture(player: ServerPlayer, pokemon: Pokemon) {
        val config = NbpConfig.data.catchCombo
        val species = speciesId(pokemon)
        if (species.isBlank()) return

        attachSpawningInfluence(player)

        val state = loadState(player)

        if (config.comboBreaksOnSpeciesChange && state.species != species) {
            state.species = species
            state.count = 1
        } else {
            state.species = species
            state.count += 1
        }

        var isNewRecord = false
        if (state.count > state.bestCount) {
            state.bestSpecies = species
            state.bestCount = state.count
            isNewRecord = true
        }

        saveState(player, state)

        networkSender(player, buildHudLines(player, state, isNewRecord))
    }

    /**
     * Aplica os IVs perfeitos garantidos da tier atual em Pokémon selvagens que nascem
     * perto do jogador enquanto o combo está ativo — em vez de "consertar" o IV só do
     * Pokémon que acabou de ser capturado, o bônus vale para qualquer spawn próximo.
     */
    private fun handlePokemonSpawn(event: SpawnEvent<PokemonEntity>) {
        val config = NbpConfig.data.catchCombo
        if (!config.enablePerfectIvBonus) return

        val player = event.spawnablePosition.cause.entity as? ServerPlayer ?: return
        val state = loadState(player)
        if (state.count < 1) return

        applyGuaranteedPerfectIvs(event.entity.pokemon, state.count)
    }

    private fun buildHudLines(player: ServerPlayer, state: ComboState, isNewRecord: Boolean): List<String> {
        if (!state.hudVisible || !state.hasCaptured || state.species.isBlank() || state.count <= 0) return emptyList()

        val config = NbpConfig.data.catchCombo
        val tier = tierFor(state.count)
        val lines = mutableListOf<String>()

        var firstLine = config.comboMessage
            .replace("{pokemon}", speciesTitle(state.species))
            .replace("{count}", state.count.toString())
        if (tier.guaranteedPerfectIvs > 0) {
            firstLine += config.ivHudSuffix.replace("{ivs}", tier.guaranteedPerfectIvs.toString())
        }
        lines += firstLine

        lines += config.hudBonusLineFormat
            .replace("{ivs}", tier.guaranteedPerfectIvs.toString())
            .replace("{shiny}", format(tier.shinyChanceMultiplier))
            .replace("{rare}", format(rareSpawnMultiplierFor(state.count)))
            .replace("{xp}", format(xpMultiplierFor(state.count)))

        val spawnPercent = speciesSpawnChancePercent(player, state.species)
        if (spawnPercent != null) {
            lines += "§7Spawn: §a${formatPercent(spawnPercent)}% §7| Shiny: §d${formatPercent(speciesShinyChancePercent(state.count))}%"
        }

        if (isNewRecord && state.count > 1 && config.enableRecordMessage) {
            lines += config.newRecordMessage
                .replace("{pokemon}", speciesTitle(state.species))
                .replace("{count}", state.count.toString())
        }

        return lines
    }

    private fun applyShinyBonus(chance: Float, player: ServerPlayer, pokemon: Pokemon): Float {
        val config = NbpConfig.data.catchCombo
        if (!config.enableShinyBonus) return chance

        val state = loadState(player)
        if (state.count < 1 || state.species != speciesId(pokemon)) return chance

        val multiplier = tierFor(state.count).shinyChanceMultiplier
        if (multiplier <= 1.0) return chance

        // Cobblemon normalmente passa a chance como denominador (ex: 8192).
        val result = if (chance > 1.0f) {
            (chance / multiplier.toFloat()).coerceAtLeast(1.0f)
        } else {
            (chance * multiplier.toFloat()).coerceAtMost(1.0f)
        }

        logDiagnostic(player.uuid, "[Shiny] ${player.name.string} combo=${state.count} x$multiplier | chance $chance -> $result")

        return result
    }

    private fun handleExperienceGained(event: ExperienceGainedEvent.Pre) {
        val config = NbpConfig.data.catchCombo
        if (!config.enableExpBonus) return

        val pokemon = event.pokemon
        val player = pokemon.getOwnerPlayer() ?: return
        val state = loadState(player)
        if (state.count < 1 || state.species != speciesId(pokemon)) return

        val multiplier = xpMultiplierFor(state.count)
        if (multiplier <= 1.0) return

        event.experience = (event.experience * multiplier).toInt().coerceAtLeast(0)
    }

    // --- Cálculo de tiers ---

    private fun tierFor(count: Int): CatchComboTier {
        val tiers = NbpConfig.data.catchCombo.tiers
        return tiers.lastOrNull { count >= it.minCombo } ?: tiers.first()
    }

    fun currentShinyMultiplier(player: ServerPlayer): Double {
        if (!isEnabled || !NbpConfig.data.catchCombo.enableShinyBonus) return 1.0
        val state = loadState(player)
        return if (state.count > 0 && state.species.isNotBlank()) tierFor(state.count).shinyChanceMultiplier else 1.0
    }

    fun currentShinyMultiplier(player: ServerPlayer, species: String): Double {
        if (!isEnabled || !NbpConfig.data.catchCombo.enableShinyBonus) return 1.0
        val state = loadState(player)
        val normalized = species.substringAfterLast(":").lowercase()
        return if (state.count > 0 && state.species.equals(normalized, ignoreCase = true)) {
            tierFor(state.count).shinyChanceMultiplier
        } else 1.0
    }

    data class ShinyBonus(val species: String, val count: Int, val multiplier: Double)

    fun currentShinyBonus(player: ServerPlayer): ShinyBonus? {
        if (!isEnabled || !NbpConfig.data.catchCombo.enableShinyBonus) return null
        val state = loadState(player)
        if (state.count <= 0 || state.species.isBlank()) return null
        return ShinyBonus(speciesTitle(state.species), state.count, tierFor(state.count).shinyChanceMultiplier)
    }

    private fun rareSpawnMultiplierFor(count: Int): Double {
        if (count <= 0) return 1.0

        val config = NbpConfig.data.catchCombo
        val firstTier = config.tiers.firstOrNull() ?: return 1.0
        val rampCombo = config.rareSpawnRampCombo.coerceAtLeast(1)

        if (count <= rampCombo) {
            val progress = count.coerceAtMost(rampCombo).toDouble() / rampCombo
            return 1.0 + progress * (firstTier.rareSpawnMultiplier - 1.0)
        }

        return tierFor(count).rareSpawnMultiplier
    }

    private fun xpMultiplierFor(count: Int): Double {
        if (count <= 0) return 1.0

        val config = NbpConfig.data.catchCombo
        val lastTier = config.tiers.lastOrNull() ?: return 1.0
        val tier = tierFor(count)

        if (tier !== lastTier || count <= lastTier.minCombo) return tier.xpMultiplier

        val stepSize = config.xpMultiplierStepSize.coerceAtLeast(1)
        val steps = (count - lastTier.minCombo) / stepSize
        return tier.xpMultiplier + steps * config.xpMultiplierIncrementPerStep
    }

    private fun applyGuaranteedPerfectIvs(pokemon: Pokemon, count: Int): Int {
        val amount = tierFor(count).guaranteedPerfectIvs
        if (amount <= 0) return 0

        val ivs = pokemon.ivs
        val (perfect, available) = PERFECT_IV_STATS.partition { ivs.getOrDefault(it) >= PERFECT_IV_VALUE }
        val needed = amount - perfect.size
        if (needed <= 0) return 0

        val seed = pokemon.uuid.hashCode().toLong() + count
        val selected = available.shuffled(Random(seed)).take(needed)
        selected.forEach { pokemon.setIV(it, PERFECT_IV_VALUE) }

        return selected.size
    }

    // --- Estado persistente (SavedData do mundo, igual a mapas/scoreboard do próprio Minecraft) ---

    /** Liga o combo aos dados do mundo desse servidor. Chamado por cada plataforma ao iniciar o servidor. */
    fun bindServer(server: MinecraftServer) {
        CatchComboStore.bind(server)
    }

    /** Desliga a ligação com o servidor atual (ex: ao parar, para não vazar entre reinícios no mesmo processo). */
    fun unbindServer() {
        CatchComboStore.unbind()
    }

    private fun loadState(player: ServerPlayer): ComboState =
        CatchComboStore.data[player.uuid.toString()] ?: ComboState()

    private fun saveState(player: ServerPlayer, state: ComboState) {
        CatchComboStore.data[player.uuid.toString()] = state
        CatchComboStore.markDirty()
    }

    // --- Utilitários ---

    private fun speciesId(pokemon: Pokemon): String = pokemon.species.resourceIdentifier.path

    private fun speciesTitle(id: String): String =
        id.split("_").joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }

    private fun format(value: Double): String = "%.1f".format(value)

    private fun tell(player: ServerPlayer, message: String) {
        player.sendSystemMessage(Component.literal(message))
    }
}

/**
 * Estado do combo por jogador (UUID -> [CatchComboFeature.ComboState]), salvo como dado do
 * mundo (igual a mapas, scoreboard, etc. do próprio Minecraft) em vez de um arquivo em `config/`.
 * Assim cada save/servidor tem seu próprio progresso de combo.
 */
private class CatchComboSavedData : SavedData() {
    val data: MutableMap<String, CatchComboFeature.ComboState> = mutableMapOf()

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        val playersTag = CompoundTag()
        for ((uuid, state) in data) {
            val stateTag = CompoundTag()
            stateTag.putString("species", state.species)
            stateTag.putInt("count", state.count)
            stateTag.putString("bestSpecies", state.bestSpecies)
            stateTag.putInt("bestCount", state.bestCount)
            stateTag.putBoolean("hudVisible", state.hudVisible)
            stateTag.putBoolean("hasCaptured", state.hasCaptured)
            playersTag.put(uuid, stateTag)
        }
        tag.put("players", playersTag)
        return tag
    }

    companion object {
        private const val DATA_NAME = "nbp_cobble_plus_catch_combo"

        private fun load(tag: CompoundTag, registries: HolderLookup.Provider): CatchComboSavedData {
            val savedData = CatchComboSavedData()
            val playersTag = tag.getCompound("players")
            for (uuid in playersTag.allKeys) {
                val stateTag = playersTag.getCompound(uuid)
                savedData.data[uuid] = CatchComboFeature.ComboState(
                    species = stateTag.getString("species"),
                    count = stateTag.getInt("count"),
                    bestSpecies = stateTag.getString("bestSpecies"),
                    bestCount = stateTag.getInt("bestCount"),
                    hudVisible = if (stateTag.contains("hudVisible")) stateTag.getBoolean("hudVisible") else true,
                    hasCaptured = if (stateTag.contains("hasCaptured")) stateTag.getBoolean("hasCaptured") else stateTag.getInt("count") > 0
                )
            }
            return savedData
        }

        private val FACTORY = Factory(::CatchComboSavedData, ::load, DataFixTypes.LEVEL)

        fun get(server: MinecraftServer): CatchComboSavedData =
            server.overworld().dataStorage.computeIfAbsent(FACTORY, DATA_NAME)
    }
}

private object CatchComboStore {
    private var savedData: CatchComboSavedData? = null
    private val fallback = mutableMapOf<String, CatchComboFeature.ComboState>()

    fun bind(server: MinecraftServer) {
        savedData = CatchComboSavedData.get(server)
    }

    fun unbind() {
        savedData = null
    }

    val data: MutableMap<String, CatchComboFeature.ComboState>
        get() = savedData?.data ?: fallback

    fun markDirty() {
        savedData?.setDirty()
    }
}
