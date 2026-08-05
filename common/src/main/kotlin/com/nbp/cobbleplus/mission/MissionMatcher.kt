package com.nbp.cobbleplus.mission

/**
 * Matcher puro de missões. Recebe os dados do Pokémon capturado/derrotado e decide
 * se ele satisfaz o alvo da missão. Mantido sem dependência de Minecraft para ser
 * testável em unit tests.
 *
 * Regra: quando o alvo especifica species/type/nature, o Pokémon deve satisfazer
 * TODOS os filtros especificados. Alvo nulo/vazio = qualquer Pokémon.
 */
object MissionMatcher {

    fun matches(
        action: MissionAction,
        target: MissionTargetValue?,
        speciesPath: String,
        types: List<String>,
        naturePath: String
    ): Boolean {
        val t = target ?: return true
        if (t.species != null && !speciesPath.equals(t.species, ignoreCase = true)) return false
        if (t.type != null && types.none { it.equals(t.type, ignoreCase = true) }) return false
        if (t.nature != null && !naturePath.equals(t.nature, ignoreCase = true)) return false
        return true
    }
}