package com.nbp.cobbleplus.feature.impl

/**
 * Standard Pokémon (Gen 6+) type effectiveness chart. Cobblemon doesn't expose this anywhere in
 * Java/Kotlin (damage calculation runs inside the bundled Showdown engine), so it's hardcoded here.
 * Keyed by lowercase type name, matching Cobblemon's `ElementalType.name`.
 */
object TypeChart {
    private val chart: Map<String, Map<String, Double>> = mapOf(
        "normal" to mapOf("rock" to 0.5, "ghost" to 0.0, "steel" to 0.5),
        "fire" to mapOf("fire" to 0.5, "water" to 0.5, "grass" to 2.0, "ice" to 2.0, "bug" to 2.0, "rock" to 0.5, "dragon" to 0.5, "steel" to 2.0),
        "water" to mapOf("fire" to 2.0, "water" to 0.5, "grass" to 0.5, "ground" to 2.0, "rock" to 2.0, "dragon" to 0.5),
        "electric" to mapOf("water" to 2.0, "electric" to 0.5, "grass" to 0.5, "ground" to 0.0, "flying" to 2.0, "dragon" to 0.5),
        "grass" to mapOf("fire" to 0.5, "water" to 2.0, "grass" to 0.5, "poison" to 0.5, "ground" to 2.0, "flying" to 0.5, "bug" to 0.5, "rock" to 2.0, "dragon" to 0.5, "steel" to 0.5),
        "ice" to mapOf("fire" to 0.5, "water" to 0.5, "grass" to 2.0, "ice" to 0.5, "ground" to 2.0, "flying" to 2.0, "dragon" to 2.0, "steel" to 0.5),
        "fighting" to mapOf("normal" to 2.0, "ice" to 2.0, "poison" to 0.5, "flying" to 0.5, "psychic" to 0.5, "bug" to 0.5, "rock" to 2.0, "ghost" to 0.0, "dark" to 2.0, "steel" to 2.0, "fairy" to 0.5),
        "poison" to mapOf("grass" to 2.0, "poison" to 0.5, "ground" to 0.5, "rock" to 0.5, "ghost" to 0.5, "steel" to 0.0, "fairy" to 2.0),
        "ground" to mapOf("fire" to 2.0, "electric" to 2.0, "grass" to 0.5, "poison" to 2.0, "flying" to 0.0, "bug" to 0.5, "rock" to 2.0, "steel" to 2.0),
        "flying" to mapOf("electric" to 0.5, "grass" to 2.0, "fighting" to 2.0, "bug" to 2.0, "rock" to 0.5, "steel" to 0.5),
        "psychic" to mapOf("fighting" to 2.0, "poison" to 2.0, "psychic" to 0.5, "dark" to 0.0, "steel" to 0.5),
        "bug" to mapOf("fire" to 0.5, "grass" to 2.0, "fighting" to 0.5, "poison" to 0.5, "flying" to 0.5, "psychic" to 2.0, "ghost" to 0.5, "dark" to 2.0, "steel" to 0.5, "fairy" to 0.5),
        "rock" to mapOf("fire" to 2.0, "ice" to 2.0, "fighting" to 0.5, "ground" to 0.5, "flying" to 2.0, "bug" to 2.0, "steel" to 0.5),
        "ghost" to mapOf("normal" to 0.0, "psychic" to 2.0, "ghost" to 2.0, "dark" to 0.5),
        "dragon" to mapOf("dragon" to 2.0, "steel" to 0.5, "fairy" to 0.0),
        "dark" to mapOf("fighting" to 0.5, "psychic" to 2.0, "ghost" to 2.0, "dark" to 0.5, "fairy" to 0.5),
        "steel" to mapOf("fire" to 0.5, "water" to 0.5, "electric" to 0.5, "ice" to 2.0, "rock" to 2.0, "steel" to 0.5, "fairy" to 2.0),
        "fairy" to mapOf("fire" to 0.5, "fighting" to 2.0, "poison" to 0.5, "dragon" to 2.0, "dark" to 2.0, "steel" to 0.5)
    )

    val allTypes: Set<String> = chart.keys

    /** Multiplicador de dano de [attackingType] contra [defendingType] (1.0 se neutro/não listado). */
    fun effectiveness(attackingType: String, defendingType: String): Double =
        chart[attackingType.lowercase()]?.get(defendingType.lowercase()) ?: 1.0

    /**
     * Multiplicador combinado de [attackingType] contra um Pokémon com os tipos [defendingTypes]
     * (produto dos multiplicadores individuais, igual ao jogo faz pra tipos duplos).
     */
    fun combinedEffectiveness(attackingType: String, defendingTypes: List<String>): Double =
        defendingTypes.fold(1.0) { acc, defending -> acc * effectiveness(attackingType, defending) }
}
