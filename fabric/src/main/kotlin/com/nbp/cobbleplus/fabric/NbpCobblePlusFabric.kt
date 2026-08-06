package com.nbp.cobbleplus.fabric

import com.nbp.cobbleplus.NbpCobblePlus
import com.nbp.cobbleplus.command.NbpCommands
import com.nbp.cobbleplus.feature.impl.AutoAnnouncerFeature
import com.nbp.cobbleplus.feature.impl.CatchComboFeature
import com.nbp.cobbleplus.feature.impl.WelcomeFeature
import com.nbp.cobbleplus.feature.impl.VanillaMobSpawnBlockerFeature
import com.nbp.cobbleplus.feature.impl.LegendarySpawnerFeature
import com.nbp.cobbleplus.feature.impl.RitualBlocksFeature
import com.nbp.cobbleplus.feature.impl.ItemMechanicsFeature
import com.nbp.cobbleplus.feature.impl.PokemonLootModifierFeature
import com.nbp.cobbleplus.feature.impl.CaptureCapFeature
import com.nbp.cobbleplus.feature.impl.ServerEventsFeature
import com.nbp.cobbleplus.feature.impl.PointsFeature
import com.nbp.cobbleplus.feature.impl.MissionsFeature
import com.nbp.cobbleplus.feature.impl.GtsFeature
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.InteractionResult
import com.nbp.cobbleplus.network.CatchComboSyncPayload
import com.nbp.cobbleplus.network.PointsRewardSyncPayload
import com.nbp.cobbleplus.network.PointsViewSyncPayload
import com.nbp.cobbleplus.network.MissionsViewSyncPayload
import com.nbp.cobbleplus.network.GtsViewSyncPayload
import com.nbp.cobbleplus.network.GtsPartyViewPayload
import com.nbp.cobbleplus.network.GtsPurchasePayload
import com.nbp.cobbleplus.network.GtsCollectPayload
import com.nbp.cobbleplus.network.GtsSellPayload
import com.nbp.cobbleplus.network.GtsCancelPayload
import com.nbp.cobbleplus.network.GtsRequestPartyPayload
import com.nbp.cobbleplus.i18n.PlayerLanguage
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import com.nbp.cobbleplus.item.ModItems
import com.nbp.cobbleplus.block.ModBlocks
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.fabricmc.fabric.api.biome.v1.BiomeModifications
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors
import net.minecraft.resources.ResourceKey
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.levelgen.GenerationStep
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.network.chat.Component
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.minecraft.world.level.ItemLike

class NbpCobblePlusFabric : ModInitializer {
    override fun onInitialize() {
        registerBlocks()
        registerItems()
        registerCreativeTab()
        BiomeModifications.addFeature(
            BiomeSelectors.foundInOverworld(),
            GenerationStep.Decoration.UNDERGROUND_ORES,
            ResourceKey.create(Registries.PLACED_FEATURE, ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, "ruby_ore"))
        )
        UseEntityCallback.EVENT.register(UseEntityCallback { player, _, hand, entity, _ ->
            if (player is net.minecraft.server.level.ServerPlayer) ItemMechanicsFeature.interact(player, hand, entity) else InteractionResult.PASS
        })
        UseItemCallback.EVENT.register(UseItemCallback { player, level, hand ->
            val stack = player.getItemInHand(hand)
            if (!level.isClientSide && player is net.minecraft.server.level.ServerPlayer && CaptureCapFeature.useUpgradeItem(player, stack)) {
                InteractionResultHolder.success(stack)
            } else {
                InteractionResultHolder.pass(stack)
            }
        })
        NbpCobblePlus.init()

