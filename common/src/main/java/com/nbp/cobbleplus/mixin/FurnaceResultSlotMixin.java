package com.nbp.cobbleplus.mixin;

import com.nbp.cobbleplus.feature.impl.MeltanFurnaceFeature;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FurnaceResultSlot.class)
abstract class FurnaceResultSlotMixin {
    @Inject(method = "onTake", at = @At("HEAD"))
    private void nbp$handleSmeltedIron(Player player, ItemStack stack, CallbackInfo callback) {
        if (player instanceof ServerPlayer serverPlayer) {
            MeltanFurnaceFeature.handleResultTaken(serverPlayer, stack);
        }
    }
}
