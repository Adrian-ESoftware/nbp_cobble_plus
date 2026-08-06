package com.nbp.cobbleplus.fabric

import com.nbp.cobbleplus.hud.CatchComboHudRenderer
import com.nbp.cobbleplus.hud.CatchComboHudState
import com.nbp.cobbleplus.hud.PointsRewardHudRenderer
import com.nbp.cobbleplus.hud.PointsRewardHudState
import com.nbp.cobbleplus.hud.PointsScreen
import com.nbp.cobbleplus.hud.MissionsScreen
import com.nbp.cobbleplus.hud.GtsScreen
import com.nbp.cobbleplus.hud.GtsClientNetworking
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
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.Minecraft

class NbpCobblePlusFabricClient : ClientModInitializer {
    override fun onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(CatchComboSyncPayload.TYPE) { payload, _ ->
            CatchComboHudState.lines = payload.lines
        }

        ClientPlayNetworking.registerGlobalReceiver(PointsRewardSyncPayload.TYPE) { payload, _ ->
            PointsRewardHudState.text = payload.text
            PointsRewardHudState.expiresAtMillis = System.currentTimeMillis() + payload.durationTicks * 50L
        }

        ClientPlayNetworking.registerGlobalReceiver(PointsViewSyncPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                Minecraft.getInstance().setScreen(PointsScreen(payload.portuguese, payload.values))
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(MissionsViewSyncPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                Minecraft.getInstance().setScreen(MissionsScreen(payload.portuguese, payload.daily, payload.weekly))
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(GtsViewSyncPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                val current = Minecraft.getInstance().screen
                if (current is GtsScreen) current.update(payload) else Minecraft.getInstance().setScreen(GtsScreen(payload))
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(GtsPartyViewPayload.TYPE) { payload, _ ->
            Minecraft.getInstance().execute {
                val current = Minecraft.getInstance().screen
                if (current is GtsScreen) current.updateParty(payload)
            }
        }

        GtsClientNetworking.purchase = { id -> ClientPlayNetworking.send(GtsPurchasePayload(id)) }
        GtsClientNetworking.collect = { ClientPlayNetworking.send(GtsCollectPayload()) }
        GtsClientNetworking.sell = { slot, price -> ClientPlayNetworking.send(GtsSellPayload(slot, price)) }
        GtsClientNetworking.cancel = { id -> ClientPlayNetworking.send(GtsCancelPayload(id)) }
        GtsClientNetworking.requestParty = { ClientPlayNetworking.send(GtsRequestPartyPayload()) }

        HudRenderCallback.EVENT.register { guiGraphics, _ ->
            val window = guiGraphics.guiWidth()
            val height = guiGraphics.guiHeight()
            CatchComboHudRenderer.render(guiGraphics, window, height)
            PointsRewardHudRenderer.render(guiGraphics, window, height)
        }
    }
}
