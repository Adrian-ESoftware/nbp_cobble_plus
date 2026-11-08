package com.nbp.cobbleplus.feature.impl

import com.nbp.cobbleplus.config.NbpConfig

enum class BuffCategory(val labelEn: String, val labelPt: String) {
    POKEMON("Pokémon", "Pokémon"),
    PLAYER("Player", "Jogador");

    fun displayName(portuguese: Boolean): String = if (portuguese) labelPt else labelEn
}

class BuffDefinition(
    val id: String,
    val category: BuffCategory,
    val pointType: PointType,
    val maxLevel: Int,
    val defaultCosts: List<Long>,
    val defaultEffectValues: List<Double>,
    val defaultDurationSeconds: Int = 1800,
    val nameEn: String,
    val namePt: String,
    val descEn: String,
    val descPt: String,
    val iconType: String = ""
) {
    fun cost(level: Int): Long {
        val configured = NbpConfig.data.buffs.buffs[id]?.costs?.getOrNull(level - 1)
        if (configured != null && configured >= 0L) return configured
        return defaultCosts.getOrElse(level - 1) { defaultCosts.last() }
    }

    fun durationSeconds(level: Int = 1): Int {
        val configuredMin = NbpConfig.data.buffs.buffs[id]?.durationMinutes
            ?: NbpConfig.data.buffs.defaultDurationMinutes
        if (configuredMin > 0) return configuredMin * 60
        return defaultDurationSeconds
    }

    fun effectValue(level: Int): Double {
        val configured = NbpConfig.data.buffs.buffs[id]?.effectValues?.getOrNull(level - 1)
        if (configured != null) return configured
        return defaultEffectValues.getOrElse(level - 1) { defaultEffectValues.last() }
    }

    fun displayName(portuguese: Boolean): String = if (portuguese) namePt else nameEn

    fun description(portuguese: Boolean): String = if (portuguese) descPt else descEn

    fun formattedEffect(level: Int, portuguese: Boolean): String {
        val value = effectValue(level)
        return when (id) {
            "battle_exp", "item_drops", "ev_gain" -> {
                val pct = (value * 100).toInt()
                "+$pct%"
            }
            "catch_rate", "shiny_rate", "hidden_ability", "rare_spawn" -> {
                val pct = (value * 100).toInt()
                if (portuguese) "+$pct% base" else "+$pct% base"
            }
            "speed" -> {
                val pct = (value * 100).toInt()
                "+$pct% ${if (portuguese) "Velocidade" else "Speed"}"
            }
            "strength" -> {
                val dmg = value.toInt()
                "+$dmg ${if (portuguese) "Dano" else "Damage"}"
            }
            "resistance" -> {
                val armor = (level * 4)
                "+$armor ${if (portuguese) "Armadura" else "Armor"}"
            }
            "regeneration" -> {
                val hp = (level * 4)
                "+$hp ${if (portuguese) "Vida Máx." else "Max HP"}"
            }
            "fly" -> {
                if (portuguese) "Voo criativo" else "Creative flight"
            }
            else -> "+$value"
        }
    }
}

object BuffCatalog {
    val BATTLE_EXP = BuffDefinition(
        id = "battle_exp",
        category = BuffCategory.POKEMON,
        pointType = PointType.VICTORY,
        maxLevel = 5,
        defaultCosts = listOf(20L, 40L, 70L, 100L, 140L),
        defaultEffectValues = listOf(0.20, 0.40, 0.60, 0.80, 1.00),
        nameEn = "Battle EXP",
        namePt = "XP de Batalha",
        descEn = "Increases EXP earned by your Pokémon in battles",
        descPt = "Aumenta o XP ganho pelos seus Pokémon em batalhas",
        iconType = "fighting"
    )

    val CATCH_RATE = BuffDefinition(
        id = "catch_rate",
        category = BuffCategory.POKEMON,
        pointType = PointType.CAPTURE,
        maxLevel = 5,
        defaultCosts = listOf(20L, 40L, 70L, 100L, 140L),
        defaultEffectValues = listOf(0.10, 0.20, 0.30, 0.40, 0.50),
        nameEn = "Catch Rate",
        namePt = "Chance de Captura",
        descEn = "Multiplies the base Poké Ball catch rate",
        descPt = "Multiplica a chance base de captura das Poké Bolas",
        iconType = "grass"
    )

    val SHINY_RATE = BuffDefinition(
        id = "shiny_rate",
        category = BuffCategory.POKEMON,
        pointType = PointType.SHINY,
        maxLevel = 5,
        defaultCosts = listOf(5L, 10L, 20L, 35L, 50L),
        defaultEffectValues = listOf(0.10, 0.20, 0.30, 0.40, 0.50),
        nameEn = "Shiny Rate",
        namePt = "Chance Shiny",
        descEn = "Multiplies the base chance of encountering shiny Pokémon",
        descPt = "Multiplica a chance base de encontrar Pokémon Shiny",
        iconType = "electric"
    )

    val HIDDEN_ABILITY = BuffDefinition(
        id = "hidden_ability",
        category = BuffCategory.POKEMON,
        pointType = PointType.SHINY,
        maxLevel = 5,
        defaultCosts = listOf(5L, 10L, 20L, 35L, 50L),
        defaultEffectValues = listOf(0.10, 0.20, 0.30, 0.40, 0.50),
        nameEn = "Hidden Ability",
        namePt = "Habilidade Oculta",
        descEn = "Multiplies the base chance of wild Pokémon having hidden abilities",
        descPt = "Multiplica a chance base de Pokémon selvagens virem com habilidade oculta",
        iconType = "psychic"
    )

