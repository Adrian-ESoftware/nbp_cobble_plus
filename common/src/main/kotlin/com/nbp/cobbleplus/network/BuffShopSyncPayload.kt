package com.nbp.cobbleplus.network

import com.nbp.cobbleplus.NbpCobblePlus
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

data class ActiveBuffSync(
    val buffId: String,
    val level: Int,
    val remainingSeconds: Int
)

class BuffShopSyncPayload(
    val portuguese: Boolean,
    val balances: Map<String, Long>,
    val activeBuffs: List<ActiveBuffSync>
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<BuffShopSyncPayload> = TYPE

    private fun write(buf: FriendlyByteBuf) {
        buf.writeBoolean(portuguese)
        buf.writeVarInt(balances.size)
        balances.forEach { (key, value) ->
            buf.writeUtf(key)
            buf.writeVarLong(value)
        }
        buf.writeVarInt(activeBuffs.size)
        activeBuffs.forEach { buff ->
            buf.writeUtf(buff.buffId)
            buf.writeVarInt(buff.level)
            buf.writeVarInt(buff.remainingSeconds)
        }
    }

    companion object {
        val TYPE = CustomPacketPayload.Type<BuffShopSyncPayload>(
            ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, "buff_shop_sync")
        )

        val CODEC: StreamCodec<FriendlyByteBuf, BuffShopSyncPayload> =
            CustomPacketPayload.codec(BuffShopSyncPayload::write, ::decode)

        private fun decode(buf: FriendlyByteBuf): BuffShopSyncPayload {
            val portuguese = buf.readBoolean()
            val balanceCount = buf.readVarInt()
            val balances = mutableMapOf<String, Long>()
            repeat(balanceCount) {
                balances[buf.readUtf()] = buf.readVarLong()
            }
            val buffCount = buf.readVarInt()
            val activeBuffs = List(buffCount) {
                ActiveBuffSync(buf.readUtf(), buf.readVarInt(), buf.readVarInt())
            }
            return BuffShopSyncPayload(portuguese, balances, activeBuffs)
        }
    }
}
