package com.nbp.cobbleplus.mission

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MissionCoreTest {

    // ─── MissionMatcher ───────────────────────────────────────────────────────

    @Test
    fun `null target matches everything`() {
        assertTrue(MissionMatcher.matches(MissionAction.CAPTURE, null, "pikachu", listOf("electric"), "hardy"))
        assertTrue(MissionMatcher.matches(MissionAction.DEFEAT, null, "jolteon", listOf("electric"), "jolly"))
    }

    @Test
    fun `species filter matches case-insensitively and rejects others`() {
        val target = MissionTargetValue(species = "pikachu")
        assertTrue(MissionMatcher.matches(MissionAction.CAPTURE, target, "PIKACHU", listOf("electric"), "hardy"))
        assertTrue(MissionMatcher.matches(MissionAction.CAPTURE, target, "pikachu", listOf("electric"), "hardy"))
        assertTrue(!MissionMatcher.matches(MissionAction.CAPTURE, target, "raichu", listOf("electric"), "hardy"))
    }

    @Test
    fun `type filter matches any of the pokemon types`() {
        val target = MissionTargetValue(type = "fire")
        assertTrue(MissionMatcher.matches(MissionAction.CAPTURE, target, "charizard", listOf("fire", "flying"), "hardy"))
        assertTrue(MissionMatcher.matches(MissionAction.CAPTURE, target, "magmar", listOf("fire"), "hardy"))
        assertTrue(!MissionMatcher.matches(MissionAction.CAPTURE, target, "squirtle", listOf("water"), "hardy"))
    }

    @Test
    fun `nature filter matches case-insensitively`() {
        val target = MissionTargetValue(nature = "timid")
        assertTrue(MissionMatcher.matches(MissionAction.CAPTURE, target, "alakazam", listOf("psychic"), "TIMID"))
        assertTrue(!MissionMatcher.matches(MissionAction.CAPTURE, target, "alakazam", listOf("psychic"), "brave"))
    }

    @Test
    fun `combined filters must all be satisfied`() {
        val target = MissionTargetValue(species = "gengar", type = "ghost", nature = "jolly")
        assertTrue(MissionMatcher.matches(MissionAction.CAPTURE, target, "gengar", listOf("ghost", "poison"), "jolly"))
        assertTrue(!MissionMatcher.matches(MissionAction.CAPTURE, target, "gengar", listOf("ghost", "poison"), "hardy"))
        assertTrue(!MissionMatcher.matches(MissionAction.CAPTURE, target, "gastly", listOf("ghost", "poison"), "jolly"))
    }

    // ─── MissionCycle / MissionAction ─────────────────────────────────────────

    @Test
    fun `cycle matching respects both`() {
        assertTrue(MissionCycle.DAILY.matches("daily"))
        assertTrue(MissionCycle.DAILY.matches("both"))
        assertTrue(!MissionCycle.DAILY.matches("weekly"))
        assertTrue(MissionCycle.WEEKLY.matches("weekly"))
        assertTrue(MissionCycle.WEEKLY.matches("BOTH"))
    }

    @Test
    fun `action lookup is case-insensitive`() {
        assertEquals(MissionAction.CAPTURE, MissionAction.byId("CAPTURE"))
        assertEquals(MissionAction.DEFEAT, MissionAction.byId("defeat"))
        assertNull(MissionAction.byId("eat"))
    }

    // ─── MissionRewardRoller ───────────────────────────────────────────────────

    @Test
    fun `empty rewards or zero rolls yields nothing`() {
        assertTrue(MissionRewardRoller.roll(emptyList(), 3, Random(1)).isEmpty())
        assertTrue(MissionRewardRoller.roll(listOf(RewardEntry()), 0, Random(1)).isEmpty())
    }

    @Test
    fun `first roll is forced when no entry hits`() {
        val rewards = listOf(RewardEntry(item = "minecraft:stick", chance = 0.0, min = 1, max = 1))
        val rolled = MissionRewardRoller.roll(rewards, 3, Random(42))
        assertEquals(1, rolled.size)
        assertEquals("minecraft:stick", rolled.first().itemId)
        assertEquals(1, rolled.first().count)
    }

    @Test
    fun `every roll hits when chance is certain`() {
        val rewards = listOf(RewardEntry(item = "minecraft:stick", chance = 100.0, min = 2, max = 5))
        val rolled = MissionRewardRoller.roll(rewards, 10, Random(7))
        assertEquals(10, rolled.size)
        rolled.forEach { assertTrue(it.count in 2..5) }
    }

    @Test
    fun `forced first roll count stays within entry range`() {
        val rewards = listOf(RewardEntry(item = "minecraft:gold_ingot", chance = 0.0, min = 3, max = 8))
        val rolled = MissionRewardRoller.roll(rewards, 5, Random(1))
        assertEquals(1, rolled.size)
        assertTrue(rolled.first().count in 3..8)
    }

    // ─── MissionGenerator ──────────────────────────────────────────────────────

    private val difficulties = MissionsData().difficulties
    private val types = listOf("fire", "water", "electric", "grass")
    private val natures = listOf("hardy", "jolly", "timid", "brave")

    @Test
    fun `generator returns empty when no eligible definitions`() {
        val defs = listOf(MissionDefinition(id = "x", action = "invalid_action"))
        val generated = MissionGenerator.generate(defs, difficulties, MissionCycle.DAILY, 3, { emptyList() }, types, natures, Random(1))
        assertTrue(generated.isEmpty())
    }

    @Test
    fun `generator honors requested count`() {
        val defs = MissionsData().missions
        val generated = MissionGenerator.generate(defs, difficulties, MissionCycle.DAILY, 3, { listOf("pikachu") }, types, natures, Random(1))
        assertEquals(3, generated.size)
    }

    @Test
    fun `weekly generation filters daily-only definitions`() {
        val defs = MissionsData().missions
        val dailyOnly = setOf("cap_any", "def_any")
        val generated = MissionGenerator.generate(defs, difficulties, MissionCycle.WEEKLY, 20, { listOf("pikachu") }, types, natures, Random(1))
        assertTrue(generated.isNotEmpty())
        generated.forEach { assertTrue(it.definitionId !in dailyOnly) }
    }

    @Test
    fun `kind species draws from the difficulty species pool`() {
        val defs = listOf(
            MissionDefinition(id = "s", action = "capture", target = MissionTargetConfig(kind = "species"), difficulties = mutableListOf("easy"))
        )
        val pool = listOf("bulbasaur", "charmander", "squirtle")
        val generated = MissionGenerator.generate(defs, difficulties, MissionCycle.DAILY, 1, { pool }, types, natures, Random(2))
        assertEquals(1, generated.size)
        assertNotNull(generated.first().target?.species)
        assertTrue(generated.first().target!!.species!! in pool)
    }

    @Test
    fun `fixed species list in target is used instead of the pool`() {
        val defs = listOf(
            MissionDefinition(id = "f", action = "capture", target = MissionTargetConfig(kind = "species", species = mutableListOf("eevee")), difficulties = mutableListOf("easy"))
        )
        val generated = MissionGenerator.generate(defs, difficulties, MissionCycle.DAILY, 1, { listOf("pikachu") }, types, natures, Random(3))
        assertEquals("eevee", generated.first().target?.species)
    }

    @Test
    fun `quantity respects difficulty range`() {
        val defs = listOf(
            MissionDefinition(id = "q", action = "capture", target = MissionTargetConfig(kind = "any"), difficulties = mutableListOf("easy"))
        )
        val generated = MissionGenerator.generate(defs, difficulties, MissionCycle.DAILY, 5, { listOf("pikachu") }, types, natures, Random(4))
        generated.forEach { assertTrue(it.quantity in 3..5) }
    }

    @Test
    fun `instance id embeds cycle`() {
        val defs = MissionsData().missions
        val generated = MissionGenerator.generate(defs, difficulties, MissionCycle.DAILY, 1, { listOf("pikachu") }, types, natures, Random(5))
        assertTrue(generated.first().instanceId.contains("_daily_"))
    }

    @Test
    fun `instance tag round trips all fields`() {
        val inst = MissionInstance(
            instanceId = "cap_any_daily_abc123",
            cycle = MissionCycle.WEEKLY,
            definitionId = "cap_type",
            action = MissionAction.DEFEAT,
            difficulty = "hard",
            target = MissionTargetValue(species = "garchomp", type = "dragon"),
            quantity = 12,
            rewardRolls = 3,
            sequence = true,
            progress = 4,
            completed = true,
            completedBy = java.util.UUID.randomUUID()
        )
        val restored = MissionInstance.fromTag(inst.toTag())
        assertEquals(inst.instanceId, restored.instanceId)
        assertEquals(MissionCycle.WEEKLY, restored.cycle)
        assertEquals(MissionAction.DEFEAT, restored.action)
        assertEquals("hard", restored.difficulty)
        assertEquals("garchomp", restored.target?.species)
        assertEquals("dragon", restored.target?.type)
        assertEquals(12, restored.quantity)
        assertEquals(3, restored.rewardRolls)
        assertTrue(restored.sequence)
        assertEquals(4, restored.progress)
        assertTrue(restored.completed)
        assertEquals(inst.completedBy, restored.completedBy)
    }

    @Test
    fun `targetless instance round trips with null target`() {
        val inst = MissionInstance(
            instanceId = "def_any_daily_xyz",
            cycle = MissionCycle.DAILY,
            definitionId = "def_any",
            action = MissionAction.CAPTURE,
            difficulty = "easy",
            target = null,
            quantity = 5,
            rewardRolls = 1,
            sequence = false
        )
        val restored = MissionInstance.fromTag(inst.toTag())
        assertNull(restored.target)
        assertEquals(5, restored.quantity)
    }
}