        PayloadTypeRegistry.playS2C().register(CatchComboSyncPayload.TYPE, CatchComboSyncPayload.CODEC)
        CatchComboFeature.networkSender = { player, lines ->
            ServerPlayNetworking.send(player, CatchComboSyncPayload(lines))
        }
        PayloadTypeRegistry.playS2C().register(PointsRewardSyncPayload.TYPE, PointsRewardSyncPayload.CODEC)
        PointsFeature.networkSender = { player, text, durationTicks ->
            ServerPlayNetworking.send(player, PointsRewardSyncPayload(text, durationTicks))
        }
        PayloadTypeRegistry.playS2C().register(PointsViewSyncPayload.TYPE, PointsViewSyncPayload.CODEC)
        PointsFeature.viewNetworkSender = { player, portuguese, values ->
            ServerPlayNetworking.send(player, PointsViewSyncPayload(portuguese, values))
        }
        PayloadTypeRegistry.playS2C().register(MissionsViewSyncPayload.TYPE, MissionsViewSyncPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(GtsViewSyncPayload.TYPE, GtsViewSyncPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(GtsPartyViewPayload.TYPE, GtsPartyViewPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(GtsPurchasePayload.TYPE, GtsPurchasePayload.CODEC)
        PayloadTypeRegistry.playC2S().register(GtsCollectPayload.TYPE, GtsCollectPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(GtsSellPayload.TYPE, GtsSellPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(GtsCancelPayload.TYPE, GtsCancelPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(GtsRequestPartyPayload.TYPE, GtsRequestPartyPayload.CODEC)
        MissionsFeature.viewNetworkSender = { player, portuguese, daily, weekly ->
            ServerPlayNetworking.send(player, MissionsViewSyncPayload(portuguese, daily, weekly))
        }
        GtsFeature.viewNetworkSender = { player, payload -> ServerPlayNetworking.send(player, payload) }
        GtsFeature.partyViewNetworkSender = { player, payload -> ServerPlayNetworking.send(player, payload) }

        ServerPlayNetworking.registerGlobalReceiver(GtsPurchasePayload.TYPE) { payload, context ->
            context.server().execute {
                GtsFeature.purchase(context.player(), payload.listingId)
                GtsFeature.sendView(context.player())
            }
        }
        ServerPlayNetworking.registerGlobalReceiver(GtsCollectPayload.TYPE) { _, context ->
            context.server().execute {
                GtsFeature.collect(context.player())
                GtsFeature.sendView(context.player())
            }
        }
        ServerPlayNetworking.registerGlobalReceiver(GtsSellPayload.TYPE) { payload, context ->
            context.server().execute {
                GtsFeature.sell(context.player(), payload.partySlot, payload.price)
                GtsFeature.sendView(context.player())
            }
        }
        ServerPlayNetworking.registerGlobalReceiver(GtsCancelPayload.TYPE) { payload, context ->
            context.server().execute {
                GtsFeature.cancel(context.player(), payload.listingId)
                GtsFeature.sendView(context.player())
            }
        }
        ServerPlayNetworking.registerGlobalReceiver(GtsRequestPartyPayload.TYPE) { _, context ->
            context.server().execute {
                GtsFeature.sendPartyView(context.player())
            }
        }

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            NbpCommands.register(dispatcher)
        }

        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            WelcomeFeature.handlePlayerJoin(handler.player)
            GtsFeature.claimPayments(handler.player)
            CatchComboFeature.attachSpawningInfluence(handler.player)
            CatchComboFeature.syncHud(handler.player)
        }

        ServerEntityEvents.ENTITY_LOAD.register { entity, _ ->
            if (VanillaMobSpawnBlockerFeature.shouldBlock(entity)) {
                if (VanillaMobSpawnBlockerFeature.hasConfiguredReplacement(entity)) {
                    VanillaMobSpawnBlockerFeature.spawnReplacement(entity)
                    VanillaMobSpawnBlockerFeature.discardAfterSpawnerRegistration(entity)
                } else {
                    entity.discard()
                }
            }
        }

        ServerTickEvents.END_SERVER_TICK.register { server ->
            VanillaMobSpawnBlockerFeature.flushPendingDiscards()
            VanillaMobSpawnBlockerFeature.tickBossReplacements(server)
            LegendarySpawnerFeature.tick(server)
            RitualBlocksFeature.tick(server)
            ItemMechanicsFeature.tick(server)
            com.nbp.cobbleplus.feature.impl.EconomyFeature.tick(server)
            com.nbp.cobbleplus.feature.impl.SafariZoneFeature.tick(server)
            com.nbp.cobbleplus.feature.impl.WagerBattleFeature.tick(server)
            ServerEventsFeature.tick(server)
            MissionsFeature.tick(server)
        }

        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            AutoAnnouncerFeature.setServer(server)
            CatchComboFeature.bindServer(server)
            LegendarySpawnerFeature.bindServer(server)
            PlayerLanguage.bind(server)
            CaptureCapFeature.bindServer(server)
            PokemonLootModifierFeature.refreshDisplayTables()
            ServerEventsFeature.bindServer(server)
            PointsFeature.bindServer(server)
            MissionsFeature.bindServer(server)
            GtsFeature.bindServer(server)
        }

        ServerLifecycleEvents.SERVER_STOPPING.register { _ ->
            AutoAnnouncerFeature.setServer(null)
            CatchComboFeature.unbindServer()
            LegendarySpawnerFeature.unbindServer()
            PlayerLanguage.unbind()
            CaptureCapFeature.unbindServer()
            ServerEventsFeature.unbindServer()
            PointsFeature.unbindServer()
            MissionsFeature.unbindServer()
            GtsFeature.unbindServer()
        }
    }

    private fun registerItems() {
        fun register(name: String, item: net.minecraft.world.item.Item) {
            Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, name), item)
        }
        register("azure_flute", ModItems.AZURE_FLUTE)
        register("urn_of_thunder", ModItems.URN_OF_THUNDER)
        register("urn_of_freezing", ModItems.URN_OF_FREEZING)
        register("urn_of_burning", ModItems.URN_OF_BURNING)
        register("ruby", ModItems.RUBY)
        register("red_chain", ModItems.RED_CHAIN)
        register("dna_syringe_empty", ModItems.DNA_SYRINGE_EMPTY)
        register("dna_syringe_mew", ModItems.DNA_SYRINGE_MEW)
        register("dna_syringe_mewtwo", ModItems.DNA_SYRINGE_MEWTWO)
        register("capture_permit", ModItems.CAPTURE_PERMIT)
    }

