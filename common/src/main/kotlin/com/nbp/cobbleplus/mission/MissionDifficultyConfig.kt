package com.nbp.cobbleplus.mission

/**
 * Comportamento configurável de cada dificuldade no missions.json:
 * peso no sorteio, faixa da quantidade-alvo, rolos de recompensa, o filtro de
 * espécies pelo qual o alvo de espécie é sorteado (labels + dex máximo)
 * e a lista de recompensas associadas a esta dificuldade.
 */
data class MissionDifficultyConfig(
    var weight: Int = 25,
    var min: Int = 3,
    var max: Int = 5,
    var rewardRolls: Int = 1,
    var requireLabels: MutableList<String> = mutableListOf(),
    var excludeLabels: MutableList<String> = mutableListOf("legendary", "mythical", "ultra_beast", "restricted"),
    var maxPokedex: Int = 0,
    var rewards: MutableList<RewardEntry> = mutableListOf()
)