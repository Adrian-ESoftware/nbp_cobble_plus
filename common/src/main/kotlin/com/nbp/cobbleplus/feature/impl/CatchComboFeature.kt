package com.nbp.cobbleplus.feature.impl

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.pokemon.ExperienceGainedEvent
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.reactive.ObservableSubscription
import com.cobblemon.mod.common.api.spawning.SpawnBucket
import com.cobblemon.mod.common.api.spawning.influence.SpawningInfluence
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.spawner
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.nbp.cobbleplus.config.CatchComboTier
import com.nbp.cobbleplus.config.NbpConfig
import com.nbp.cobbleplus.feature.FeatureModule
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.UUID
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

    private const val PERFECT_IV_VALUE = 31

    private val PERFECT_IV_STATS: List<Stat> = listOf(
        Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED
    )

    private var captureSub: ObservableSubscription<*>? = null
    private var shinySub: ObservableSubscription<*>? = null
    private var expSub: ObservableSubscription<*>? = null

    /** Envia as linhas do HUD para o cliente do jogador. Ligado por cada plataforma (Fabric/NeoForge). */
    var networkSender: (ServerPlayer, List<String>) -> Unit = { _, _ -> }

    private val influenceAttachedFor = mutableSetOf<UUID>()

    data class ComboState(
        var species: String = "",
        var count: Int = 0,
        var bestSpecies: String = "",
        var bestCount: Int = 0
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
    }

    override fun onDisable() {
        captureSub?.unsubscribe()
        shinySub?.unsubscribe()
        expSub?.unsubscribe()
        captureSub = null
        shinySub = null
        expSub = null
        influenceAttachedFor.clear()
    }

    /**
     * Garante que o [PlayerSpawner][com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawner] do jogador
     * tenha nossa [SpawningInfluence] anexada. Isso é o que faz o bônus de spawns raros valer tanto para
     * spawns reais quanto para mods que consultam `spawner.influences` diretamente, como o Cobblenav/Pokénav.
     */
    fun attachSpawningInfluence(player: ServerPlayer) {
        if (!influenceAttachedFor.add(player.uuid)) return

        @Suppress("UNCHECKED_CAST")
        (player.spawner.influences as? MutableList<SpawningInfluence>)?.add(CatchComboSpawningInfluence(player.uuid))
    }

    /** Reenvia o HUD com o combo salvo do jogador. Chamado ao entrar no servidor. */
    fun syncHud(player: ServerPlayer) {
        networkSender(player, buildHudLines(loadState(player), isNewRecord = false, perfectIvsApplied = 0))
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

            for (bucket in bucketWeights.keys.toList()) {
                if (config.rareSpawnBucketNames.any { it.equals(bucket.name, ignoreCase = true) }) {
                    bucketWeights[bucket] = (bucketWeights.getValue(bucket) * multiplier).toFloat()
                }
            }
        }
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

        if (state.bestCount > 0) {
            tell(player, "§7Recorde: §f${speciesTitle(state.bestSpecies)} §ax${state.bestCount}")
        }

        return 1
    }

    fun resetCombo(player: ServerPlayer): Int {
        val config = NbpConfig.data.catchCombo
        val state = loadState(player)
        saveState(player, ComboState(bestSpecies = state.bestSpecies, bestCount = state.bestCount))
        tell(player, config.resetMessage)
        networkSender(player, emptyList())
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

        val perfectIvsApplied = if (config.enablePerfectIvBonus) applyGuaranteedPerfectIvs(pokemon, state.count) else 0

        networkSender(player, buildHudLines(state, isNewRecord, perfectIvsApplied))
    }

    private fun buildHudLines(state: ComboState, isNewRecord: Boolean, perfectIvsApplied: Int): List<String> {
        if (state.species.isBlank() || state.count <= 0) return emptyList()

        val config = NbpConfig.data.catchCombo
        val tier = tierFor(state.count)
        val lines = mutableListOf<String>()

        var firstLine = config.comboMessage
            .replace("{pokemon}", speciesTitle(state.species))
            .replace("{count}", state.count.toString())
        if (perfectIvsApplied > 0) {
            firstLine += config.perfectIvSuffix.replace("{amount}", perfectIvsApplied.toString())
        }
        lines += firstLine

        lines += config.hudBonusLineFormat
            .replace("{ivs}", tier.guaranteedPerfectIvs.toString())
            .replace("{shiny}", format(tier.shinyChanceMultiplier))
            .replace("{rare}", format(rareSpawnMultiplierFor(state.count)))
            .replace("{xp}", format(xpMultiplierFor(state.count)))

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
        return if (chance > 1.0f) {
            (chance / multiplier.toFloat()).coerceAtLeast(1.0f)
        } else {
            (chance * multiplier.toFloat()).coerceAtMost(1.0f)
        }
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

    // --- Estado persistente (arquivo próprio, igual ao NbpConfig) ---
    // Não usamos Entity#getPersistentData() porque essa API não existe no
    // código comum multiplataforma (Fabric/NeoForge via Architectury).

    private fun loadState(player: ServerPlayer): ComboState =
        CatchComboStore.data[player.uuid.toString()] ?: ComboState()

    private fun saveState(player: ServerPlayer, state: ComboState) {
        CatchComboStore.data[player.uuid.toString()] = state
        CatchComboStore.save()
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
 * Persistência simples do combo por jogador (UUID -> [CatchComboFeature.ComboState]),
 * seguindo o mesmo padrão de arquivo JSON usado por [NbpConfig].
 */
private object CatchComboStore {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val storeFile = File("config/nbp_cobble_plus_catchcombo.json")
    private val type = TypeToken.getParameterized(
        MutableMap::class.java,
        String::class.java,
        CatchComboFeature.ComboState::class.java
    ).type

    val data: MutableMap<String, CatchComboFeature.ComboState> = load()

    private fun load(): MutableMap<String, CatchComboFeature.ComboState> {
        return try {
            if (storeFile.exists()) {
                FileReader(storeFile).use { reader ->
                    gson.fromJson<MutableMap<String, CatchComboFeature.ComboState>>(reader, type) ?: mutableMapOf()
                }
            } else {
                mutableMapOf()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            mutableMapOf()
        }
    }

    fun save() {
        try {
            storeFile.parentFile?.mkdirs()
            FileWriter(storeFile).use { writer -> gson.toJson(data, type, writer) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
