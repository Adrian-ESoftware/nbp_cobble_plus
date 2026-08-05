package com.nbp.cobbleplus.mission

/** Valores concretos sorteados para uma missão gerada (valores null = sem filtro). */
data class MissionTargetValue(
    val species: String? = null,
    val type: String? = null,
    val nature: String? = null
)