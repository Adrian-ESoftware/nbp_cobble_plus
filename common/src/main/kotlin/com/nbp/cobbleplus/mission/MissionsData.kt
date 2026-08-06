package com.nbp.cobbleplus.mission

/**
 * Conteúdo do arquivo `config/nbp_cobble_plus/missions.json`.
 * Dificuldades (comportamento + filtro de espécie + recompensas) e as
 * definições de missões.
 */
data class MissionsData(
    var difficulties: MutableMap<String, MissionDifficultyConfig> = linkedMapOf(
        "easy" to MissionDifficultyConfig(
            weight = 40,
            min = 3,
            max = 5,
            rewardRolls = 1,
            maxPokedex = 151,
            rewards = mutableListOf(
                RewardEntry("cobblemon:poke_ball", 60.0, 3, 8),
                RewardEntry("minecraft:gold_ingot", 40.0, 1, 3)
            )
        ),
        "medium" to MissionDifficultyConfig(
            weight = 35,
            min = 6,
            max = 10,
            rewardRolls = 2,
            rewards = mutableListOf(
                RewardEntry("cobblemon:ultra_ball", 50.0, 2, 5),
                RewardEntry("minecraft:diamond", 30.0, 1, 2),
                RewardEntry("minecraft:experience_bottle", 20.0, 1, 3)
            )
        ),
        "hard" to MissionDifficultyConfig(
            weight = 20,
            min = 12,
            max = 20,
            rewardRolls = 3,
            requireLabels = mutableListOf("powerhouse"),
            rewards = mutableListOf(
                RewardEntry("cobblemon:rare_candy", 40.0, 1, 3),
                RewardEntry("cobblemon:master_ball", 10.0, 1, 1),
                RewardEntry("minecraft:golden_apple", 30.0, 1, 2)
            )
        ),
        "hardcore" to MissionDifficultyConfig(
            weight = 5,
            min = 1,
            max = 2,
            rewardRolls = 4,
            requireLabels = mutableListOf("legendary", "mythical", "ultra_beast"),
            rewards = mutableListOf(
                RewardEntry("cobblemon:ability_patch", 20.0, 1, 1),
                RewardEntry("cobblemon:max_mushroom", 25.0, 2, 4),
                RewardEntry("cobblemon:rare_candy", 40.0, 1, 2)
            )
        )
    ),
    var missions: MutableList<MissionDefinition> = mutableListOf(
        MissionDefinition(
            id = "cap_any",
            cycle = "daily",
            action = "capture",
            target = MissionTargetConfig(kind = "any"),
            difficulties = mutableListOf("easy", "medium")
        ),
        MissionDefinition(
            id = "def_any",
            cycle = "daily",
            action = "defeat",
            target = MissionTargetConfig(kind = "any"),
            difficulties = mutableListOf("easy", "medium")
        ),
        MissionDefinition(
            id = "cap_type",
            cycle = "both",
            action = "capture",
            target = MissionTargetConfig(kind = "type"),
            difficulties = mutableListOf("easy", "medium", "hard")
        ),
        MissionDefinition(
            id = "def_type",
            cycle = "both",
            action = "defeat",
            target = MissionTargetConfig(kind = "type"),
            difficulties = mutableListOf("easy", "medium", "hard")
        ),
        MissionDefinition(
            id = "cap_nature",
            cycle = "both",
            action = "capture",
            target = MissionTargetConfig(kind = "nature"),
            difficulties = mutableListOf("easy", "medium"),
            sequence = true
        ),
        MissionDefinition(
            id = "cap_species",
            cycle = "weekly",
            action = "capture",
            target = MissionTargetConfig(kind = "species"),
            difficulties = mutableListOf("hard", "hardcore"),
            sequence = true
        ),
        MissionDefinition(
            id = "def_species",
            cycle = "weekly",
            action = "defeat",
            target = MissionTargetConfig(kind = "species"),
            difficulties = mutableListOf("hard", "hardcore")
        )
    )
)