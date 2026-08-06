package com.nbp.cobbleplus.neoforge

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
import com.nbp.cobbleplus.feature.impl.RctBattleConditionFeature
import com.nbp.cobbleplus.feature.impl.GtsFeature
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import com.nbp.cobbleplus.hud.CatchComboHudRenderer
import com.nbp.cobbleplus.hud.CatchComboHudState
import com.nbp.cobbleplus.hud.PointsRewardHudRenderer
import com.nbp.cobbleplus.hud.PointsRewardHudState
import com.nbp.cobbleplus.hud.PointsScreen
import com.nbp.cobbleplus.hud.MissionsScreen
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
import com.nbp.cobbleplus.hud.GtsScreen
import com.nbp.cobbleplus.hud.GtsClientNetworking
import com.nbp.cobbleplus.i18n.PlayerLanguage
import net.minecraft.server.level.ServerPlayer
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.neoforge.client.event.RenderGuiEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.server.ServerStartingEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import com.nbp.cobbleplus.item.ModItems
import com.nbp.cobbleplus.block.ModBlocks
import net.minecraft.core.registries.Registries
import net.neoforged.neoforge.registries.DeferredRegister
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.minecraft.network.chat.Component
import net.minecraft.client.Minecraft
import net.minecraft.world.level.ItemLike
import java.util.function.Supplier

