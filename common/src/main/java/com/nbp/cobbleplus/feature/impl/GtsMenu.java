package com.nbp.cobbleplus.feature.impl;

import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Server-side chest menu for the GTS. The item in each listing slot is the real Cobblemon PokemonItem, so it renders as a 3D model in the vanilla chest screen. */
public final class GtsMenu extends ChestMenu {
    private final List<Long> listingIds;

    public GtsMenu(int containerId, Inventory inventory, List<ItemStack> listingItems, List<Long> listingIds) {
        super(MenuType.GENERIC_9x6, containerId, inventory, new SimpleContainer(54), 6);
        this.listingIds = listingIds;
        for (int i = 0; i < Math.min(45, listingItems.size()); i++) {
            getContainer().setItem(i, listingItems.get(i));
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < listingIds.size() && clickType != ClickType.QUICK_CRAFT) {
            if (GtsFeature.INSTANCE.purchase(player, listingIds.get(slotId)) && player instanceof ServerPlayer serverPlayer) serverPlayer.closeContainer();
            return;
        }
        // Listings are read-only. Prevent taking, placing, cloning, or dropping GUI items.
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
