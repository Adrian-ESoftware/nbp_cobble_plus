package com.nbp.cobbleplus.mission

/**
 * Alvo de uma missão conforme configurado no missions.json.
 * `kind` é `any`, `species`, `type` ou `nature`. Quando o kind não é `any` mas a
 * lista correspondente está vazia, o alvo é sorteado na geração (espécie pelo pool
 * da dificuldade, tipo entre os elementos, natureza entre as natures).
 * Quando listas são preenchidas, uma é sorteada dentre elas na geração ou usada
 * como valor fixo no caso de uma única entrada.
 */
data class MissionTargetConfig(
    var kind: String = "any",
    var species: MutableList<String> = mutableListOf(),
    var type: MutableList<String> = mutableListOf(),
    var nature: MutableList<String> = mutableListOf()
) {
    val isAny: Boolean get() = kind.equals("any", ignoreCase = true)
}