@Mod(NbpCobblePlus.MOD_ID)
class NbpCobblePlusNeoForge(modEventBus: IEventBus) {
    init {
        BLOCKS.register(modEventBus)
        ITEMS.register(modEventBus)
        CREATIVE_TABS.register(modEventBus)

        // A inicialização dos módulos precisa ocorrer depois que todos os mods
        // terminaram seus construtores. Fazer isso aqui (no construtor @Mod)
        // pode executar listeners do Cobblemon durante a janela em que o
        // ModLoadingContext ainda está ativo e mudar a ordem de inicialização
        // de mods client-side como Quark/Zeta.
        modEventBus.addListener { _: FMLCommonSetupEvent ->
            NbpCobblePlus.init()
        }

        modEventBus.addListener { event: RegisterPayloadHandlersEvent ->
            val registrar = event.registrar("1")
            registrar.playToClient(CatchComboSyncPayload.TYPE, CatchComboSyncPayload.CODEC) { payload, _ ->
                CatchComboHudState.lines = payload.lines
            }
            registrar.playToClient(PointsRewardSyncPayload.TYPE, PointsRewardSyncPayload.CODEC) { payload, _ ->
                PointsRewardHudState.text = payload.text
                PointsRewardHudState.expiresAtMillis = System.currentTimeMillis() + payload.durationTicks * 50L
            }
            registrar.playToClient(PointsViewSyncPayload.TYPE, PointsViewSyncPayload.CODEC) { payload, _ ->
                Minecraft.getInstance().execute {
                    Minecraft.getInstance().setScreen(PointsScreen(payload.portuguese, payload.values))
                }
            }
            registrar.playToClient(MissionsViewSyncPayload.TYPE, MissionsViewSyncPayload.CODEC) { payload, _ ->
                Minecraft.getInstance().execute {
                    Minecraft.getInstance().setScreen(MissionsScreen(payload.portuguese, payload.daily, payload.weekly))
                }
            }
            registrar.playToClient(GtsViewSyncPayload.TYPE, GtsViewSyncPayload.CODEC) { payload, _ ->
                Minecraft.getInstance().execute {
                    val current = Minecraft.getInstance().screen
                    if (current is GtsScreen) current.update(payload) else Minecraft.getInstance().setScreen(GtsScreen(payload))
                }
            }
            registrar.playToClient(GtsPartyViewPayload.TYPE, GtsPartyViewPayload.CODEC) { payload, _ ->
                Minecraft.getInstance().execute {
                    val current = Minecraft.getInstance().screen
                    if (current is GtsScreen) current.updateParty(payload)
                }
            }
            registrar.playToServer(GtsPurchasePayload.TYPE, GtsPurchasePayload.CODEC) { payload, context ->
                (context.player() as? ServerPlayer)?.let { player ->
                    GtsFeature.purchase(player, payload.listingId)
                    GtsFeature.sendView(player)
                }
            }
            registrar.playToServer(GtsCollectPayload.TYPE, GtsCollectPayload.CODEC) { _, context ->
                (context.player() as? ServerPlayer)?.let { player ->
                    GtsFeature.collect(player)
                    GtsFeature.sendView(player)
                }
            }
            registrar.playToServer(GtsSellPayload.TYPE, GtsSellPayload.CODEC) { payload, context ->
                (context.player() as? ServerPlayer)?.let { player ->
                    GtsFeature.sell(player, payload.partySlot, payload.price)
                    GtsFeature.sendView(player)
                }
            }
            registrar.playToServer(GtsCancelPayload.TYPE, GtsCancelPayload.CODEC) { payload, context ->
                (context.player() as? ServerPlayer)?.let { player ->
                    GtsFeature.cancel(player, payload.listingId)
                    GtsFeature.sendView(player)
                }
            }
            registrar.playToServer(GtsRequestPartyPayload.TYPE, GtsRequestPartyPayload.CODEC) { _, context ->
                (context.player() as? ServerPlayer)?.let { player ->
                    GtsFeature.sendPartyView(player)
                }
            }
        }

        CatchComboFeature.networkSender = { player, lines ->
            PacketDistributor.sendToPlayer(player, CatchComboSyncPayload(lines))
        }
        PointsFeature.networkSender = { player, text, durationTicks ->
            PacketDistributor.sendToPlayer(player, PointsRewardSyncPayload(text, durationTicks))
        }
        PointsFeature.viewNetworkSender = { player, portuguese, values ->
            PacketDistributor.sendToPlayer(player, PointsViewSyncPayload(portuguese, values))
        }
        MissionsFeature.viewNetworkSender = { player, portuguese, daily, weekly ->
            PacketDistributor.sendToPlayer(player, MissionsViewSyncPayload(portuguese, daily, weekly))
        }
        GtsFeature.viewNetworkSender = { player, payload -> PacketDistributor.sendToPlayer(player, payload) }
        GtsFeature.partyViewNetworkSender = { player, payload -> PacketDistributor.sendToPlayer(player, payload) }
        if (FMLEnvironment.dist == Dist.CLIENT) {
            GtsClientNetworking.purchase = { id -> PacketDistributor.sendToServer(GtsPurchasePayload(id)) }
            GtsClientNetworking.collect = { PacketDistributor.sendToServer(GtsCollectPayload()) }
            GtsClientNetworking.sell = { slot, price -> PacketDistributor.sendToServer(GtsSellPayload(slot, price)) }
            GtsClientNetworking.cancel = { id -> PacketDistributor.sendToServer(GtsCancelPayload(id)) }
            GtsClientNetworking.requestParty = { PacketDistributor.sendToServer(GtsRequestPartyPayload()) }
        }

        NeoForge.EVENT_BUS.addListener { event: RegisterCommandsEvent ->
            NbpCommands.register(event.dispatcher)
        }

        NeoForge.EVENT_BUS.addListener { event: PlayerEvent.PlayerLoggedInEvent ->
            val player = event.entity
            if (player is ServerPlayer) {
                WelcomeFeature.handlePlayerJoin(player)
                GtsFeature.claimPayments(player)
                CatchComboFeature.attachSpawningInfluence(player)
                CatchComboFeature.syncHud(player)
            }
        }

        NeoForge.EVENT_BUS.addListener { event: EntityJoinLevelEvent ->
            if (VanillaMobSpawnBlockerFeature.shouldBlock(event.entity)) {
                if (VanillaMobSpawnBlockerFeature.hasConfiguredReplacement(event.entity)) {
                    VanillaMobSpawnBlockerFeature.spawnReplacement(event.entity)
                    VanillaMobSpawnBlockerFeature.discardAfterSpawnerRegistration(event.entity)
                } else {
                    event.isCanceled = true
                }
            }
        }

        NeoForge.EVENT_BUS.addListener { event: ServerTickEvent.Post ->
            VanillaMobSpawnBlockerFeature.flushPendingDiscards()
            VanillaMobSpawnBlockerFeature.tickBossReplacements(event.server)
            LegendarySpawnerFeature.tick(event.server)
            RitualBlocksFeature.tick(event.server)
            ItemMechanicsFeature.tick(event.server)
            com.nbp.cobbleplus.feature.impl.EconomyFeature.tick(event.server)
            com.nbp.cobbleplus.feature.impl.SafariZoneFeature.tick(event.server)
            com.nbp.cobbleplus.feature.impl.WagerBattleFeature.tick(event.server)
            ServerEventsFeature.tick(event.server)
            MissionsFeature.tick(event.server)
            RctBattleConditionFeature.tick(event.server)
        }

        NeoForge.EVENT_BUS.addListener { event: PlayerInteractEvent.EntityInteract ->
            val player = event.entity
            if (player is ServerPlayer) {
                val result = ItemMechanicsFeature.interact(player, event.hand, event.target)
                if (result.consumesAction()) { event.cancellationResult = result; event.isCanceled = true }
            }
        }

        NeoForge.EVENT_BUS.addListener { event: PlayerInteractEvent.RightClickItem ->
            val player = event.entity
            if (player is ServerPlayer && CaptureCapFeature.useUpgradeItem(player, event.itemStack)) {
                event.cancellationResult = net.minecraft.world.InteractionResult.SUCCESS
                event.isCanceled = true
            }
        }

        NeoForge.EVENT_BUS.addListener { event: ServerStartingEvent ->
            AutoAnnouncerFeature.setServer(event.server)
            CatchComboFeature.bindServer(event.server)
            LegendarySpawnerFeature.bindServer(event.server)
            PlayerLanguage.bind(event.server)
            CaptureCapFeature.bindServer(event.server)
            PokemonLootModifierFeature.refreshDisplayTables()
            ServerEventsFeature.bindServer(event.server)
                PointsFeature.bindServer(event.server)
                MissionsFeature.bindServer(event.server)
                GtsFeature.bindServer(event.server)
        }

        NeoForge.EVENT_BUS.addListener { event: ServerStoppingEvent ->
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

        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForge.EVENT_BUS.addListener { event: RenderGuiEvent.Post ->
                val guiGraphics = event.guiGraphics
                CatchComboHudRenderer.render(guiGraphics, guiGraphics.guiWidth(), guiGraphics.guiHeight())
                PointsRewardHudRenderer.render(guiGraphics, guiGraphics.guiWidth(), guiGraphics.guiHeight())
            }
        }
    }

