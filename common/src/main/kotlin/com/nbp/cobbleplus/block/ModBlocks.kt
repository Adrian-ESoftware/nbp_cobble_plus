package com.nbp.cobbleplus.block

import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.InteractionResult
import com.nbp.cobbleplus.feature.impl.RitualBlocksFeature
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import net.minecraft.network.chat.Component

object ModBlocks {
    val RUBY_ORE = Block(
        BlockBehaviour.Properties.of()
            .strength(3.0f, 3.0f)
            .sound(SoundType.STONE)
            .requiresCorrectToolForDrops()
    )

    val PALKIA_BLOCK = activeLegendBlock("palkia", "mega_showdown:lustrous_orb")
    val USED_PALKIA_BLOCK = usedLegendBlock("Palkia")
    val DIALGA_BLOCK = activeLegendBlock("dialga", "mega_showdown:adamant_orb")
    val USED_DIALGA_BLOCK = usedLegendBlock("Dialga")
    val GIRATINA_BLOCK = activeLegendBlock("giratina", "mega_showdown:griseous_orb")
    val USED_GIRATINA_BLOCK = usedLegendBlock("Giratina")

    val ARCEUS_CHALICE = ArceusChaliceBlock(
        BlockBehaviour.Properties.of()
            .strength(-1.0f, 3_600_000.0f)
            .sound(SoundType.STONE)
            .lightLevel { 10 }
            .noOcclusion()
    )

    val USED_ARCEUS_CHALICE = UsedArceusChaliceBlock(
        BlockBehaviour.Properties.of()
            .strength(50.0f, 1_200.0f)
            .sound(SoundType.STONE)
            .requiresCorrectToolForDrops()
            .noOcclusion(),
        "message.nbp_cobble_plus.chalice.empty"
    )

    private fun activeLegendBlock(species: String, orb: String) = CreationTrioBlock(
        BlockBehaviour.Properties.of()
            .strength(-1.0f, 3_600_000.0f)
            .sound(SoundType.METAL)
            .lightLevel { 7 }
    , RitualBlocksFeature.TrioConfig(species, orb))

    private fun usedLegendBlock(label: String) = UsedRitualBlock(
        BlockBehaviour.Properties.of()
            .strength(50.0f, 1_200.0f)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops(),
        "message.nbp_cobble_plus.trio.used",
        label
    )
}

class ArceusChaliceBlock(properties: BlockBehaviour.Properties) : Block(properties) {
    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape = ChaliceShape.VALUE

    override fun useItemOn(stack: ItemStack, state: BlockState, level: Level, pos: BlockPos, player: Player, hand: InteractionHand, hit: BlockHitResult): ItemInteractionResult {
        if (hand != InteractionHand.MAIN_HAND) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
        if (level.isClientSide) return ItemInteractionResult.SUCCESS
        return if (RitualBlocksFeature.useChalice(level as net.minecraft.server.level.ServerLevel, pos, player, stack)) ItemInteractionResult.SUCCESS else ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
    }

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult {
        if (!level.isClientSide) RitualBlocksFeature.useChalice(level as net.minecraft.server.level.ServerLevel, pos, player, ItemStack.EMPTY)
        return InteractionResult.sidedSuccess(level.isClientSide)
    }
}

class CreationTrioBlock(properties: BlockBehaviour.Properties, private val config: RitualBlocksFeature.TrioConfig) : Block(properties) {
    override fun useItemOn(stack: ItemStack, state: BlockState, level: Level, pos: BlockPos, player: Player, hand: InteractionHand, hit: BlockHitResult): ItemInteractionResult {
        if (hand != InteractionHand.MAIN_HAND) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
        if (level.isClientSide) return ItemInteractionResult.SUCCESS
        return if (RitualBlocksFeature.useTrio(level as net.minecraft.server.level.ServerLevel, pos, player, stack, config)) ItemInteractionResult.SUCCESS else ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
    }
}

open class UsedRitualBlock(properties: BlockBehaviour.Properties, private val messageKey: String, private vararg val args: Any) : Block(properties) {
    override fun useItemOn(stack: ItemStack, state: BlockState, level: Level, pos: BlockPos, player: Player, hand: InteractionHand, hit: BlockHitResult): ItemInteractionResult {
        if (hand == InteractionHand.MAIN_HAND && !level.isClientSide) player.displayClientMessage(Component.translatable(messageKey, *args), true)
        return ItemInteractionResult.sidedSuccess(level.isClientSide)
    }

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult {
        if (!level.isClientSide) player.displayClientMessage(Component.translatable(messageKey, *args), true)
        return InteractionResult.sidedSuccess(level.isClientSide)
    }
}

class UsedArceusChaliceBlock(properties: BlockBehaviour.Properties, messageKey: String) : UsedRitualBlock(properties, messageKey) {
    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = ChaliceShape.VALUE
}

private object ChaliceShape {
    private val boxes = arrayOf(
        doubleArrayOf(.3125,0.0,.3125,.6875,.09375,.6875), doubleArrayOf(.625,0.0,.28125,.71875,.0625,.375),
        doubleArrayOf(.28125,0.0,.28125,.375,.0625,.375), doubleArrayOf(.640625,.0625,.296875,.703125,.09375,.359375),
        doubleArrayOf(.28125,0.0,.625,.375,.0625,.71875), doubleArrayOf(.625,0.0,.625,.71875,.0625,.71875),
        doubleArrayOf(.640625,.0625,.640625,.703125,.09375,.703125), doubleArrayOf(.296875,.0625,.296875,.359375,.09375,.359375),
        doubleArrayOf(.296875,.0625,.640625,.359375,.09375,.703125), doubleArrayOf(.4375,.125,.4375,.5625,.3125,.5625),
        doubleArrayOf(.375,.0625,.375,.625,.125,.625), doubleArrayOf(.3125,.3125,.3125,.6875,.375,.6875),
        doubleArrayOf(.6875,.375,.3125,.75,.6875,.6875), doubleArrayOf(.25,.375,.3125,.3125,.6875,.6875),
        doubleArrayOf(.3125,.375,.25,.6875,.6875,.3125), doubleArrayOf(.3125,.375,.6875,.6875,.6875,.75),
        doubleArrayOf(.25,.4375,.6875,.3125,.8125,.75), doubleArrayOf(.25,.4375,.25,.3125,.8125,.3125),
        doubleArrayOf(.25,.8125,.25,.28125,.875,.28125), doubleArrayOf(.234375,.625,.671875,.328125,.75,.765625),
        doubleArrayOf(.6875,.4375,.6875,.75,.8125,.75), doubleArrayOf(.671875,.625,.671875,.765625,.75,.765625),
        doubleArrayOf(.234375,.625,.234375,.328125,.75,.328125), doubleArrayOf(.671875,.625,.234375,.765625,.75,.328125),
        doubleArrayOf(.6875,.4375,.25,.75,.8125,.3125), doubleArrayOf(.71875,.8125,.25,.75,.875,.28125),
        doubleArrayOf(.25,.8125,.71875,.28125,.875,.75), doubleArrayOf(.71875,.8125,.71875,.75,.875,.75),
        doubleArrayOf(.375,.25,.375,.625,.3125,.625)
    )
    val VALUE: VoxelShape = boxes.map { b -> Block.box(b[0]*16,b[1]*16,b[2]*16,b[3]*16,b[4]*16,b[5]*16) }
        .reduce(Shapes::or)
}
