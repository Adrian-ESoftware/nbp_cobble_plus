package com.nbp.cobbleplus.mixin;

import com.nbp.cobbleplus.feature.impl.VanillaMobSpawnBlockerFeature;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.InfestedBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(InfestedBlock.class)
abstract class InfestedBlockMixin {
    @Redirect(method = "spawnInfestation", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean nbp$markInfestedSpawn(ServerLevel level, Entity entity) {
        VanillaMobSpawnBlockerFeature.markReplacementSource(entity);
        return level.addFreshEntity(entity);
    }
}
