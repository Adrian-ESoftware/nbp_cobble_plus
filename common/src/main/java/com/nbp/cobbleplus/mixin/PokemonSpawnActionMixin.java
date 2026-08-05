package com.nbp.cobbleplus.mixin;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnAction;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.nbp.cobbleplus.feature.impl.SynchronizedNaturesFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PokemonSpawnAction.class, remap = false)
public abstract class PokemonSpawnActionMixin {
    @Shadow private PokemonProperties props;

    @Inject(method = "createEntity()Lcom/cobblemon/mod/common/entity/pokemon/PokemonEntity;", at = @At("HEAD"))
    private void nbp$applySynchronizedNature(CallbackInfoReturnable<PokemonEntity> cir) {
        SynchronizedNaturesFeature.INSTANCE.possiblyChangeNature(
                ((PokemonSpawnAction) (Object) this).getSpawnablePosition(), props);
    }
}
