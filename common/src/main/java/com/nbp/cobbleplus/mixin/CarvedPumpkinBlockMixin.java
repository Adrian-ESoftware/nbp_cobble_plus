package com.nbp.cobbleplus.mixin;

import com.nbp.cobbleplus.feature.impl.VanillaMobSpawnBlockerFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CarvedPumpkinBlock.class)
abstract class CarvedPumpkinBlockMixin {
    @Inject(method = "spawnGolemInWorld", at = @At("HEAD"))
    private static void nbp$markConstructedGolem(Level level, BlockPattern.BlockPatternMatch match,
                                                 Entity entity, BlockPos pos, CallbackInfo callback) {
        VanillaMobSpawnBlockerFeature.markReplacementSource(entity);
    }
}
