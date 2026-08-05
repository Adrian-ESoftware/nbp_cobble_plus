package com.nbp.cobbleplus.mission

enum class MissionAction(val id: String) {
    CAPTURE("capture"),
    DEFEAT("defeat");

    companion object {
        fun byId(id: String): MissionAction? = entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}