package com.nbp.cobbleplus.feature.impl

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import java.util.UUID
import java.util.stream.Stream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BuffStateTest {

    @Test
    fun `active buff replacement resets duration and updates level`() {
        val state = ActiveBuffState("speed", 1, 450, false)
        assertEquals(1, state.level)
        assertEquals(450, state.remainingSeconds)

        // Purchasing level 2 replaces previous level and duration
        val newDuration = BuffCatalog.SPEED.durationSeconds(2)
        state.level = 2
        state.remainingSeconds = newDuration

        assertEquals(2, state.level)
        assertEquals(1800, state.remainingSeconds)
    }

    @Test
    fun `fly buff single level and granted flag behavior`() {
        val fly = BuffCatalog.FLY
        assertEquals(1, fly.maxLevel)
        assertEquals(10L, fly.cost(1))

        val state = ActiveBuffState("fly", 1, 1800, flyGranted = true)
        assertTrue(state.flyGranted)
        assertEquals(1, state.level)
    }

    @Test
    fun `ev gain calculation gives correct boosted values`() {
        // Levels 1 to 5: +20%, +40%, +60%, +80%, +100%
        val baseEv = 2
        val expectedMinGains = listOf(2, 2, 3, 3, 4) // floor of (2 * (1 + bonus))
        val expectedMaxGains = listOf(3, 3, 4, 4, 4) // ceiling of (2 * (1 + bonus))

        for (lvl in 1..5) {
            val bonus = BuffCatalog.EV_GAIN.effectValue(lvl)
            val raw = baseEv * (1.0 + bonus)
            val baseGain = raw.toInt()
            assertTrue(baseGain in expectedMinGains[lvl - 1]..expectedMaxGains[lvl - 1])
        }

        // At level 5 (+100%), 2 EVs should exactly double to 4 EVs without variance
        val lvl5Bonus = BuffCatalog.EV_GAIN.effectValue(5)
        val lvl5Raw = baseEv * (1.0 + lvl5Bonus)
        assertEquals(4, lvl5Raw.toInt())
    }

    @Test
    fun `drop calculation gives correct boosted quantities`() {
        // At level 5 (+100%), wholeMultiplier is 1.0 -> duplicates every drop
        val lvl5Bonus = BuffCatalog.ITEM_DROPS.effectValue(5)
        assertEquals(1.0, lvl5Bonus)
        assertEquals(1, lvl5Bonus.toInt())
        assertEquals(0.0, lvl5Bonus - lvl5Bonus.toInt())

        // At level 1 (+20%), wholeMultiplier is 0, fractionalChance is 0.20
        val lvl1Bonus = BuffCatalog.ITEM_DROPS.effectValue(1)
        assertEquals(0.20, lvl1Bonus)
        assertEquals(0, lvl1Bonus.toInt())
        assertEquals(0.20, lvl1Bonus - lvl1Bonus.toInt())
    }

    @Test
    fun `all point types correspond to expected categories`() {
        // Victory: XP, Drops, EVs, Player buffs
        assertEquals(PointType.VICTORY, BuffCatalog.BATTLE_EXP.pointType)
        assertEquals(PointType.VICTORY, BuffCatalog.ITEM_DROPS.pointType)
        assertEquals(PointType.VICTORY, BuffCatalog.EV_GAIN.pointType)
        assertEquals(PointType.VICTORY, BuffCatalog.SPEED.pointType)
        assertEquals(PointType.VICTORY, BuffCatalog.STRENGTH.pointType)
        assertEquals(PointType.VICTORY, BuffCatalog.RESISTANCE.pointType)
        assertEquals(PointType.VICTORY, BuffCatalog.REGENERATION.pointType)

        // Capture: Catch rate
        assertEquals(PointType.CAPTURE, BuffCatalog.CATCH_RATE.pointType)

        // Shiny: Shiny rate, Hidden ability
        assertEquals(PointType.SHINY, BuffCatalog.SHINY_RATE.pointType)
        assertEquals(PointType.SHINY, BuffCatalog.HIDDEN_ABILITY.pointType)

        // Legendary: Rare Pokemon, Fly
        assertEquals(PointType.LEGENDARY, BuffCatalog.RARE_SPAWN.pointType)
        assertEquals(PointType.LEGENDARY, BuffCatalog.FLY.pointType)
    }

    @Test
    fun `player attribute calculations are accurate across levels`() {
        for (lvl in 1..5) {
            // Speed (+20% to +100%)
            val speedBonus = BuffCatalog.SPEED.effectValue(lvl)
            assertEquals(0.20 * lvl, speedBonus, 0.001)

            // Strength (+3 to +15 damage)
            val strBonus = BuffCatalog.STRENGTH.effectValue(lvl)
            assertEquals(3.0 * lvl, strBonus, 0.001)

            // Resistance: 4 * lvl armor, 2 * lvl toughness, 0.1 * lvl knockback
            val armor = 4.0 * lvl
            val tough = 2.0 * lvl
            val knock = 0.10 * lvl
            assertEquals(4.0 * lvl, armor)
            assertEquals(2.0 * lvl, tough)
            assertEquals(0.10 * lvl, knock, 0.001)

            // Regeneration: 4 * lvl max HP
            val hp = 4.0 * lvl
            assertEquals(4.0 * lvl, hp)

            // Regeneration passive tick intervals: 50, 40, 30, 20, 10
            val expectedInterval = when (lvl) {
                1 -> 50
                2 -> 40
                3 -> 30
                4 -> 20
                else -> 10
            }
            assertTrue(expectedInterval in 10..50)
        }
    }

    @Test
    fun `buff saved data correctly persists and restores accounts and buffs`() {
        val registries = HolderLookup.Provider.create(Stream.empty())
        val data = BuffSavedData()
        val playerUuid = UUID.randomUUID()

        data.accounts[playerUuid] = mutableMapOf(
            "speed" to ActiveBuffState("speed", 3, 1200, false),
            "fly" to ActiveBuffState("fly", 1, 600, true)
        )

        val compound = CompoundTag()
        data.save(compound, registries)

        val loaded = BuffSavedData.load(compound, registries)
        val loadedBuffs = loaded.accounts[playerUuid]
        assertNotNull(loadedBuffs)
        assertEquals(2, loadedBuffs.size)

        val speed = loadedBuffs["speed"]
        assertNotNull(speed)
        assertEquals("speed", speed.buffId)
        assertEquals(3, speed.level)
        assertEquals(1200, speed.remainingSeconds)
        assertEquals(false, speed.flyGranted)

        val fly = loadedBuffs["fly"]
        assertNotNull(fly)
        assertEquals("fly", fly.buffId)
        assertEquals(1, fly.level)
        assertEquals(600, fly.remainingSeconds)
        assertEquals(true, fly.flyGranted)
    }
}
