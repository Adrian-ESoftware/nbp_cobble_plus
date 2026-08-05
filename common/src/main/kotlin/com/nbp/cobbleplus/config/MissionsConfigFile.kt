package com.nbp.cobbleplus.config

import com.google.gson.GsonBuilder
import com.nbp.cobbleplus.NbpCobblePlus
import com.nbp.cobbleplus.mission.Difficulty
import com.nbp.cobbleplus.mission.MissionAction
import com.nbp.cobbleplus.mission.MissionCycle
import com.nbp.cobbleplus.mission.MissionsData
import java.io.File
import java.io.FileReader
import java.io.FileWriter

/**
 * Carrega as definições de missões (dificuldades, buckets de recompensa e templates)
 * do arquivo `config/nbp_cobble_plus/missions.json` — mesmo padrão do PokemonLootConfig.
 * Caso falhe, o módulo continua com os padrões embutidos.
 */
object MissionsConfigFile {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val file = File("config/nbp_cobble_plus/missions.json")

    @Volatile
    var data = MissionsData()
        private set

    fun load() {
        try {
            file.parentFile.mkdirs()
            data = if (file.exists()) {
                FileReader(file).use { gson.fromJson(it, MissionsData::class.java) ?: MissionsData() }
            } else {
                MissionsData().also { save(it) }
            }
            sanitize(data)
            NbpCobblePlus.logger.info("Loaded missions: ${data.missions.size} definitions, ${data.buckets.size} reward buckets")
        } catch (exception: Exception) {
            NbpCobblePlus.logger.error("Failed to load ${file.path}; missions definitions keep defaults", exception)
            data = MissionsData()
        }
    }

    private fun save(value: MissionsData) {
        FileWriter(file).use { gson.toJson(value, it) }
    }

    internal fun sanitize(value: MissionsData) {
        value.difficulties.entries.removeAll { (key, _) -> Difficulty.byId(key) == null }
        value.difficulties.values.forEach { diff ->
            diff.weight = diff.weight.coerceAtLeast(0)
            diff.min = diff.min.coerceAtLeast(1)
            diff.max = diff.max.coerceAtLeast(diff.min)
            diff.rewardRolls = diff.rewardRolls.coerceAtLeast(1)
            diff.requireLabels = diff.requireLabels.map(String::trim).filter(String::isNotEmpty).distinct().toMutableList()
            diff.excludeLabels = diff.excludeLabels.map(String::trim).filter(String::isNotEmpty).distinct().toMutableList()
            diff.maxPokedex = diff.maxPokedex.coerceAtLeast(0)
        }
        value.buckets.entries.removeAll { (_, entries) -> entries.isEmpty() }
        value.buckets.values.forEach { entries ->
            entries.forEach { entry ->
                entry.item = entry.item.trim()
                entry.chance = entry.chance.coerceIn(0.0, 100.0)
                entry.min = entry.min.coerceAtLeast(1)
                entry.max = entry.max.coerceAtLeast(entry.min)
            }
            entries.removeIf { it.item.isEmpty() }
        }
        value.missions.removeIf { it.id.isBlank() || it.bucket.isBlank() }
        value.missions.forEach { mission ->
            mission.action = if (MissionAction.byId(mission.action) != null) mission.action.lowercase() else "capture"
            mission.cycle = when {
                mission.cycle.equals("both", ignoreCase = true) -> "both"
                MissionCycle.byId(mission.cycle) != null -> mission.cycle.lowercase()
                else -> "daily"
            }
            mission.difficulties = mission.difficulties
                .map(String::trim)
                .filter { Difficulty.byId(it) != null }
                .distinct()
                .toMutableList()
            if (mission.difficulties.isEmpty()) mission.difficulties = mutableListOf("easy")
            mission.rolls = mission.rolls.coerceAtLeast(0)
        }
    }
}