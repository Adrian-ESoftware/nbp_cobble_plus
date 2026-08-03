package com.nbp.cobbleplus.feature.impl

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.nbp.cobbleplus.block.ModBlocks
import com.nbp.cobbleplus.item.ModItems
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.entity.EntityType
import net.minecraft.world.phys.AABB
import kotlin.math.cos
import kotlin.math.sin

object RitualBlocksFeature {
    const val REQUIRED_PLATES = 18
    const val RITUAL_TICKS = 90L

    val plates = linkedSetOf(
        "mega_showdown:stone_plate", "mega_showdown:earth_plate", "mega_showdown:legend_plate",
        "mega_showdown:flame_plate", "mega_showdown:splash_plate", "mega_showdown:zap_plate",
        "mega_showdown:meadow_plate", "mega_showdown:icicle_plate", "mega_showdown:fist_plate",
        "mega_showdown:toxic_plate", "mega_showdown:iron_plate", "mega_showdown:pixie_plate",
        "mega_showdown:sky_plate", "mega_showdown:mind_plate", "mega_showdown:insect_plate",
        "mega_showdown:dread_plate", "mega_showdown:draco_plate", "mega_showdown:spooky_plate"
    )

    data class TrioConfig(val species: String, val orb: String)

    fun useChalice(level: ServerLevel, pos: BlockPos, player: Player, stack: ItemStack): Boolean {
        val data = data(level)
        val state = data.entry(pos)
        if (state.getBoolean("complete")) {
            val flute = ItemStack(ModItems.AZURE_FLUTE)
            if (!player.inventory.add(flute)) player.drop(flute, false)
            level.setBlockAndUpdate(pos, ModBlocks.USED_ARCEUS_CHALICE.defaultBlockState())
            removeChaliceDisplays(level, pos)
            data.remove(pos)
            player.displayClientMessage(Component.translatable("message.nbp_cobble_plus.chalice.flute_taken"), true)
            level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1f, 1f)
            return true
        }

