package com.nbp.cobbleplus.network

import com.nbp.cobbleplus.NbpCobblePlus
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

/**
 * Pacote S2C com uma linha de recompensa de pontos (texto já formatado com códigos `§`)
 * e por quantos ticks o cliente deve mantê-la visível antes de sumir sozinha.
 */
data class PointsRewardSyncPayload(val text: String, val durationTicks: Int) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<PointsRewardSyncPayload> = TYPE

    private fun write(buf: FriendlyByteBuf) {
        buf.writeUtf(text)
        buf.writeVarInt(durationTicks)
    }

    companion object {
        val TYPE: CustomPacketPayload.Type<PointsRewardSyncPayload> = CustomPacketPayload.Type(
            ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, "points_reward_sync")
        )

        val CODEC: StreamCodec<FriendlyByteBuf, PointsRewardSyncPayload> =
            CustomPacketPayload.codec(PointsRewardSyncPayload::write, ::decode)

        private fun decode(buf: FriendlyByteBuf): PointsRewardSyncPayload =
            PointsRewardSyncPayload(buf.readUtf(), buf.readVarInt())
    }
}
