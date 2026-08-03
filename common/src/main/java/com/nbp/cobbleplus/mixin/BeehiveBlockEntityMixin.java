package com.nbp.cobbleplus.mixin;

import com.nbp.cobbleplus.feature.impl.PokemonApiaryFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BeehiveBlockEntity.class)
public abstract class BeehiveBlockEntityMixin {
    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void nbp$tickPokemonApiary(Level level, BlockPos pos, BlockState state,
                                              BeehiveBlockEntity hive, CallbackInfo ci) {
        PokemonApiaryFeature.onHiveTick(level, pos, state, hive);
    }
}
