package com.nbp.cobbleplus.mission

import kotlin.random.Random

/**
 * Rolador puro do bucket de recompensas.
 * Total = N rolos. O 1º rolo garante pelo menos um item (caso nenhuma entrada acerte a
 * chance, uma é forçada uniformemente). Os demais rolos acertam cada entrada cujo
 * sorteio de chance % passe.
 */
object MissionRewardRoller {

    fun roll(bucket: List<RewardEntry>, rolls: Int, random: Random = Random.Default): List<RewardRollResult> {
        if (bucket.isEmpty() || rolls <= 0) return emptyList()
        val results = mutableListOf<RewardRollResult>()
        for (i in 0 until rolls) {
            val hits = bucket.filter { random.nextDouble() * 100.0 < it.chance }
            if (i == 0 && hits.isEmpty()) {
                val forced = bucket.random(random)
                results += RewardRollResult(forced.item, random.nextInt(forced.min, forced.max + 1))
            } else {
                hits.forEach { entry ->
                    results += RewardRollResult(entry.item, random.nextInt(entry.min, entry.max + 1))
                }
            }
        }
        return results
    }
}