package com.nbp.cobbleplus.feature.impl

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RitualRulesTest {
    @Test
    fun `combo HUD starts hidden until a capture exists`() {
        val state = CatchComboFeature.ComboState()
        assertFalse(state.hasCaptured)
        state.hasCaptured = true
        assertTrue(state.hasCaptured)
    }

    @Test
    fun `accepts each configured plate once`() {
        val inserted = mutableSetOf<String>()
        val plate = "mega_showdown:stone_plate"
        assertTrue(RitualRules.canInsert(inserted, plate))
        inserted += plate
        assertFalse(RitualRules.canInsert(inserted, plate))
    }

    @Test
    fun `rejects unrelated items`() {
        assertFalse(RitualRules.canInsert(emptySet(), "minecraft:stone"))
    }

    @Test
    fun `only all eighteen unique plates complete the chalice`() {
        assertFalse(RitualRules.isComplete(RitualBlocksFeature.plates.drop(1).toSet()))
        assertTrue(RitualRules.isComplete(RitualBlocksFeature.plates))
    }

    @Test
    fun `saved plate list survives decoding without duplicates`() {
        val decoded = RitualRules.decodePlates("mega_showdown:stone_plate,mega_showdown:stone_plate,mega_showdown:earth_plate")
        assertTrue(decoded.size == 2)
    }
}
