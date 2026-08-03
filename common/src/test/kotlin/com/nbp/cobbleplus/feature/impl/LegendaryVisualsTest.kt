package com.nbp.cobbleplus.feature.impl

import net.minecraft.ChatFormatting
import kotlin.test.Test
import kotlin.test.assertEquals

class LegendaryVisualsTest {
    @Test fun `creation trio receives distinct glow colors`() {
        assertEquals(ChatFormatting.AQUA, LegendaryVisuals.colorFor("dialga"))
        assertEquals(ChatFormatting.LIGHT_PURPLE, LegendaryVisuals.colorFor("palkia"))
        assertEquals(ChatFormatting.DARK_PURPLE, LegendaryVisuals.colorFor("giratina"))
    }

    @Test fun `legendary birds use elemental colors`() {
        assertEquals(ChatFormatting.YELLOW, LegendaryVisuals.colorFor("zapdos"))
        assertEquals(ChatFormatting.AQUA, LegendaryVisuals.colorFor("articuno"))
        assertEquals(ChatFormatting.RED, LegendaryVisuals.colorFor("moltres"))
    }
}
