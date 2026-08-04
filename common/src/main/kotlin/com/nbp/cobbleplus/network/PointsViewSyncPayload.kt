package com.nbp.cobbleplus.network

import com.nbp.cobbleplus.NbpCobblePlus
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

/**
 * Pacote S2C com o extrato completo de pontos pra abrir a tela [com.nbp.cobbleplus.hud.PointsScreen].
 * O servidor já resolve o idioma do jogador; `values` está na mesma ordem de `PointType.entries`.
 */
class PointsViewSyncPayload(val portuguese: Boolean, val values: LongArray) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<PointsViewSyncPayload> = TYPE

    private fun write(buf: FriendlyByteBuf) {
        buf.writeBoolean(portuguese)
        buf.writeVarInt(values.size)
        values.forEach { buf.writeLong(it) }
    }

    companion object {
        val TYPE: CustomPacketPayload.Type<PointsViewSyncPayload> = CustomPacketPayload.Type(
            ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, "points_view_sync")
        )

        val CODEC: StreamCodec<FriendlyByteBuf, PointsViewSyncPayload> =
            CustomPacketPayload.codec(PointsViewSyncPayload::write, ::decode)

        private fun decode(buf: FriendlyByteBuf): PointsViewSyncPayload {
            val portuguese = buf.readBoolean()
            val size = buf.readVarInt()
            val values = LongArray(size) { buf.readLong() }
            return PointsViewSyncPayload(portuguese, values)
        }
    }
}
