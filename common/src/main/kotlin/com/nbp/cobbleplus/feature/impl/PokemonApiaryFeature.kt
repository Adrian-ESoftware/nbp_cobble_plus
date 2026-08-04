package com.nbp.cobbleplus.feature.impl

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.nbp.cobbleplus.config.NbpConfig
import com.nbp.cobbleplus.feature.FeatureModule
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BeehiveBlock
import net.minecraft.world.level.block.entity.BeehiveBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import kotlin.math.absoluteValue

object PokemonApiaryFeature : FeatureModule {
    override val name = "Pokémon apiaries"
    override val isEnabled: Boolean get() = NbpConfig.data.pokemonApiary.enabled
    private val nextProduction = mutableMapOf<String, Long>()
    private val nextPastureDrop = mutableMapOf<java.util.UUID, Long>()

    override fun onEnable() = Unit
    override fun onDisable() {
        nextProduction.clear()
        nextPastureDrop.clear()
    }

    @JvmStatic
    fun onPastureCheck(pasture: PokemonPastureBlockEntity) {
        if (!isEnabled || !NbpConfig.data.pokemonApiary.enablePastureItemProduction) return
        val level = pasture.level as? ServerLevel ?: return
        pasture.tetheredPokemon.forEach { tether ->
            val pokemon = tether.getPokemon() ?: return@forEach
            val species = pokemon.species.resourceIdentifier.path
            if (species != "combee" && species != "vespiquen") return@forEach
            val due = nextPastureDrop[pokemon.uuid]
            if (due == null) {
                nextPastureDrop[pokemon.uuid] = level.gameTime + pastureDropDelay(level, species)
                return@forEach
            }
            if (level.gameTime < due) return@forEach

            val item = if (species == "vespiquen") Items.HONEY_BOTTLE else Items.HONEYCOMB
            val position = pokemon.entity?.position() ?: pasture.blockPos.center
            level.addFreshEntity(ItemEntity(level, position.x, position.y + 0.25, position.z, ItemStack(item)))
            nextPastureDrop[pokemon.uuid] = level.gameTime + pastureDropDelay(level, species)
        }
    }

    @JvmStatic
    fun onHiveTick(level: Level, pos: BlockPos, state: BlockState, hive: BeehiveBlockEntity) {
        if (!isEnabled || level !is ServerLevel) return
        val config = NbpConfig.data.pokemonApiary
        val interval = config.checkIntervalTicks.coerceAtLeast(20)
        if ((level.gameTime + pos.asLong().hashCode().absoluteValue) % interval != 0L) return

        val key = "${level.dimension().location()}@${pos.asLong()}"
        if (state.getValue(BeehiveBlock.HONEY_LEVEL) >= BeehiveBlock.MAX_HONEY_LEVELS) {
            nextProduction.remove(key)
            return
        }
        if (config.requireDaytime && !level.isDay) {
            nextProduction.remove(key)
            return
        }
        if (config.requireFlowers && !hasFlowers(level, pos, config.flowerRadius.coerceIn(1, 16))) {
            nextProduction.remove(key)
            return
        }

        val producer = bestProducer(level, pos, config.producerRadius.coerceIn(2.0, 32.0))
        if (producer == null) {
            nextProduction.remove(key)
            return
        }

        val due = nextProduction[key]
        if (due == null) {
            nextProduction[key] = level.gameTime + productionDelay(level, producer)
            return
        }
        if (level.gameTime < due) return

        level.setBlock(pos, state.setValue(BeehiveBlock.HONEY_LEVEL, state.getValue(BeehiveBlock.HONEY_LEVEL) + 1), 3)
        hive.setChanged()
        nextProduction[key] = level.gameTime + productionDelay(level, producer)
    }

    private fun bestProducer(level: ServerLevel, pos: BlockPos, radius: Double): String? {
        var hasCombee = false
        val area = AABB.ofSize(pos.center, radius * 2.0, radius, radius * 2.0)
        for (entity in level.getEntitiesOfClass(PokemonEntity::class.java, area) { it.isAlive }) {
            when (entity.pokemon.species.resourceIdentifier.path) {
                "vespiquen" -> return "vespiquen"
                "combee" -> hasCombee = true
            }
        }
        return if (hasCombee) "combee" else null
    }

    private fun hasFlowers(level: ServerLevel, center: BlockPos, radius: Int): Boolean {
        val bottom = center.offset(-radius, -2, -radius)
        val top = center.offset(radius, 2, radius)
        return BlockPos.betweenClosed(bottom, top).any { level.getBlockState(it).`is`(BlockTags.FLOWERS) }
    }

    private fun productionDelay(level: ServerLevel, producer: String): Long {
        val config = NbpConfig.data.pokemonApiary
        val configuredMin: Int
        val configuredMax: Int
        if (producer == "vespiquen") {
            configuredMin = config.vespiquenMinimumProductionTicks
            configuredMax = config.vespiquenMaximumProductionTicks
        } else {
            configuredMin = config.combeeMinimumProductionTicks
            configuredMax = config.combeeMaximumProductionTicks
        }
        val min = configuredMin.coerceAtLeast(20)
        val max = configuredMax.coerceAtLeast(min)
        return level.random.nextInt(max - min + 1).toLong() + min
    }

    private fun pastureDropDelay(level: ServerLevel, species: String): Long {
        val config = NbpConfig.data.pokemonApiary
        val configuredMin = if (species == "vespiquen") config.vespiquenPastureMinimumDropTicks else config.combeePastureMinimumDropTicks
        val configuredMax = if (species == "vespiquen") config.vespiquenPastureMaximumDropTicks else config.combeePastureMaximumDropTicks
        val min = configuredMin.coerceAtLeast(20)
        val max = configuredMax.coerceAtLeast(min)
        return min.toLong() + level.random.nextInt(max - min + 1)
    }
}