        val heldId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.item).toString()
        val inserted = RitualRules.decodePlates(state.getString("plates"))
        if (heldId in plates) {
            if (!inserted.add(heldId)) {
                player.displayClientMessage(Component.translatable("message.nbp_cobble_plus.chalice.duplicate"), true)
                return true
            }
            consume(player, stack)
            state.putString("plates", inserted.joinToString(","))
            if (inserted.size == REQUIRED_PLATES) state.putBoolean("complete", true)
            data.setDirty()
            level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1f, 1.2f)
            player.displayClientMessage(Component.translatable("message.nbp_cobble_plus.chalice.progress", inserted.size, REQUIRED_PLATES), true)
            if (inserted.size == REQUIRED_PLATES) {
                level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x + .5, pos.y + 1.0, pos.z + .5, 100, .5, .8, .5, .1)
                player.displayClientMessage(Component.translatable("message.nbp_cobble_plus.chalice.complete"), true)
                ensureChaliceDisplays(level, pos)
            }
            return true
        }
        player.displayClientMessage(Component.translatable("message.nbp_cobble_plus.chalice.progress", inserted.size, REQUIRED_PLATES), true)
        return true
    }

    fun useTrio(level: ServerLevel, pos: BlockPos, player: Player, stack: ItemStack, config: TrioConfig): Boolean {
        val data = data(level)
        val state = data.entry(pos)
        val heldId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.item).toString()
        if (stack.`is`(ModItems.RED_CHAIN)) {
            if (state.getBoolean("active")) {
                player.displayClientMessage(Component.translatable("message.nbp_cobble_plus.trio.already_active", config.species), true)
                return true
            }
            consume(player, stack)
            state.putBoolean("active", true)
            data.setDirty()
            level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5f, 1f)
            player.displayClientMessage(Component.translatable("message.nbp_cobble_plus.trio.activated", config.species), true)
            return true
        }
        if (heldId == config.orb) {
            if (!state.getBoolean("active")) {
                player.displayClientMessage(Component.translatable("message.nbp_cobble_plus.trio.needs_chain"), true)
                return true
            }
            if (state.getBoolean("ritual")) return true
            consume(player, stack)
            state.putBoolean("ritual", true)
            state.putString("species", config.species)
            state.putLong("finish", level.gameTime + RITUAL_TICKS)
            data.setDirty()
            ensureTrioDisplay(level, pos, config.species)
            level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.MASTER, 1.8f, 1f)
            player.displayClientMessage(Component.translatable("message.nbp_cobble_plus.trio.responds", config.species), true)
            return true
        }
        return false
    }

    fun tick(server: MinecraftServer) {
        server.allLevels.forEach { level ->
            val data = data(level)
            if (level.gameTime % 4L == 0L) animateChalices(level, data)
            if (level.gameTime % 4L == 0L) animateTrioRituals(level, data)
            val due = data.due(level.gameTime)
            due.forEach { (pos, state) ->
                val expected = when (state.getString("species")) {
                    "dialga" -> ModBlocks.DIALGA_BLOCK
                    "palkia" -> ModBlocks.PALKIA_BLOCK
                    "giratina" -> ModBlocks.GIRATINA_BLOCK
                    else -> null
                }
                if (expected != null && level.getBlockState(pos).`is`(expected)) {
                    val spawnPos = LegendarySpawnSafety.find(level, pos.above(2), 10) ?: pos.above(2)
                    val pokemon = PokemonProperties.parse("species=${state.getString("species")} level=60 min_perfect_ivs=4").createEntity(level)
                    pokemon.moveTo(spawnPos.x + .5, spawnPos.y.toDouble(), spawnPos.z + .5, level.random.nextFloat() * 360f, 0f)
                    pokemon.setPersistenceRequired()
                    if (level.addFreshEntity(pokemon)) LegendaryVisuals.apply(pokemon, state.getString("species"))
                    val used = when (expected) {
                        ModBlocks.DIALGA_BLOCK -> ModBlocks.USED_DIALGA_BLOCK
                        ModBlocks.PALKIA_BLOCK -> ModBlocks.USED_PALKIA_BLOCK
                        else -> ModBlocks.USED_GIRATINA_BLOCK
                    }
                    level.setBlockAndUpdate(pos, used.defaultBlockState())
                    level.sendParticles(ParticleTypes.EXPLOSION, spawnPos.x + .5, spawnPos.y + 1.0, spawnPos.z + .5, 12, .7, 1.0, .7, .05)
                    level.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.MASTER, 1.6f, 1f)
                }
                removeTrioDisplay(level, pos)
                data.remove(pos)
            }
        }
    }

    private fun animateTrioRituals(level: ServerLevel, data: RitualSavedData) {
        data.rituals().forEach { (pos, state) ->
            val species = state.getString("species")
            val expected = when (species) { "dialga" -> ModBlocks.DIALGA_BLOCK; "palkia" -> ModBlocks.PALKIA_BLOCK; "giratina" -> ModBlocks.GIRATINA_BLOCK; else -> null }
            if (expected == null || !level.getBlockState(pos).`is`(expected)) {
                removeTrioDisplay(level, pos); data.remove(pos); return@forEach
            }
            ensureTrioDisplay(level, pos, species)
            val elapsed = (level.gameTime - (state.getLong("finish") - RITUAL_TICKS)).coerceIn(0L, RITUAL_TICKS)
            val progress = elapsed.toDouble() / RITUAL_TICKS
            val y = pos.y + 1.15 + progress * 2.3
            val display = level.getEntities(EntityType.ITEM_DISPLAY, AABB(pos).inflate(4.0)) { trioTag(pos) in it.tags }.firstOrNull()
            display?.setPos(pos.x + .5, y, pos.z + .5)
            display?.setYRot((progress * progress * 1440.0).toFloat())
            val particle = when (species) { "dialga" -> ParticleTypes.ELECTRIC_SPARK; "palkia" -> ParticleTypes.DRAGON_BREATH; else -> ParticleTypes.PORTAL }
            level.sendParticles(particle, pos.x + .5, y, pos.z + .5, 14, .25 + progress * .5, .25 + progress * .5, .25 + progress * .5, .03)
            level.sendParticles(ParticleTypes.ENCHANT, pos.x + .5, y, pos.z + .5, 8, .3, .3, .3, .06)
            if (elapsed % 16L < 4L) level.playSound(null, pos.x + .5, y, pos.z + .5, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.MASTER, .8f, (.7 + progress).toFloat())
        }
    }

    private fun ensureTrioDisplay(level: ServerLevel, pos: BlockPos, species: String) {
        val tag = trioTag(pos)
        if (level.getEntities(EntityType.ITEM_DISPLAY, AABB(pos).inflate(4.0)) { tag in it.tags }.isNotEmpty()) return
        val orb = when (species) { "dialga" -> "mega_showdown:adamant_orb"; "palkia" -> "mega_showdown:lustrous_orb"; else -> "mega_showdown:griseous_orb" }
        val transform = "left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.45f,0.45f,0.45f]"
        val command = "summon minecraft:item_display ${pos.x + .5} ${pos.y + 1.15} ${pos.z + .5} {Tags:[\"$tag\"],item:{id:\"$orb\",count:1},item_display:\"fixed\",transformation:{$transform}}"
        level.server.commands.performPrefixedCommand(level.server.createCommandSourceStack().withLevel(level).withSuppressedOutput(), command)
    }

    private fun removeTrioDisplay(level: ServerLevel, pos: BlockPos) {
        val tag = trioTag(pos)
        level.getEntities(EntityType.ITEM_DISPLAY, AABB(pos).inflate(5.0)) { tag in it.tags }.forEach { it.discard() }
    }

    private fun trioTag(pos: BlockPos) = "nbp_trio_${pos.asLong()}"

    private fun animateChalices(level: ServerLevel, data: RitualSavedData) {
        val angle = (level.gameTime % 360L).toDouble()
        data.completed().forEach { pos ->
            if (!level.getBlockState(pos).`is`(ModBlocks.ARCEUS_CHALICE)) { data.remove(pos); removeChaliceDisplays(level, pos); return@forEach }
            ensureChaliceDisplays(level, pos)
            val key = chaliceTag(pos)
            val displays = level.getEntities(EntityType.ITEM_DISPLAY, AABB(pos).inflate(3.0)) { key in it.tags }
            displays.forEach { display ->
                if ("nbp_chalice_flute" in display.tags) {
                    display.setYRot(angle.toFloat())
                } else {
                    val index = display.tags.firstNotNullOfOrNull { it.removePrefix("nbp_chalice_plate_").toIntOrNull() } ?: return@forEach
                    val radians = Math.toRadians(angle * 1.2 + index * 20.0)
                    val bob = sin(Math.toRadians(angle * 2.0 + index * 40.0)) * .08
                    display.setPos(pos.x + .5 + 1.2 * cos(radians), pos.y + .75 + bob, pos.z + .5 + 1.2 * sin(radians))
                    display.setYRot((angle * 1.2).toFloat())
                }
            }
        }
    }

    private fun ensureChaliceDisplays(level: ServerLevel, pos: BlockPos) {
        val key = chaliceTag(pos)
        val existing = level.getEntities(EntityType.ITEM_DISPLAY, AABB(pos).inflate(3.0)) { key in it.tags }
        if (existing.size == REQUIRED_PLATES + 1 && existing.all { "nbp_chalice_small_v2" in it.tags }) return
        existing.forEach { it.discard() }
        val source = level.server.createCommandSourceStack().withLevel(level).withSuppressedOutput()
        val fluteTransform = "left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.25f,0.25f,0.25f]"
        val plateTransform = "left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.22f,0.22f,0.22f]"
        val flute = "summon minecraft:item_display ${pos.x + .5} ${pos.y + 1.75} ${pos.z + .5} {Tags:[\"$key\",\"nbp_chalice_flute\",\"nbp_chalice_small_v2\"],item:{id:\"nbp_cobble_plus:azure_flute\",count:1},item_display:\"fixed\",transformation:{$fluteTransform}}"
        level.server.commands.performPrefixedCommand(source, flute)
        plates.forEachIndexed { index, plate ->
            val radians = Math.toRadians(index * 20.0)
            val x = pos.x + .5 + 1.2 * cos(radians)
            val z = pos.z + .5 + 1.2 * sin(radians)
            val command = "summon minecraft:item_display $x ${pos.y + .75} $z {Tags:[\"$key\",\"nbp_chalice_plate_$index\",\"nbp_chalice_small_v2\"],item:{id:\"$plate\",count:1},item_display:\"fixed\",transformation:{$plateTransform}}"
            level.server.commands.performPrefixedCommand(source, command)
        }
    }

    private fun removeChaliceDisplays(level: ServerLevel, pos: BlockPos) {
        val key = chaliceTag(pos)
        level.getEntities(EntityType.ITEM_DISPLAY, AABB(pos).inflate(3.0)) { key in it.tags }.forEach { it.discard() }
    }

    private fun chaliceTag(pos: BlockPos) = "nbp_chalice_${pos.asLong()}"

    private fun consume(player: Player, stack: ItemStack) {
        if (!player.abilities.instabuild) stack.shrink(1)
    }

    private fun data(level: ServerLevel): RitualSavedData = level.dataStorage.computeIfAbsent(RitualSavedData.FACTORY, "nbp_ritual_blocks")
}

