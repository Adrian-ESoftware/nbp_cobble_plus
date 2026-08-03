package com.nbp.cobbleplus.feature.impl

import com.cobblemon.mod.common.api.drop.DropEntry
import com.cobblemon.mod.common.api.drop.ItemDropEntry
import com.nbp.cobbleplus.config.PokemonLootAddition
import com.nbp.cobbleplus.config.PokemonLootRule
import com.nbp.cobbleplus.config.PokemonLootConfig
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PokemonLootModifierFeatureTest {
    @Test fun `default legendary rules add only nether stars`() {
        val defaults = PokemonLootConfig.defaultData()
        val legendary = setOf("arceus", "eternatus", "necrozma", "deoxys", "giratina")
        assertEquals(legendary, defaults.species.keys)
        assertTrue(defaults.species.values.all { it.add.single().item == "minecraft:nether_star" })
    }

    @Test fun `normalizes short and namespaced species ids`() {
        assertEquals("cobblemon:pikachu", PokemonLootModifierFeature.normalizeSpecies("Pikachu"))
        assertEquals("cobblemon:pikachu", PokemonLootModifierFeature.normalizeSpecies("cobblemon:Pikachu"))
    }

    @Test fun `removes only configured item and preserves original remainder`() {
        val drops = mutableListOf<DropEntry>(item("minecraft:rotten_flesh"), item("minecraft:bone"))
        PokemonLootModifierFeature.applyRule(drops, PokemonLootRule(remove = mutableListOf("minecraft:bone")), RandomSource.create(1L))
        assertEquals(1, drops.size)
        assertEquals(ResourceLocation.parse("minecraft:rotten_flesh"), (drops.single() as ItemDropEntry).item)
    }

    @Test fun `adds configured quantity without replacing original loot`() {
        val drops = mutableListOf<DropEntry>(item("minecraft:bone"))
        val rule = PokemonLootRule(add = mutableListOf(PokemonLootAddition("minecraft:diamond", 1.0, 2, 2)))
        PokemonLootModifierFeature.applyRule(drops, rule, RandomSource.create(2L))
        assertTrue(drops.any { it is ItemDropEntry && it.item == ResourceLocation.parse("minecraft:bone") })
        val diamond = drops.filterIsInstance<ItemDropEntry>().single { it.item == ResourceLocation.parse("minecraft:diamond") }
        assertEquals(2, diamond.quantity)
    }

    @Test fun `zero chance does not add item`() {
        val drops = mutableListOf<DropEntry>()
        val rule = PokemonLootRule(add = mutableListOf(PokemonLootAddition("minecraft:diamond", 0.0, 1, 1)))
        PokemonLootModifierFeature.applyRule(drops, rule, RandomSource.create(3L))
        assertFalse(drops.any())
    }

    private fun item(id: String) = ItemDropEntry().apply { item = ResourceLocation.parse(id) }
}
