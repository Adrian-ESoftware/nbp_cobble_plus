package com.nbp.cobbleplus.network

import com.nbp.cobbleplus.NbpCobblePlus
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

/** Recompensa exibida na tela (ícone do item + quantidade máxima da dificuldade). */
data class RewardViewRow(val itemId: String, val count: Int)

/** Linha de uma missão na tela; strings já resolvidas no servidor. */
data class MissionViewRow(
    val instanceId: String,
    val description: String,
    val difficulty: String,
    val progress: Int,
    val quantity: Int,
    val completed: Boolean,
    val lockedBy: String?,
    val rewards: List<RewardViewRow>
)

/**
 * Pacote S2C com o conteúdo das duas abas (Diárias e Semanais) para abrir a tela
 * [com.nbp.cobbleplus.hud.MissionsScreen]. O servidor resolve idioma e textos.
 */
class MissionsViewSyncPayload(
    val portuguese: Boolean,
    val daily: List<MissionViewRow>,
    val weekly: List<MissionViewRow>
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<MissionsViewSyncPayload> = TYPE

    private fun write(buf: FriendlyByteBuf) {
        buf.writeBoolean(portuguese)
        writeRows(buf, daily)
        writeRows(buf, weekly)
    }

    companion object {
        val TYPE: CustomPacketPayload.Type<MissionsViewSyncPayload> = CustomPacketPayload.Type(
            ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, "missions_view_sync")
        )

        val CODEC: StreamCodec<FriendlyByteBuf, MissionsViewSyncPayload> =
            CustomPacketPayload.codec(MissionsViewSyncPayload::write, ::decode)

        private fun writeRows(buf: FriendlyByteBuf, rows: List<MissionViewRow>) {
            buf.writeVarInt(rows.size)
            rows.forEach { row ->
                buf.writeUtf(row.instanceId)
                buf.writeUtf(row.description)
                buf.writeUtf(row.difficulty)
                buf.writeVarInt(row.progress)
                buf.writeVarInt(row.quantity)
                buf.writeBoolean(row.completed)
                buf.writeNullable(row.lockedBy, FriendlyByteBuf::writeUtf)
                buf.writeVarInt(row.rewards.size)
                row.rewards.forEach { reward ->
                    buf.writeUtf(reward.itemId)
                    buf.writeVarInt(reward.count)
                }
            }
        }

        private fun readRows(buf: FriendlyByteBuf): List<MissionViewRow> {
            val size = buf.readVarInt()
            return List(size) {
                val instanceId = buf.readUtf()
                val description = buf.readUtf()
                val difficulty = buf.readUtf()
                val progress = buf.readVarInt()
                val quantity = buf.readVarInt()
                val completed = buf.readBoolean()
                val lockedBy = buf.readNullable(FriendlyByteBuf::readUtf)
                val rewardsSize = buf.readVarInt()
                val rewards = List(rewardsSize) {
                    RewardViewRow(buf.readUtf(), buf.readVarInt())
                }
                MissionViewRow(instanceId, description, difficulty, progress, quantity, completed, lockedBy, rewards)
            }
        }

        private fun decode(buf: FriendlyByteBuf): MissionsViewSyncPayload {
            val portuguese = buf.readBoolean()
            return MissionsViewSyncPayload(portuguese, readRows(buf), readRows(buf))
        }
    }
}