    val ITEM_DROPS = BuffDefinition(
        id = "item_drops",
        category = BuffCategory.POKEMON,
        pointType = PointType.VICTORY,
        maxLevel = 5,
        defaultCosts = listOf(20L, 40L, 70L, 100L, 140L),
        defaultEffectValues = listOf(0.20, 0.40, 0.60, 0.80, 1.00),
        nameEn = "Item Drops",
        namePt = "Drops de Itens",
        descEn = "Increases item drop quantities and rolls from defeated Pokémon",
        descPt = "Aumenta a quantidade e chance de drops de Pokémon derrotados",
        iconType = "water"
    )

    val EV_GAIN = BuffDefinition(
        id = "ev_gain",
        category = BuffCategory.POKEMON,
        pointType = PointType.VICTORY,
        maxLevel = 5,
        defaultCosts = listOf(20L, 40L, 70L, 100L, 140L),
        defaultEffectValues = listOf(0.20, 0.40, 0.60, 0.80, 1.00),
        nameEn = "EV Gain",
        namePt = "EVs Ganhos",
        descEn = "Increases effort values (EVs) gained by your Pokémon",
        descPt = "Aumenta os pontos de esforço (EVs) ganhos pelos seus Pokémon",
        iconType = "dragon"
    )

    val RARE_SPAWN = BuffDefinition(
        id = "rare_spawn",
        category = BuffCategory.POKEMON,
        pointType = PointType.LEGENDARY,
        maxLevel = 5,
        defaultCosts = listOf(5L, 10L, 20L, 35L, 50L),
        defaultEffectValues = listOf(0.10, 0.20, 0.30, 0.40, 0.50),
        nameEn = "Rare Pokémon",
        namePt = "Pokémon Raros",
        descEn = "Multiplies the spawn weight of rare and ultra-rare Pokémon near you",
        descPt = "Multiplica a chance de Pokémon raros e ultra-raros nascerem perto de você",
        iconType = "ghost"
    )

    val SPEED = BuffDefinition(
        id = "speed",
        category = BuffCategory.PLAYER,
        pointType = PointType.VICTORY,
        maxLevel = 5,
        defaultCosts = listOf(20L, 40L, 70L, 100L, 140L),
        defaultEffectValues = listOf(0.20, 0.40, 0.60, 0.80, 1.00),
        nameEn = "Speed",
        namePt = "Velocidade",
        descEn = "Increases Movement Speed attribute (+20% to +100%)",
        descPt = "Aumenta o atributo de Velocidade de Movimento (+20% a +100%)",
        iconType = "flying"
    )

    val STRENGTH = BuffDefinition(
        id = "strength",
        category = BuffCategory.PLAYER,
        pointType = PointType.VICTORY,
        maxLevel = 5,
        defaultCosts = listOf(20L, 40L, 70L, 100L, 140L),
        defaultEffectValues = listOf(3.0, 6.0, 9.0, 12.0, 15.0),
        nameEn = "Strength",
        namePt = "Força",
        descEn = "Increases Attack Damage attribute (+3 to +15 damage)",
        descPt = "Aumenta o atributo de Dano de Ataque (+3 a +15 de dano)",
        iconType = "fire"
    )

    val RESISTANCE = BuffDefinition(
        id = "resistance",
        category = BuffCategory.PLAYER,
        pointType = PointType.VICTORY,
        maxLevel = 5,
        defaultCosts = listOf(20L, 40L, 70L, 100L, 140L),
        defaultEffectValues = listOf(1.0, 2.0, 3.0, 4.0, 5.0),
        nameEn = "Resistance",
        namePt = "Resistência",
        descEn = "Increases Armor (+4 to +20) and Armor Toughness (+2 to +10)",
        descPt = "Aumenta os atributos de Armadura (+4 a +20) e Tenacidade (+2 a +10)",
        iconType = "steel"
    )

    val REGENERATION = BuffDefinition(
        id = "regeneration",
        category = BuffCategory.PLAYER,
        pointType = PointType.VICTORY,
        maxLevel = 5,
        defaultCosts = listOf(20L, 40L, 70L, 100L, 140L),
        defaultEffectValues = listOf(1.0, 2.0, 3.0, 4.0, 5.0),
        nameEn = "Regeneration",
        namePt = "Regeneração",
        descEn = "Increases Max Health attribute (+4 to +20 HP) and heals continuously",
        descPt = "Aumenta o atributo de Vida Máxima (+4 a +20 HP) e regenera vida",
        iconType = "fairy"
    )

    val FLY = BuffDefinition(
        id = "fly",
        category = BuffCategory.PLAYER,
        pointType = PointType.LEGENDARY,
        maxLevel = 1,
        defaultCosts = listOf(10L),
        defaultEffectValues = listOf(1.0),
        nameEn = "Creative Flight",
        namePt = "Voo Criativo",
        descEn = "Grants creative flight in survival mode",
        descPt = "Permite voar livremente no modo sobrevivência",
        iconType = "flying"
    )

    val ALL = listOf(
        BATTLE_EXP, CATCH_RATE, SHINY_RATE, HIDDEN_ABILITY, ITEM_DROPS, EV_GAIN, RARE_SPAWN,
        SPEED, STRENGTH, RESISTANCE, REGENERATION, FLY
    )

    fun get(id: String): BuffDefinition? = ALL.firstOrNull { it.id.equals(id, ignoreCase = true) }
}

fun roman(n: Int): String = when (n) {
    1 -> "I"
    2 -> "II"
    3 -> "III"
    4 -> "IV"
    5 -> "V"
    else -> n.toString()
}
