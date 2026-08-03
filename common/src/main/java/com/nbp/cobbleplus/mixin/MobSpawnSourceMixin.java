package com.nbp.cobbleplus.mixin;

import com.nbp.cobbleplus.feature.impl.VanillaMobSpawnBlockerFeature;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
abstract class MobSpawnSourceMixin {
    @Inject(method = "finalizeSpawn", at = @At("HEAD"))
    private void nbp$markSpawnerSource(ServerLevelAccessor level, DifficultyInstance difficulty,
                                       MobSpawnType spawnType, SpawnGroupData spawnData,
                                       CallbackInfoReturnable<SpawnGroupData> callback) {
        if (spawnType == MobSpawnType.SPAWNER || spawnType == MobSpawnType.TRIAL_SPAWNER) {
            VanillaMobSpawnBlockerFeature.markReplacementSource((Mob) (Object) this);
        }
    }
}
