package com.nbp.cobbleplus.mission

/** Uma entrada de recompensa: item + chance (%) + quantidade mínima/máxima. */
data class RewardEntry(
    var item: String = "minecraft:stick",
    var chance: Double = 50.0,
    var min: Int = 1,
    var max: Int = 1
)

/** Resultado de uma rolagem: item sorteado com quantidade rolada. */
data class RewardRollResult(val itemId: String, val count: Int)