    private fun registerBlocks() {
        fun register(name: String, block: Block) {
            val id = ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, name)
            Registry.register(BuiltInRegistries.BLOCK, id, block)
            Registry.register(BuiltInRegistries.ITEM, id, BlockItem(block, Item.Properties()))
        }
        register("ruby_ore", ModBlocks.RUBY_ORE)
        register("palkia_block", ModBlocks.PALKIA_BLOCK)
        register("used_palkia_block", ModBlocks.USED_PALKIA_BLOCK)
        register("dialga_block", ModBlocks.DIALGA_BLOCK)
        register("used_dialga_block", ModBlocks.USED_DIALGA_BLOCK)
        register("giratina_block", ModBlocks.GIRATINA_BLOCK)
        register("used_giratina_block", ModBlocks.USED_GIRATINA_BLOCK)
        register("arceus_chalice", ModBlocks.ARCEUS_CHALICE)
        register("used_arceus_chalice", ModBlocks.USED_ARCEUS_CHALICE)
    }

    private fun registerCreativeTab() {
        val tab = FabricItemGroup.builder()
            .title(Component.translatable("itemGroup.nbp_cobble_plus"))
            .icon { net.minecraft.world.item.ItemStack(ModItems.AZURE_FLUTE) }
            .displayItems { _, output -> creativeItems().forEach { output.accept(it) } }
            .build()
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, "main"), tab)
    }

    private fun creativeItems(): List<ItemLike> = listOf(
        ModItems.AZURE_FLUTE, ModItems.URN_OF_THUNDER, ModItems.URN_OF_FREEZING, ModItems.URN_OF_BURNING,
        ModItems.RUBY, ModItems.RED_CHAIN, ModItems.DNA_SYRINGE_EMPTY, ModItems.DNA_SYRINGE_MEW, ModItems.DNA_SYRINGE_MEWTWO, ModItems.CAPTURE_PERMIT,
        ModBlocks.RUBY_ORE, ModBlocks.PALKIA_BLOCK, ModBlocks.USED_PALKIA_BLOCK, ModBlocks.DIALGA_BLOCK,
        ModBlocks.USED_DIALGA_BLOCK, ModBlocks.GIRATINA_BLOCK, ModBlocks.USED_GIRATINA_BLOCK,
        ModBlocks.ARCEUS_CHALICE, ModBlocks.USED_ARCEUS_CHALICE
    )
}
