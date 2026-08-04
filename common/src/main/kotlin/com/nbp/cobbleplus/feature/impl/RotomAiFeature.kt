package com.nbp.cobbleplus.feature.impl

import com.cobblemon.mod.common.api.drop.ItemDropEntry
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail
import com.cobblemon.mod.common.pokemon.Species
import com.nbp.cobbleplus.config.NbpConfig
import com.nbp.cobbleplus.config.PokemonLootAddition
import com.nbp.cobbleplus.config.PokemonLootConfig
import com.nbp.cobbleplus.config.RotomAiConfig
import com.nbp.cobbleplus.feature.FeatureModule
import com.nbp.cobbleplus.i18n.PlayerLanguage
import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.service.AiServices
import dev.langchain4j.service.MemoryId
import dev.langchain4j.service.UserMessage
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

interface RotomAssistant {
    fun chat(@MemoryId playerId: UUID, @UserMessage message: String): String
}

object RotomAiFeature : FeatureModule {
    override val name = "RotomAI"
    override val isEnabled get() = NbpConfig.data.rotomAi.enabled

    private val logger = LoggerFactory.getLogger("NBP-RotomAI")
    private val executor: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "nbp-rotomai").apply { isDaemon = true }
    }

    @Volatile private var assistant: RotomAssistant? = null
    @Volatile private var assistantSignature: String? = null

    override fun onEnable() = Unit

    override fun onDisable() {
        // Forces a fresh ChatModel/AiServices build (and drops in-memory chat history) next time
        // ask() runs, e.g. after /nbp reload picks up a changed API key or model.
        assistant = null
        assistantSignature = null
    }

    /** Despacha a pergunta do jogador fora da main thread e responde só pra ele quando pronto. */
    fun ask(player: ServerPlayer, message: String) {
        if (message.isBlank()) return
        val config = NbpConfig.data.rotomAi
        executor.execute {
            try {
                val service = obtainAssistant(config)
                if (service == null) {
                    replyOnMainThread(player, PlayerLanguage.text(player, "rotomai.not_configured"))
                    return@execute
                }
                val reply = service.chat(player.uuid, message)
                replyOnMainThread(player, Component.literal("§d[RotomAI] §f$reply"))
            } catch (e: Exception) {
                logger.error("RotomAI request failed for ${player.scoreboardName}", e)
                replyOnMainThread(player, PlayerLanguage.text(player, "rotomai.error"))
            }
        }
    }

    private fun replyOnMainThread(player: ServerPlayer, component: Component) {
        player.server.execute { player.sendSystemMessage(component) }
    }

    private fun obtainAssistant(config: RotomAiConfig): RotomAssistant? {
        if (config.apiKey.isBlank() || config.model.isBlank()) return null
        val signature = "${config.apiKey}|${config.baseUrl}|${config.model}|${config.requestTimeoutSeconds}|${config.maxOutputTokens}|${config.maxHistoryMessages}"
        assistant?.let { if (assistantSignature == signature) return it }
        synchronized(this) {
            assistant?.let { if (assistantSignature == signature) return it }
            return runCatching {
                val model: ChatModel = OpenAiChatModel.builder()
                    .baseUrl(config.baseUrl)
                    .apiKey(config.apiKey)
                    .modelName(config.model)
                    .timeout(Duration.ofSeconds(config.requestTimeoutSeconds.toLong().coerceAtLeast(1)))
                    .maxTokens(config.maxOutputTokens)
                    .build()
                val built = AiServices.builder(RotomAssistant::class.java)
                    .chatModel(model)
                    .tools(RotomAiTools())
                    .systemMessageProvider { config.systemPrompt }
                    .chatMemoryProvider { MessageWindowChatMemory.withMaxMessages(config.maxHistoryMessages.coerceAtLeast(1)) }
                    .build()
                assistant = built
                assistantSignature = signature
                built
            }.onFailure {
                logger.error("Failed to build the RotomAI assistant; check rotomAi.apiKey/baseUrl/model in the config.", it)
            }.getOrNull()
        }
    }
}

