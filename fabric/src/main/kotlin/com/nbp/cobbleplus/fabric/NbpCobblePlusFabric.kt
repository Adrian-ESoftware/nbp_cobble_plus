package com.nbp.cobbleplus.fabric

import com.nbp.cobbleplus.NbpCobblePlus
import com.nbp.cobbleplus.command.NbpCommands
import com.nbp.cobbleplus.feature.impl.AutoAnnouncerFeature
import com.nbp.cobbleplus.feature.impl.CatchComboFeature
import com.nbp.cobbleplus.feature.impl.WelcomeFeature
import com.nbp.cobbleplus.network.CatchComboSyncPayload
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking

class NbpCobblePlusFabric : ModInitializer {
    override fun onInitialize() {
        NbpCobblePlus.init()

        PayloadTypeRegistry.playS2C().register(CatchComboSyncPayload.TYPE, CatchComboSyncPayload.CODEC)
        CatchComboFeature.networkSender = { player, lines ->
            ServerPlayNetworking.send(player, CatchComboSyncPayload(lines))
        }

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            NbpCommands.register(dispatcher)
        }

        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            WelcomeFeature.handlePlayerJoin(handler.player)
            CatchComboFeature.attachSpawningInfluence(handler.player)
            CatchComboFeature.syncHud(handler.player)
        }

        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            AutoAnnouncerFeature.setServer(server)
        }

        ServerLifecycleEvents.SERVER_STOPPING.register { _ ->
            AutoAnnouncerFeature.setServer(null)
        }
    }
}
