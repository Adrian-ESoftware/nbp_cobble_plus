package com.nbp.cobbleplus.network

import com.nbp.cobbleplus.NbpCobblePlus
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

data class GtsViewRow(val id: Long, val species: String, val shiny: Boolean, val seller: String, val price: Long)

class GtsViewSyncPayload(
    val rows: List<GtsViewRow>,
    val balance: String,
    val pending: Long
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<GtsViewSyncPayload> = TYPE

    private fun write(buf: FriendlyByteBuf) {
        buf.writeVarInt(rows.size)
        rows.forEach {
            buf.writeVarLong(it.id)
            buf.writeUtf(it.species)
            buf.writeBoolean(it.shiny)
            buf.writeUtf(it.seller)
            buf.writeVarLong(it.price)
        }
        buf.writeUtf(balance)
        buf.writeVarLong(pending)
    }

    companion object {
        val TYPE = CustomPacketPayload.Type<GtsViewSyncPayload>(
            ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, "gts_view_sync")
        )
        val CODEC: StreamCodec<FriendlyByteBuf, GtsViewSyncPayload> =
            CustomPacketPayload.codec(GtsViewSyncPayload::write) { buf ->
                val rows = List(buf.readVarInt()) {
                    GtsViewRow(buf.readVarLong(), buf.readUtf(), buf.readBoolean(), buf.readUtf(), buf.readVarLong())
                }
                GtsViewSyncPayload(rows, buf.readUtf(), buf.readVarLong())
            }
    }
}

data class GtsPurchasePayload(val listingId: Long) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<GtsPurchasePayload> = TYPE
    private fun write(buf: FriendlyByteBuf) { buf.writeVarLong(listingId) }
    companion object {
        val TYPE = CustomPacketPayload.Type<GtsPurchasePayload>(ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, "gts_purchase"))
        val CODEC: StreamCodec<FriendlyByteBuf, GtsPurchasePayload> = CustomPacketPayload.codec(GtsPurchasePayload::write) { GtsPurchasePayload(it.readVarLong()) }
    }
}

class GtsCollectPayload : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<GtsCollectPayload> = TYPE
    private fun write(buf: FriendlyByteBuf) = Unit
    companion object {
        val TYPE = CustomPacketPayload.Type<GtsCollectPayload>(ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, "gts_collect"))
        val CODEC: StreamCodec<FriendlyByteBuf, GtsCollectPayload> =
            CustomPacketPayload.codec(GtsCollectPayload::write) { GtsCollectPayload() }
    }
}