/** Ferramentas do RotomAI: cada método é chamado pelo próprio modelo quando precisa de dado real do Cobblemon. */
private class RotomAiTools {
    private val statLabels = listOf(
        Stats.HP to "HP", Stats.ATTACK to "Attack", Stats.DEFENCE to "Defense",
        Stats.SPECIAL_ATTACK to "Sp.Atk", Stats.SPECIAL_DEFENCE to "Sp.Def", Stats.SPEED to "Speed"
    )

    @Tool("Gets detailed characteristics of a Pokémon species: national dex number, type(s), base stats, abilities, height, weight, catch rate and egg groups.")
    fun getPokemonInfo(@P("Pokémon species name, e.g. pikachu") species: String): String {
        val sp = resolveSpecies(species) ?: return notFound(species)
        val form = sp.standardForm
        val types = form.types.joinToString("/") { it.name.replaceFirstChar(Char::uppercase) }
        val stats = statLabels.joinToString(", ") { (stat, label) -> "$label ${form.baseStats[stat] ?: 0}" }
        val abilities = form.abilities.map { it.template.name }.distinct().joinToString(", ")
        val eggGroups = form.eggGroups.joinToString(", ") { it.showdownID }
        val heightM = "%.1f".format(Locale.US, form.height / 10f)
        val weightKg = "%.1f".format(Locale.US, form.weight / 10f)
        return "#${sp.nationalPokedexNumber} ${sp.name} - Type: $types | Stats: $stats | Abilities: $abilities | " +
            "Height: ${heightM}m | Weight: ${weightKg}kg | Catch rate: ${form.catchRate} | Egg groups: $eggGroups"
    }

    @Tool("Reports what a Pokémon species is weak to, resists, and is immune to, based on its type(s) and the standard type chart.")
    fun getPokemonWeaknesses(@P("Pokémon species name") species: String): String {
        val sp = resolveSpecies(species) ?: return notFound(species)
        val types = sp.standardForm.types.map { it.name.lowercase() }
        val weak = mutableListOf<String>()
        val resist = mutableListOf<String>()
        val immune = mutableListOf<String>()
        TypeChart.allTypes.forEach { attacking ->
            val multiplier = TypeChart.combinedEffectiveness(attacking, types)
            when {
                multiplier == 0.0 -> immune += attacking
                multiplier > 1.0 -> weak += "$attacking (x${formatMultiplier(multiplier)})"
                multiplier < 1.0 -> resist += "$attacking (x${formatMultiplier(multiplier)})"
            }
        }
        return "${sp.name} (${types.joinToString("/")}) - " +
            (if (weak.isEmpty()) "no notable weaknesses; " else "weak to: ${weak.joinToString(", ")}; ") +
            (if (resist.isEmpty()) "no notable resistances; " else "resists: ${resist.joinToString(", ")}; ") +
            (if (immune.isEmpty()) "no immunities." else "immune to: ${immune.joinToString(", ")}.")
    }

    @Tool("Reports where and under what conditions a wild Pokémon species can spawn: biomes, dimensions, rarity and level range.")
    fun getPokemonSpawnLocations(@P("Pokémon species name") species: String): String {
        val sp = resolveSpecies(species) ?: return notFound(species)
        val details = runCatching {
            CobblemonSpawnPools.WORLD_SPAWN_POOL.filterIsInstance<PokemonSpawnDetail>()
                .filter { it.pokemon.species?.equals(sp.name, ignoreCase = true) == true }
        }.getOrDefault(emptyList())
        if (details.isEmpty()) {
            return "${sp.name} has no natural wild spawn entries configured on this server (it may only be obtainable via breeding, trading, evolution or events)."
        }
        val lines = details.map { detail ->
            val dimensions = runCatching { detail.conditions.mapNotNull { it.dimensions }.flatten().joinToString(", ") }.getOrDefault("")
            val biomes = runCatching { detail.conditions.mapNotNull { it.biomes }.flatten().joinToString(", ") { it.toString() } }.getOrDefault("")
            val bucket = runCatching { detail.bucket.name }.getOrDefault("unknown")
            val levels = runCatching { detail.levelRange?.let { "${it.first}-${it.last}" } ?: "?" }.getOrDefault("?")
            buildString {
                append("rarity=$bucket, level=$levels")
                if (dimensions.isNotBlank()) append(", dimensions=$dimensions")
                if (biomes.isNotBlank()) append(", biomes=$biomes")
            }
        }
        return "${sp.name} spawn conditions: ${lines.joinToString(" | ")}"
    }

