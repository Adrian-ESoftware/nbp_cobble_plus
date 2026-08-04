package com.nbp.cobbleplus.fabric

import com.nbp.cobbleplus.hud.CatchComboHudRenderer
import com.nbp.cobbleplus.hud.CatchComboHudState
import com.nbp.cobbleplus.hud.PointsRewardHudRenderer
import com.nbp.cobbleplus.hud.PointsRewardHudState
import com.nbp.cobbleplus.network.CatchComboSyncPayload
import com.nbp.cobbleplus.network.PointsRewardSyncPayload
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback

class NbpCobblePlusFabricClient : ClientModInitializer {
    override fun onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(CatchComboSyncPayload.TYPE) { payload, _ ->
            CatchComboHudState.lines = payload.lines
        }

        ClientPlayNetworking.registerGlobalReceiver(PointsRewardSyncPayload.TYPE) { payload, _ ->
            PointsRewardHudState.text = payload.text
            PointsRewardHudState.expiresAtMillis = System.currentTimeMillis() + payload.durationTicks * 50L
        }

        HudRenderCallback.EVENT.register { guiGraphics, _ ->
            val window = guiGraphics.guiWidth()
            val height = guiGraphics.guiHeight()
            CatchComboHudRenderer.render(guiGraphics, window, height)
            PointsRewardHudRenderer.render(guiGraphics, window, height)
        }
    }
}
