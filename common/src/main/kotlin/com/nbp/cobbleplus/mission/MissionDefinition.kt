package com.nbp.cobbleplus.mission

/**
 * Definição modelo de uma missão (uma linha de `missions` no missions.json).
 * `cycle` aceita `daily`, `weekly` ou `both`.
 */
data class MissionDefinition(
    var id: String = "",
    var cycle: String = "daily",
    var action: String = "capture",
    var target: MissionTargetConfig = MissionTargetConfig(),
    var difficulties: MutableList<String> = mutableListOf("easy", "medium"),
    var bucket: String = "",
    var rolls: Int = 0,
    var sequence: Boolean = false
)