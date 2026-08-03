package com.nbp.cobbleplus.feature.impl

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.Heightmap
import kotlin.math.abs

object LegendarySpawnSafety {
    private const val REQUIRED_HEADROOM = 4

    fun find(level: ServerLevel, origin: BlockPos, horizontalRadius: Int = 8): BlockPos? {
        val offsets = buildList {
            add(0 to 0)
            for (radius in 1..horizontalRadius) {
                for (x in -radius..radius) for (z in -radius..radius) {
                    if (abs(x) == radius || abs(z) == radius) add(x to z)
                }
            }
        }
        return offsets.firstNotNullOfOrNull { (dx, dz) -> findColumn(level, origin.x + dx, origin.z + dz, origin.y) }
    }

    fun findColumn(level: ServerLevel, x: Int, z: Int, preferredY: Int): BlockPos? {
        if (level.dimension() != Level.NETHER) {
            val surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
            val minimum = if (level.dimension() == Level.END) 40 else maxOf(level.seaLevel - 4, level.minBuildHeight + 2)
            for (y in maxOf(surface, minimum)..minOf(surface + 12, level.maxBuildHeight - REQUIRED_HEADROOM)) {
                val candidate = BlockPos(x, y, z)
                if (isSafe(level, candidate)) return candidate
            }
            return null
        }

        val minimum = maxOf(32, level.minBuildHeight + 2)
        val maximum = minOf(115, level.maxBuildHeight - REQUIRED_HEADROOM)
        return (minimum..maximum)
            .filter { y -> isSafe(level, BlockPos(x, y, z)) }
            .minByOrNull { y -> abs(y - preferredY.coerceIn(minimum, maximum)) }
            ?.let { BlockPos(x, it, z) }
    }

    fun isSafe(level: ServerLevel, feet: BlockPos): Boolean {
        val floor = feet.below()
        if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) return false
        return (0 until REQUIRED_HEADROOM).all { offset ->
            val pos = feet.above(offset)
            level.getBlockState(pos).isAir && level.getFluidState(pos).isEmpty
        }
    }
}
