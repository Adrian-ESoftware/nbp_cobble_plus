package com.nbp.cobbleplus.network

import com.nbp.cobbleplus.NbpCobblePlus
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Pacote S2C com as linhas de texto do HUD do combo de capturas.
 * Lista vazia significa "esconder o HUD".
 */
data class CatchComboSyncPayload(val lines: List<String>) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<CatchComboSyncPayload> = TYPE

    private fun write(buf: FriendlyByteBuf) {
        buf.writeVarInt(lines.size)
        lines.forEach { buf.writeUtf(it) }
    }

    companion object {
        val TYPE: CustomPacketPayload.Type<CatchComboSyncPayload> =
            CustomPacketPayload.createType("${NbpCobblePlus.MOD_ID}:catch_combo_sync")

        val CODEC: StreamCodec<FriendlyByteBuf, CatchComboSyncPayload> =
            CustomPacketPayload.codec(CatchComboSyncPayload::write, ::decode)

        private fun decode(buf: FriendlyByteBuf): CatchComboSyncPayload {
            val size = buf.readVarInt()
            val lines = ArrayList<String>(size)
            repeat(size) { lines.add(buf.readUtf()) }
            return CatchComboSyncPayload(lines)
        }
    }
}
