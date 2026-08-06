package com.nbp.cobbleplus.mixin;

import com.nbp.cobbleplus.feature.impl.RctRuntimeRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.gitlab.srcmc.rctmod.api.service.TrainerManager")
public abstract class RctTrainerManagerMixin {
    @Inject(method = "getData(Ljava/lang/String;)Lcom/gitlab/srcmc/rctmod/api/data/pack/TrainerMobData;", at = @At("RETURN"), cancellable = true)
    private void nbp$runtimeTrainerData(String id, CallbackInfoReturnable<Object> cir) {
        Object custom = RctRuntimeRegistry.INSTANCE.dataFor(id);
        if (custom != null) cir.setReturnValue(custom);
    }
}