    @Tool("Lists what items a Pokémon species can drop when captured or defeated, with chance/quantity, accounting for this server's custom loot configuration.")
    fun getPokemonDrops(@P("Pokémon species name") species: String): String {
        val sp = resolveSpecies(species) ?: return notFound(species)
        val rule = lootRuleFor(sp)
        val removedIds = rule?.remove?.mapNotNull(ResourceLocation::tryParse)?.toSet() ?: emptySet()
        val nativeDrops = sp.standardForm.drops.entries.filterIsInstance<ItemDropEntry>()
            .filter { it.item !in removedIds }
            .map { "${it.item} (${formatDropOdds(it)})" }
        val addedDrops = rule?.add?.filter { it.enabledSafe() }?.map {
            "${it.item} (${(it.chance * 100).toInt()}%, x${it.minQuantity}-${it.maxQuantity})"
        } ?: emptyList()
        val all = nativeDrops + addedDrops
        return if (all.isEmpty()) "${sp.name} has no configured drops." else "${sp.name} can drop: ${all.joinToString("; ")}"
    }

    @Tool("Finds which Pokémon species can drop a given item, accounting for this server's custom loot configuration.")
    fun findPokemonThatDropItem(@P("Minecraft/Cobblemon item id, e.g. cobblemon:rare_candy or minecraft:diamond") itemId: String): String {
        val targetId = ResourceLocation.tryParse(itemId.trim()) ?: return "Invalid item id: $itemId"
        val matches = mutableListOf<String>()
        PokemonSpecies.species.forEach { sp ->
            val rule = lootRuleFor(sp)
            val removedIds = rule?.remove?.mapNotNull(ResourceLocation::tryParse)?.toSet() ?: emptySet()
            val nativeHas = sp.standardForm.drops.entries.filterIsInstance<ItemDropEntry>()
                .any { it.item == targetId && it.item !in removedIds }
            val addedHas = rule?.add?.any { it.enabledSafe() && ResourceLocation.tryParse(it.item) == targetId } ?: false
            if (nativeHas || addedHas) matches += sp.name
        }
        return if (matches.isEmpty()) "No Pokémon are currently configured to drop $itemId." else "Pokémon that can drop $itemId: ${matches.joinToString(", ")}"
    }

    @Tool("Reports what a Pokémon species evolves into (and what it evolves from, if anything), with a best-effort description of the evolution method.")
    fun getPokemonEvolutions(@P("Pokémon species name") species: String): String {
        val sp = resolveSpecies(species) ?: return notFound(species)
        val form = sp.standardForm
        val into = form.evolutions.map { evo ->
            val target = evo.result.species ?: "unknown"
            "$target (${evo.javaClass.simpleName})"
        }
        val from = form.preEvolution?.species?.name
        return "${sp.name}: " +
            (if (from != null) "evolves from $from; " else "no pre-evolution; ") +
            (if (into.isEmpty()) "does not evolve further." else "evolves into: ${into.joinToString(", ")}.")
    }

    private fun resolveSpecies(name: String): Species? = PokemonSpecies.getByName(name.trim().lowercase())

    private fun notFound(species: String): String =
        "No Pokémon species found named '$species'. Double-check the spelling (use the English species name, e.g. 'charizard')."

    private fun formatMultiplier(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else "%.2f".format(Locale.US, value)

    private fun formatDropOdds(entry: ItemDropEntry): String {
        val quantity = entry.quantityRange?.let { "x${it.first}-${it.last}" } ?: "x${entry.quantity}"
        return "${entry.percentage}%, $quantity"
    }

    private fun lootRuleFor(species: Species) = PokemonLootConfig.data.species.entries
        .firstOrNull { PokemonLootModifierFeature.normalizeSpecies(it.key) == PokemonLootModifierFeature.normalizeSpecies(species.resourceIdentifier.toString()) }
        ?.value
        ?.takeIf { it.enabled }

    private fun PokemonLootAddition.enabledSafe(): Boolean = chance > 0.0
}
