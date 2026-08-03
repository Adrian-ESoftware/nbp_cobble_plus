package com.nbp.cobbleplus.fabric

import com.nbp.cobbleplus.hud.CatchComboHudRenderer
import com.nbp.cobbleplus.hud.CatchComboHudState
import com.nbp.cobbleplus.network.CatchComboSyncPayload
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback

class NbpCobblePlusFabricClient : ClientModInitializer {
    override fun onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(CatchComboSyncPayload.TYPE) { payload, _ ->
            CatchComboHudState.lines = payload.lines
        }

        HudRenderCallback.EVENT.register { guiGraphics, _ ->
            val window = guiGraphics.guiWidth()
            val height = guiGraphics.guiHeight()
            CatchComboHudRenderer.render(guiGraphics, window, height)
        }
    }
}
