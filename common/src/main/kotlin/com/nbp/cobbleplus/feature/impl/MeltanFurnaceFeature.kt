package com.nbp.cobbleplus.feature.impl

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.nbp.cobbleplus.NbpCobblePlus
import com.nbp.cobbleplus.config.NbpConfig
import com.nbp.cobbleplus.feature.FeatureModule
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object MeltanFurnaceFeature : FeatureModule {
    override val name = "Meltan from smelted iron"
    override val isEnabled: Boolean get() = NbpConfig.data.meltanFurnace.enabled
    override fun onEnable() = Unit
    override fun onDisable() = Unit

    @JvmStatic
    fun handleResultTaken(player: ServerPlayer, stack: ItemStack) {
        val config = NbpConfig.data.meltanFurnace
        if (!config.enabled || !stack.`is`(Items.IRON_INGOT) || stack.count <= 0) return

        var successes = 0
        val chance = config.chancePerIronIngot.coerceIn(0.0, 1.0)
        repeat(stack.count) { if (player.random.nextDouble() < chance) successes++ }
        if (successes == 0) return

        val level = player.serverLevel()
        val furnace = findNearestFurnace(level, player.blockPosition()) ?: return
        repeat(successes) { spawnMeltan(level, furnace.above()) }
    }

    private fun findNearestFurnace(level: ServerLevel, origin: BlockPos): BlockPos? {
        val config = NbpConfig.data.meltanFurnace
        val valid = config.validFurnaces.map { it.lowercase() }.toSet()
        val horizontal = config.horizontalSearchRadius.coerceIn(1, 32)
        val vertical = config.verticalSearchRadius.coerceIn(1, 16)
        var nearest: BlockPos? = null
        var nearestDistance = Double.MAX_VALUE

        BlockPos.betweenClosed(
            origin.offset(-horizontal, -vertical, -horizontal),
            origin.offset(horizontal, vertical, horizontal)
        ).forEach { mutablePos ->
            val pos = mutablePos.immutable()
            val id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).block).toString()
            if (id in valid && level.getBlockState(pos.above()).isAir && level.getBlockState(pos.above(2)).isAir) {
                val distance = pos.distSqr(origin)
                if (distance < nearestDistance) {
                    nearestDistance = distance
                    nearest = pos
                }
            }
        }
        return nearest
    }

    private fun spawnMeltan(level: ServerLevel, pos: BlockPos) {
        try {
            val config = NbpConfig.data.meltanFurnace
            val properties = PokemonProperties.parse("species=meltan min_perfect_ivs=${config.perfectIvCount.coerceIn(0, 6)}")
            val meltan = properties.createEntity(level)
            meltan.moveTo(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5, level.random.nextFloat() * 360F, 0F)
            level.addFreshEntity(meltan)
        } catch (exception: Exception) {
            NbpCobblePlus.logger.error("Failed to spawn Meltan from a furnace", exception)
        }
    }
}
