package com.nbp.cobbleplus.mixin;

import com.nbp.cobbleplus.feature.impl.VanillaMobSpawnBlockerFeature;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BaseSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BaseSpawner.class)
abstract class BaseSpawnerMixin {
    @Redirect(method = "serverTick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerLevel;tryAddFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean nbp$markSpawnerEntity(ServerLevel level, Entity entity) {
        VanillaMobSpawnBlockerFeature.markReplacementSource(entity);
        return level.tryAddFreshEntityWithPassengers(entity);
    }
}