    companion object {
        private val BLOCKS = DeferredRegister.create(Registries.BLOCK, NbpCobblePlus.MOD_ID).apply {
            register("ruby_ore", Supplier { ModBlocks.RUBY_ORE })
            register("palkia_block", Supplier { ModBlocks.PALKIA_BLOCK })
            register("used_palkia_block", Supplier { ModBlocks.USED_PALKIA_BLOCK })
            register("dialga_block", Supplier { ModBlocks.DIALGA_BLOCK })
            register("used_dialga_block", Supplier { ModBlocks.USED_DIALGA_BLOCK })
            register("giratina_block", Supplier { ModBlocks.GIRATINA_BLOCK })
            register("used_giratina_block", Supplier { ModBlocks.USED_GIRATINA_BLOCK })
            register("arceus_chalice", Supplier { ModBlocks.ARCEUS_CHALICE })
            register("used_arceus_chalice", Supplier { ModBlocks.USED_ARCEUS_CHALICE })
        }

        private val ITEMS = DeferredRegister.create(Registries.ITEM, NbpCobblePlus.MOD_ID).apply {
            register("azure_flute", Supplier { ModItems.AZURE_FLUTE })
            register("urn_of_thunder", Supplier { ModItems.URN_OF_THUNDER })
            register("urn_of_freezing", Supplier { ModItems.URN_OF_FREEZING })
            register("urn_of_burning", Supplier { ModItems.URN_OF_BURNING })
            register("ruby", Supplier { ModItems.RUBY })
            register("red_chain", Supplier { ModItems.RED_CHAIN })
            register("dna_syringe_empty", Supplier { ModItems.DNA_SYRINGE_EMPTY })
            register("dna_syringe_mew", Supplier { ModItems.DNA_SYRINGE_MEW })
            register("dna_syringe_mewtwo", Supplier { ModItems.DNA_SYRINGE_MEWTWO })
            register("capture_permit", Supplier { ModItems.CAPTURE_PERMIT })
            register("ruby_ore", Supplier { BlockItem(ModBlocks.RUBY_ORE, Item.Properties()) })
            register("palkia_block", Supplier { BlockItem(ModBlocks.PALKIA_BLOCK, Item.Properties()) })
            register("used_palkia_block", Supplier { BlockItem(ModBlocks.USED_PALKIA_BLOCK, Item.Properties()) })
            register("dialga_block", Supplier { BlockItem(ModBlocks.DIALGA_BLOCK, Item.Properties()) })
            register("used_dialga_block", Supplier { BlockItem(ModBlocks.USED_DIALGA_BLOCK, Item.Properties()) })
            register("giratina_block", Supplier { BlockItem(ModBlocks.GIRATINA_BLOCK, Item.Properties()) })
            register("used_giratina_block", Supplier { BlockItem(ModBlocks.USED_GIRATINA_BLOCK, Item.Properties()) })
            register("arceus_chalice", Supplier { BlockItem(ModBlocks.ARCEUS_CHALICE, Item.Properties()) })
            register("used_arceus_chalice", Supplier { BlockItem(ModBlocks.USED_ARCEUS_CHALICE, Item.Properties()) })
        }

        private val CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, NbpCobblePlus.MOD_ID).apply {
            register("main", Supplier {
                CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.nbp_cobble_plus"))
                    .icon { ItemStack(ModItems.AZURE_FLUTE) }
                    .displayItems { _, output ->
                        listOf<ItemLike>(
                            ModItems.AZURE_FLUTE, ModItems.URN_OF_THUNDER, ModItems.URN_OF_FREEZING, ModItems.URN_OF_BURNING,
                            ModItems.RUBY, ModItems.RED_CHAIN, ModItems.DNA_SYRINGE_EMPTY, ModItems.DNA_SYRINGE_MEW, ModItems.DNA_SYRINGE_MEWTWO, ModItems.CAPTURE_PERMIT,
                            ModBlocks.RUBY_ORE, ModBlocks.PALKIA_BLOCK, ModBlocks.USED_PALKIA_BLOCK, ModBlocks.DIALGA_BLOCK,
                            ModBlocks.USED_DIALGA_BLOCK, ModBlocks.GIRATINA_BLOCK, ModBlocks.USED_GIRATINA_BLOCK,
                            ModBlocks.ARCEUS_CHALICE, ModBlocks.USED_ARCEUS_CHALICE
                        ).forEach { output.accept(it) }
                    }.build()
            })
        }
    }
}
