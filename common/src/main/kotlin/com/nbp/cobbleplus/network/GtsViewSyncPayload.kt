package com.nbp.cobbleplus.network

import com.nbp.cobbleplus.NbpCobblePlus
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

data class GtsViewRow(
    val id: Long,
    val species: String,
    val shiny: Boolean,
    val seller: String,
    val price: Long,
    val level: Int,
    val nature: String,
    val ability: String,
    val ivPct: Float,
    val evPct: Float,
    val gender: String,
    val form: String,
    val ivHp: Int,
    val ivAtk: Int,
    val ivDef: Int,
    val ivSpAtk: Int,
    val ivSpDef: Int,
    val ivSpd: Int,
    val evHp: Int,
    val evAtk: Int,
    val evDef: Int,
    val evSpAtk: Int,
    val evSpDef: Int,
    val evSpd: Int
)

class GtsViewSyncPayload(
    val rows: List<GtsViewRow>,
    val balance: String,
    val pending: Long
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<GtsViewSyncPayload> = TYPE

    private fun write(buf: FriendlyByteBuf) {
        buf.writeVarInt(rows.size)
        rows.forEach { r ->
            buf.writeVarLong(r.id)
            buf.writeUtf(r.species)
            buf.writeBoolean(r.shiny)
            buf.writeUtf(r.seller)
            buf.writeVarLong(r.price)
            buf.writeVarInt(r.level)
            buf.writeUtf(r.nature)
            buf.writeUtf(r.ability)
            buf.writeFloat(r.ivPct)
            buf.writeFloat(r.evPct)
            buf.writeUtf(r.gender)
            buf.writeUtf(r.form)
            buf.writeVarInt(r.ivHp); buf.writeVarInt(r.ivAtk); buf.writeVarInt(r.ivDef)
            buf.writeVarInt(r.ivSpAtk); buf.writeVarInt(r.ivSpDef); buf.writeVarInt(r.ivSpd)
            buf.writeVarInt(r.evHp); buf.writeVarInt(r.evAtk); buf.writeVarInt(r.evDef)
            buf.writeVarInt(r.evSpAtk); buf.writeVarInt(r.evSpDef); buf.writeVarInt(r.evSpd)
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
                    GtsViewRow(
                        buf.readVarLong(), buf.readUtf(), buf.readBoolean(), buf.readUtf(), buf.readVarLong(),
                        buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readFloat(), buf.readFloat(),
                        buf.readUtf(), buf.readUtf(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt()
                    )
                }
                GtsViewSyncPayload(rows, buf.readUtf(), buf.readVarLong())
            }
    }
}

data class GtsPartyRow(
    val slot: Int,
    val species: String,
    val level: Int,
    val shiny: Boolean,
    val nature: String,
    val ability: String,
    val ivPct: Float,
    val evPct: Float,
    val gender: String,
    val form: String,
    val ivHp: Int,
    val ivAtk: Int,
    val ivDef: Int,
    val ivSpAtk: Int,
    val ivSpDef: Int,
    val ivSpd: Int,
    val evHp: Int,
    val evAtk: Int,
    val evDef: Int,
    val evSpAtk: Int,
    val evSpDef: Int,
    val evSpd: Int
)

class GtsPartyViewPayload(
    val party: List<GtsPartyRow>
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<GtsPartyViewPayload> = TYPE

    private fun write(buf: FriendlyByteBuf) {
        buf.writeVarInt(party.size)
        party.forEach { p ->
            buf.writeVarInt(p.slot)
            buf.writeUtf(p.species)
            buf.writeVarInt(p.level)
            buf.writeBoolean(p.shiny)
            buf.writeUtf(p.nature)
            buf.writeUtf(p.ability)
            buf.writeFloat(p.ivPct)
            buf.writeFloat(p.evPct)
            buf.writeUtf(p.gender)
            buf.writeUtf(p.form)
            buf.writeVarInt(p.ivHp); buf.writeVarInt(p.ivAtk); buf.writeVarInt(p.ivDef)
            buf.writeVarInt(p.ivSpAtk); buf.writeVarInt(p.ivSpDef); buf.writeVarInt(p.ivSpd)
            buf.writeVarInt(p.evHp); buf.writeVarInt(p.evAtk); buf.writeVarInt(p.evDef)
            buf.writeVarInt(p.evSpAtk); buf.writeVarInt(p.evSpDef); buf.writeVarInt(p.evSpd)
        }
    }

    companion object {
        val TYPE = CustomPacketPayload.Type<GtsPartyViewPayload>(
            ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, "gts_party_view")
        )
        val CODEC: StreamCodec<FriendlyByteBuf, GtsPartyViewPayload> =
            CustomPacketPayload.codec(GtsPartyViewPayload::write) { buf ->
                val party = List(buf.readVarInt()) {
                    GtsPartyRow(
                        buf.readVarInt(), buf.readUtf(), buf.readVarInt(), buf.readBoolean(),
                        buf.readUtf(), buf.readUtf(), buf.readFloat(), buf.readFloat(),
                        buf.readUtf(), buf.readUtf(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt()
                    )
                }
                GtsPartyViewPayload(party)
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

data class GtsSellPayload(val partySlot: Int, val price: Long) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<GtsSellPayload> = TYPE
    private fun write(buf: FriendlyByteBuf) {
        buf.writeVarInt(partySlot)
        buf.writeVarLong(price)
    }
    companion object {
        val TYPE = CustomPacketPayload.Type<GtsSellPayload>(ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, "gts_sell"))
        val CODEC: StreamCodec<FriendlyByteBuf, GtsSellPayload> =
            CustomPacketPayload.codec(GtsSellPayload::write) { GtsSellPayload(it.readVarInt(), it.readVarLong()) }
    }
}

data class GtsCancelPayload(val listingId: Long) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<GtsCancelPayload> = TYPE
    private fun write(buf: FriendlyByteBuf) { buf.writeVarLong(listingId) }
    companion object {
        val TYPE = CustomPacketPayload.Type<GtsCancelPayload>(ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, "gts_cancel"))
        val CODEC: StreamCodec<FriendlyByteBuf, GtsCancelPayload> =
            CustomPacketPayload.codec(GtsCancelPayload::write) { GtsCancelPayload(it.readVarLong()) }
    }
}

class GtsRequestPartyPayload : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<GtsRequestPartyPayload> = TYPE
    private fun write(buf: FriendlyByteBuf) = Unit
    companion object {
        val TYPE = CustomPacketPayload.Type<GtsRequestPartyPayload>(ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, "gts_request_party"))
        val CODEC: StreamCodec<FriendlyByteBuf, GtsRequestPartyPayload> =
            CustomPacketPayload.codec(GtsRequestPartyPayload::write) { GtsRequestPartyPayload() }
    }
}
