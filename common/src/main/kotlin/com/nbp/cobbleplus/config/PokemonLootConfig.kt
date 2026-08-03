package com.nbp.cobbleplus.config

import com.google.gson.GsonBuilder
import com.nbp.cobbleplus.NbpCobblePlus
import java.io.File
import java.io.FileReader
import java.io.FileWriter

data class PokemonLootAddition(
    var item: String = "minecraft:diamond",
    var chance: Double = 1.0,
    var minQuantity: Int = 1,
    var maxQuantity: Int = 1
)

data class PokemonLootRule(
    var enabled: Boolean = true,
    var remove: MutableList<String> = mutableListOf(),
    var add: MutableList<PokemonLootAddition> = mutableListOf()
)

data class PokemonLootConfigData(
    var enabled: Boolean = true,
    var species: MutableMap<String, PokemonLootRule> = linkedMapOf()
)

object PokemonLootConfig {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val file = File("config/nbp_cobble_plus/pokemon_loot.json")

    @Volatile
    var data = defaultData()
        private set

    fun load() {
        try {
            file.parentFile.mkdirs()
            data = if (file.exists()) {
                FileReader(file).use { gson.fromJson(it, PokemonLootConfigData::class.java) ?: PokemonLootConfigData() }
            } else {
                defaultData().also { save(it) }
            }
            sanitize(data)
            NbpCobblePlus.logger.info("Loaded Pokémon loot modifiers: ${data.species.size} species")
        } catch (exception: Exception) {
            NbpCobblePlus.logger.error("Failed to load ${file.path}; Pokémon loot modifiers were disabled", exception)
            data = PokemonLootConfigData(enabled = false)
        }
    }

    private fun save(value: PokemonLootConfigData) {
        FileWriter(file).use { gson.toJson(value, it) }
    }

    internal fun sanitize(value: PokemonLootConfigData) {
        value.species.values.forEach { rule ->
            rule.remove = rule.remove.map(String::trim).filter(String::isNotEmpty).distinct().toMutableList()
            rule.add.forEach { addition ->
                addition.item = addition.item.trim()
                addition.chance = addition.chance.coerceIn(0.0, 1.0)
                addition.minQuantity = addition.minQuantity.coerceAtLeast(1)
                addition.maxQuantity = addition.maxQuantity.coerceAtLeast(addition.minQuantity)
            }
            rule.add.removeIf { it.item.isEmpty() }
        }
    }

    internal fun defaultData() = PokemonLootConfigData(
        species = linkedMapOf(
            "arceus" to netherStarRule(1.0),
            "eternatus" to netherStarRule(0.75),
            "necrozma" to netherStarRule(0.75),
            "deoxys" to netherStarRule(0.50),
            "giratina" to netherStarRule(0.50)
        )
    )

    private fun netherStarRule(chance: Double) = PokemonLootRule(
        add = mutableListOf(
            PokemonLootAddition(
                item = "minecraft:nether_star",
                chance = chance,
                minQuantity = 1,
                maxQuantity = 1
            )
        )
    )

}
