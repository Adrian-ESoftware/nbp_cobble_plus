package com.nbp.cobbleplus.mission

enum class MissionCycle(val id: String) {
    DAILY("daily"),
    WEEKLY("weekly");

    fun matches(definitionCycle: String): Boolean {
        val normalized = definitionCycle.lowercase()
        return when (this) {
            DAILY -> normalized == "daily" || normalized == "both"
            WEEKLY -> normalized == "weekly" || normalized == "both"
        }
    }

    companion object {
        fun byId(id: String): MissionCycle? = entries.firstOrNull { it.id == id }
    }
}