object RitualRules {
    fun decodePlates(encoded: String): MutableSet<String> = encoded.split(',').filter(String::isNotBlank).toMutableSet()
    fun canInsert(inserted: Set<String>, itemId: String): Boolean = itemId in RitualBlocksFeature.plates && itemId !in inserted
    fun isComplete(inserted: Set<String>): Boolean = inserted.size == RitualBlocksFeature.REQUIRED_PLATES && inserted.containsAll(RitualBlocksFeature.plates)
}

class RitualSavedData(private val entries: CompoundTag = CompoundTag()) : SavedData() {
    fun entry(pos: BlockPos): CompoundTag {
        val key = pos.asLong().toString()
        if (!entries.contains(key)) entries.put(key, CompoundTag())
        return entries.getCompound(key)
    }

    fun remove(pos: BlockPos) { entries.remove(pos.asLong().toString()); setDirty() }

    fun due(time: Long): List<Pair<BlockPos, CompoundTag>> = entries.allKeys.mapNotNull { key ->
        val value = entries.getCompound(key)
        if (value.getBoolean("ritual") && value.getLong("finish") <= time) BlockPos.of(key.toLong()) to value else null
    }

    fun completed(): List<BlockPos> = entries.allKeys.mapNotNull { key ->
        if (entries.getCompound(key).getBoolean("complete")) key.toLongOrNull()?.let(BlockPos::of) else null
    }

    fun rituals(): List<Pair<BlockPos, CompoundTag>> = entries.allKeys.mapNotNull { key ->
        val value = entries.getCompound(key)
        if (value.getBoolean("ritual")) key.toLongOrNull()?.let { BlockPos.of(it) to value } else null
    }

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag = tag.apply { put("entries", entries) }

    companion object {
        val FACTORY = Factory(::RitualSavedData, { tag, _ -> RitualSavedData(tag.getCompound("entries")) }, DataFixTypes.LEVEL)
    }
}
