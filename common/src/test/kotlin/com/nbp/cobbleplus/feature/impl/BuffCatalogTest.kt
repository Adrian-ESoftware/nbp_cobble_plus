package com.nbp.cobbleplus.feature.impl

import com.nbp.cobbleplus.config.BuffConfigEntry
import com.nbp.cobbleplus.config.NbpConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BuffCatalogTest {

    @Test
    fun `all 12 required buffs are registered in catalog`() {
        assertEquals(12, BuffCatalog.ALL.size)

        val expectedIds = listOf(
            "battle_exp", "catch_rate", "shiny_rate", "hidden_ability",
            "item_drops", "ev_gain", "rare_spawn",
            "speed", "strength", "resistance", "regeneration", "fly"
        )

        expectedIds.forEach { id ->
            val buff = BuffCatalog.get(id)
            assertNotNull(buff, "Buff $id must exist in BuffCatalog")
            assertEquals(id, buff.id)
        }
    }

    @Test
    fun `pokemon buffs match point types, costs, levels and effects`() {
        // Battle EXP
        val xp = BuffCatalog.BATTLE_EXP
        assertEquals(BuffCategory.POKEMON, xp.category)
        assertEquals(PointType.VICTORY, xp.pointType)
        assertEquals(5, xp.maxLevel)
        assertEquals(listOf(20L, 40L, 70L, 100L, 140L), (1..5).map { xp.cost(it) })
        assertEquals(listOf(0.20, 0.40, 0.60, 0.80, 1.00), (1..5).map { xp.effectValue(it) })

        // Catch Rate
        val cr = BuffCatalog.CATCH_RATE
        assertEquals(BuffCategory.POKEMON, cr.category)
        assertEquals(PointType.CAPTURE, cr.pointType)
        assertEquals(5, cr.maxLevel)
        assertEquals(listOf(20L, 40L, 70L, 100L, 140L), (1..5).map { cr.cost(it) })
        assertEquals(listOf(0.10, 0.20, 0.30, 0.40, 0.50), (1..5).map { cr.effectValue(it) })

        // Shiny Rate
        val shiny = BuffCatalog.SHINY_RATE
        assertEquals(BuffCategory.POKEMON, shiny.category)
        assertEquals(PointType.SHINY, shiny.pointType)
        assertEquals(5, shiny.maxLevel)
        assertEquals(listOf(5L, 10L, 20L, 35L, 50L), (1..5).map { shiny.cost(it) })
        assertEquals(listOf(0.10, 0.20, 0.30, 0.40, 0.50), (1..5).map { shiny.effectValue(it) })

        // Hidden Ability
        val ha = BuffCatalog.HIDDEN_ABILITY
        assertEquals(BuffCategory.POKEMON, ha.category)
        assertEquals(PointType.SHINY, ha.pointType)
        assertEquals(5, ha.maxLevel)
        assertEquals(listOf(5L, 10L, 20L, 35L, 50L), (1..5).map { ha.cost(it) })
        assertEquals(listOf(0.10, 0.20, 0.30, 0.40, 0.50), (1..5).map { ha.effectValue(it) })

        // Item Drops
        val drops = BuffCatalog.ITEM_DROPS
        assertEquals(BuffCategory.POKEMON, drops.category)
        assertEquals(PointType.VICTORY, drops.pointType)
        assertEquals(5, drops.maxLevel)
        assertEquals(listOf(20L, 40L, 70L, 100L, 140L), (1..5).map { drops.cost(it) })
        assertEquals(listOf(0.20, 0.40, 0.60, 0.80, 1.00), (1..5).map { drops.effectValue(it) })

        // EV Gain
        val ev = BuffCatalog.EV_GAIN
        assertEquals(BuffCategory.POKEMON, ev.category)
        assertEquals(PointType.VICTORY, ev.pointType)
        assertEquals(5, ev.maxLevel)
        assertEquals(listOf(20L, 40L, 70L, 100L, 140L), (1..5).map { ev.cost(it) })
        assertEquals(listOf(0.20, 0.40, 0.60, 0.80, 1.00), (1..5).map { ev.effectValue(it) })

        // Rare Spawn
        val rare = BuffCatalog.RARE_SPAWN
        assertEquals(BuffCategory.POKEMON, rare.category)
        assertEquals(PointType.LEGENDARY, rare.pointType)
        assertEquals(5, rare.maxLevel)
        assertEquals(listOf(5L, 10L, 20L, 35L, 50L), (1..5).map { rare.cost(it) })
        assertEquals(listOf(0.10, 0.20, 0.30, 0.40, 0.50), (1..5).map { rare.effectValue(it) })
    }

    @Test
    fun `player buffs match point types, costs, levels and effects`() {
        val playerEffects = listOf(
            BuffCatalog.SPEED,
            BuffCatalog.STRENGTH,
            BuffCatalog.RESISTANCE,
            BuffCatalog.REGENERATION
        )

        playerEffects.forEach { buff ->
            assertEquals(BuffCategory.PLAYER, buff.category)
            assertEquals(PointType.VICTORY, buff.pointType)
            assertEquals(5, buff.maxLevel)
            assertEquals(listOf(20L, 40L, 70L, 100L, 140L), (1..5).map { buff.cost(it) })
        }

        // Speed attribute: +20% to +100%
        assertEquals(listOf(0.20, 0.40, 0.60, 0.80, 1.00), (1..5).map { BuffCatalog.SPEED.effectValue(it) })

        // Strength attribute: +3 to +15 attack damage
        assertEquals(listOf(3.0, 6.0, 9.0, 12.0, 15.0), (1..5).map { BuffCatalog.STRENGTH.effectValue(it) })

        // Resistance attribute: levels 1 to 5
        assertEquals(listOf(1.0, 2.0, 3.0, 4.0, 5.0), (1..5).map { BuffCatalog.RESISTANCE.effectValue(it) })

        // Regeneration attribute: levels 1 to 5
        assertEquals(listOf(1.0, 2.0, 3.0, 4.0, 5.0), (1..5).map { BuffCatalog.REGENERATION.effectValue(it) })

        // Fly
        val fly = BuffCatalog.FLY
        assertEquals(BuffCategory.PLAYER, fly.category)
        assertEquals(PointType.LEGENDARY, fly.pointType)
        assertEquals(1, fly.maxLevel)
        assertEquals(10L, fly.cost(1))
        assertEquals(1.0, fly.effectValue(1))
    }

    @Test
    fun `default duration is 30 minutes (1800 seconds)`() {
        BuffCatalog.ALL.forEach { buff ->
            assertEquals(1800, buff.durationSeconds(1), "Buff ${buff.id} default duration should be 1800s")
        }
    }

    @Test
    fun `config overrides are respected when present`() {
        val customBuffs = mapOf(
            "battle_exp" to BuffConfigEntry(
                durationMinutes = 45,
                costs = listOf(25L, 50L, 80L, 120L, 160L),
                effectValues = listOf(0.25, 0.50, 0.75, 1.00, 1.25)
            )
        )
        NbpConfig.data.buffs.buffs = customBuffs

        try {
            val buff = BuffCatalog.BATTLE_EXP
            assertEquals(2700, buff.durationSeconds(1)) // 45 * 60
            assertEquals(25L, buff.cost(1))
            assertEquals(160L, buff.cost(5))
            assertEquals(0.25, buff.effectValue(1))
            assertEquals(1.25, buff.effectValue(5))
        } finally {
            NbpConfig.data.buffs.buffs = emptyMap()
        }
    }

    @Test
    fun `roman numerals formatted correctly`() {
        assertEquals("I", roman(1))
        assertEquals("II", roman(2))
        assertEquals("III", roman(3))
        assertEquals("IV", roman(4))
        assertEquals("V", roman(5))
    }

    @Test
    fun `effect formatting produces readable strings`() {
        assertEquals("+20%", BuffCatalog.BATTLE_EXP.formattedEffect(1, true))
        assertEquals("+100%", BuffCatalog.BATTLE_EXP.formattedEffect(5, true))
        assertEquals("+10% base", BuffCatalog.CATCH_RATE.formattedEffect(1, true))
        assertEquals("+40% Velocidade", BuffCatalog.SPEED.formattedEffect(2, true))
        assertEquals("+40% Speed", BuffCatalog.SPEED.formattedEffect(2, false))
        assertEquals("+6 Dano", BuffCatalog.STRENGTH.formattedEffect(2, true))
        assertEquals("+6 Damage", BuffCatalog.STRENGTH.formattedEffect(2, false))
        assertEquals("+8 Armadura", BuffCatalog.RESISTANCE.formattedEffect(2, true))
        assertEquals("+8 Armor", BuffCatalog.RESISTANCE.formattedEffect(2, false))
        assertEquals("+8 Vida Máx.", BuffCatalog.REGENERATION.formattedEffect(2, true))
        assertEquals("+8 Max HP", BuffCatalog.REGENERATION.formattedEffect(2, false))
        assertEquals("Voo criativo", BuffCatalog.FLY.formattedEffect(1, true))
    }
}
