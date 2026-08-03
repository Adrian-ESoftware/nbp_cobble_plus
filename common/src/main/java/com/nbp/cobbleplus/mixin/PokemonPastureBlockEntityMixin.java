package com.nbp.cobbleplus.mixin;

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.nbp.cobbleplus.feature.impl.PokemonApiaryFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PokemonPastureBlockEntity.class)
public abstract class PokemonPastureBlockEntityMixin {
    @Inject(method = "checkPokemon", at = @At("TAIL"), remap = false)
    private void nbp$producePastureHoney(CallbackInfo ci) {
        PokemonApiaryFeature.onPastureCheck((PokemonPastureBlockEntity) (Object) this);
    }
}
