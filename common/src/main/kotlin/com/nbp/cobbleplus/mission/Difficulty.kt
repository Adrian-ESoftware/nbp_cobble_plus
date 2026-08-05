package com.nbp.cobbleplus.mission

enum class Difficulty(val id: String, val color: String) {
    EASY("easy", "§a"),
    MEDIUM("medium", "§e"),
    HARD("hard", "§6"),
    HARDCORE("hardcore", "§c");

    companion object {
        fun byId(id: String): Difficulty? = entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}