package com.nbp.cobbleplus.network

import com.nbp.cobbleplus.NbpCobblePlus
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

data class BuffPurchasePayload(
    val buffId: String,
    val level: Int
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<BuffPurchasePayload> = TYPE

    private fun write(buf: FriendlyByteBuf) {
        buf.writeUtf(buffId)
        buf.writeVarInt(level)
    }

    companion object {
        val TYPE = CustomPacketPayload.Type<BuffPurchasePayload>(
            ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, "buff_purchase")
        )

        val CODEC: StreamCodec<FriendlyByteBuf, BuffPurchasePayload> =
            CustomPacketPayload.codec(BuffPurchasePayload::write) { buf ->
                BuffPurchasePayload(buf.readUtf(), buf.readVarInt())
            }
    }
}
