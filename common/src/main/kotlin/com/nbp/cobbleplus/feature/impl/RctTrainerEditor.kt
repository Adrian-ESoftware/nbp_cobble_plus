package com.nbp.cobbleplus.feature.impl

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import java.nio.file.Files
import java.nio.file.Path

/** Editable representation used by the in-game trainer editor. */
data class EditableTrainerPokemon(
    var species: String = "pikachu",
    var form: String? = null,
    var nickname: String? = null,
    var level: Int = 1,
    var ability: String? = null,
    var nature: String? = null,
    var heldItem: String? = null,
    var moves: MutableList<String> = mutableListOf(),
    var ivs: MutableMap<String, Int> = mutableMapOf(),
    var evs: MutableMap<String, Int> = mutableMapOf(),
    var shiny: Boolean = false,
    var gender: String? = null
)

data class EditableRctTrainer(
    var id: String = "nbp_trainer",
    var name: String = "NBP Trainer",
    var series: String = "radicalred",
    var condition: String? = null,
    var team: MutableList<EditableTrainerPokemon> = mutableListOf()
)

/** Writes RCT-compatible resources and makes them available after /reload. */
object RctTrainerEditor {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun write(server: MinecraftServer, trainer: EditableRctTrainer): Path {
        require(trainer.id.matches(Regex("[a-z0-9_./-]+"))) { "Invalid trainer id" }
        val root = server.getWorldPath(LevelResource.ROOT)
            .resolve("datapacks/nbp_generated_trainers/data/rctmod/trainers")
        Files.createDirectories(root)
        val json = JsonObject().apply {
            addProperty("name", trainer.name)
            add("series", JsonArray().also { it.add(trainer.series) })
            add("team", JsonArray().also { array -> trainer.team.forEach { array.add(toJson(it)) } })
        }
        val path = root.resolve("${trainer.id}.json")
        Files.writeString(path, gson.toJson(json))
        return path
    }

    private fun toJson(pokemon: EditableTrainerPokemon): JsonObject = JsonObject().apply {
        addProperty("species", pokemon.species)
        pokemon.form?.takeIf { it.isNotBlank() }?.let { addProperty("form", it) }
        pokemon.nickname?.takeIf { it.isNotBlank() }?.let { addProperty("nickname", it) }
        addProperty("level", pokemon.level.coerceIn(1, 100))
        pokemon.ability?.takeIf { it.isNotBlank() }?.let { addProperty("ability", it) }
        pokemon.nature?.takeIf { it.isNotBlank() }?.let { addProperty("nature", it) }
        pokemon.heldItem?.takeIf { it.isNotBlank() }?.let { addProperty("item", it) }
        pokemon.gender?.takeIf { it.isNotBlank() }?.let { addProperty("gender", it.uppercase()) }
        if (pokemon.moves.isNotEmpty()) add("moveset", JsonArray().also { pokemon.moves.take(4).forEach(it::add) })
        if (pokemon.ivs.isNotEmpty()) add("ivs", stats(pokemon.ivs))
        if (pokemon.evs.isNotEmpty()) add("evs", stats(pokemon.evs))
        if (pokemon.shiny) addProperty("shiny", true)
    }

    private fun stats(values: Map<String, Int>) = JsonObject().also { obj ->
        values.forEach { (stat, value) -> obj.addProperty(stat, value.coerceIn(0, 252)) }
    }
}
