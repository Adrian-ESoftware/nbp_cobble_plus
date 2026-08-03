package com.nbp.cobbleplus.neoforge

import com.nbp.cobbleplus.NbpCobblePlus
import com.nbp.cobbleplus.command.NbpCommands
import com.nbp.cobbleplus.feature.impl.AutoAnnouncerFeature
import com.nbp.cobbleplus.feature.impl.CatchComboFeature
import com.nbp.cobbleplus.feature.impl.WelcomeFeature
import com.nbp.cobbleplus.hud.CatchComboHudRenderer
import com.nbp.cobbleplus.hud.CatchComboHudState
import com.nbp.cobbleplus.network.CatchComboSyncPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.client.event.RenderGuiEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStartingEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

@Mod(NbpCobblePlus.MOD_ID)
class NbpCobblePlusNeoForge(modEventBus: IEventBus) {
    init {
        NbpCobblePlus.init()

        modEventBus.addListener { event: RegisterPayloadHandlersEvent ->
            val registrar = event.registrar("1")
            registrar.playToClient(CatchComboSyncPayload.TYPE, CatchComboSyncPayload.CODEC) { payload, _ ->
                CatchComboHudState.lines = payload.lines
            }
        }

        CatchComboFeature.networkSender = { player, lines ->
            PacketDistributor.sendToPlayer(player, CatchComboSyncPayload(lines))
        }

        NeoForge.EVENT_BUS.addListener { event: RegisterCommandsEvent ->
            NbpCommands.register(event.dispatcher)
        }

        NeoForge.EVENT_BUS.addListener { event: PlayerEvent.PlayerLoggedInEvent ->
            val player = event.entity
            if (player is ServerPlayer) {
                WelcomeFeature.handlePlayerJoin(player)
                CatchComboFeature.attachSpawningInfluence(player)
                CatchComboFeature.syncHud(player)
            }
        }

        NeoForge.EVENT_BUS.addListener { event: ServerStartingEvent ->
            AutoAnnouncerFeature.setServer(event.server)
        }

        NeoForge.EVENT_BUS.addListener { event: ServerStoppingEvent ->
            AutoAnnouncerFeature.setServer(null)
        }

        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForge.EVENT_BUS.addListener { event: RenderGuiEvent.Post ->
                val guiGraphics = event.guiGraphics
                CatchComboHudRenderer.render(guiGraphics, guiGraphics.guiWidth(), guiGraphics.guiHeight())
            }
        }
    }
}
