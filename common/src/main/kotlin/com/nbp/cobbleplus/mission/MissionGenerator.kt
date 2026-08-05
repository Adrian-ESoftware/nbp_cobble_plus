package com.nbp.cobbleplus.mission

import kotlin.random.Random

/**
 * Gerador puro das missões de um ciclo. Sorteia definições elegíveis, dificuldade
 * ponderada pela configuração, alvo (espécie pelo pool da dificuldade, tipo ou
 * natureza) e quantidade dentro da faixa da dificuldade.
 */
object MissionGenerator {

    fun generate(
        definitions: List<MissionDefinition>,
        difficulties: Map<String, MissionDifficultyConfig>,
        cycle: MissionCycle,
        count: Int,
        speciesPool: (String) -> List<String>,
        types: List<String>,
        natures: List<String>,
        random: Random = Random.Default
    ): List<MissionInstance> {
        val eligible = definitions.filter { cycle.matches(it.cycle) && MissionAction.byId(it.action) != null }
        if (eligible.isEmpty()) return emptyList()
        val chosen = eligible.shuffled(random).take(count.coerceAtLeast(0))
        return chosen.map { def ->
            val difficultyId = pickDifficulty(def.difficulties, difficulties, random)
            val diff = difficulties[difficultyId] ?: MissionDifficultyConfig()
            val lo = diff.min.coerceAtLeast(1)
            val hi = diff.max.coerceAtLeast(lo)
            val quantity = random.nextInt(lo, hi + 1)
            val rolls = if (def.rolls > 0) def.rolls else diff.rewardRolls.coerceAtLeast(1)
            val target = drawTarget(def, difficultyId, speciesPool, types, natures, random)
            MissionInstance(
                instanceId = "${def.id}_${cycle.id}_${random.nextLong().toString(36)}",
                cycle = cycle,
                definitionId = def.id,
                action = checkNotNull(MissionAction.byId(def.action)),
                difficulty = difficultyId,
                target = target,
                quantity = quantity,
                rewardRolls = rolls,
                bucketId = def.bucket,
                sequence = def.sequence
            )
        }
    }

    private fun pickDifficulty(
        allowed: List<String>,
        difficulties: Map<String, MissionDifficultyConfig>,
        random: Random
    ): String {
        val candidates = allowed.filter { difficulties.containsKey(it) }
        if (candidates.isEmpty()) return "easy"
        val weights = candidates.map { (difficulties[it]?.weight ?: 0).coerceAtLeast(0) }
        val total = weights.sum()
        if (total <= 0) return candidates.random(random)
        var roll = random.nextInt(total)
        candidates.forEachIndexed { index, candidate ->
            roll -= weights[index]
            if (roll < 0) return candidate
        }
        return candidates.last()
    }

    private fun drawTarget(
        def: MissionDefinition,
        difficultyId: String,
        speciesPool: (String) -> List<String>,
        types: List<String>,
        natures: List<String>,
        random: Random
    ): MissionTargetValue? {
        val target = def.target
        val kind = target.kind.lowercase()
        if (target.isAny && target.species.isEmpty() && target.type.isEmpty() && target.nature.isEmpty()) return null
        val species = when {
            target.species.isNotEmpty() -> target.species.random(random)
            kind == "species" -> speciesPool(difficultyId).randomOrNull()
            else -> null
        }
        val type = when {
            target.type.isNotEmpty() -> target.type.random(random)
            kind == "type" -> types.randomOrNull()
            else -> null
        }
        val nature = when {
            target.nature.isNotEmpty() -> target.nature.random(random)
            kind == "nature" -> natures.randomOrNull()
            else -> null
        }
        return MissionTargetValue(species = species, type = type, nature = nature)
    }
}