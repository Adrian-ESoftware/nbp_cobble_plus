package com.nbp.cobbleplus.item

import com.nbp.cobbleplus.feature.impl.ItemMechanicsFeature
import com.nbp.cobbleplus.feature.impl.CaptureCapFeature
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

object ModItems {
    val AZURE_FLUTE = SummoningItem("arceus", 4)
    val URN_OF_THUNDER = UrnItem("zapdos")
    val URN_OF_FREEZING = UrnItem("articuno")
    val URN_OF_BURNING = UrnItem("moltres")
    val RUBY = RubyItem()
    val RED_CHAIN = Item(Item.Properties().stacksTo(1))
    val DNA_SYRINGE_EMPTY = Item(Item.Properties().stacksTo(16))
    val DNA_SYRINGE_MEW = Item(Item.Properties().stacksTo(16))
    val DNA_SYRINGE_MEWTWO = Item(Item.Properties().stacksTo(16))
    val CAPTURE_PERMIT = CapturePermitItem()
    val TRAINER_EDITOR = TrainerEditorItem()
}

class TrainerEditorItem : Item(Properties().stacksTo(1)) {
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)
        if (!level.isClientSide && player is ServerPlayer) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§bEditor de Treinador: interface em preparação."))
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
    }
}

class CapturePermitItem : Item(Properties().stacksTo(16)) {
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)
        if (!level.isClientSide && player is ServerPlayer) CaptureCapFeature.useUpgradeItem(player, stack)
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
    }
}

class SummoningItem(private val species: String, private val perfectIvs: Int) : Item(Properties().stacksTo(1)) {
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)
        if (!level.isClientSide) ItemMechanicsFeature.startSummon(player, stack, species, perfectIvs)
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
    }
}

class UrnItem(val species: String) : Item(Properties().stacksTo(1)) {
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)
        if (!level.isClientSide) ItemMechanicsFeature.useUrn(player, stack, species)
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
    }
}

class RubyItem : Item(Properties()) {
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)
        if (!level.isClientSide) ItemMechanicsFeature.tryAssembleRedChain(player, stack)
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
    }
}
