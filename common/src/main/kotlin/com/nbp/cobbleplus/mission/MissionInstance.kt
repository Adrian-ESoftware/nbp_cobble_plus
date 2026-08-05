package com.nbp.cobbleplus.mission

import net.minecraft.nbt.CompoundTag
import java.util.UUID

/**
 * Instância runtime de uma missão gerada para um ciclo. Guarda tudo que precisa ser
 * persistido (alvo, quantidade, rolos, flags) de forma autocontida — mesmo que a
 * definição saia do config, a missão ativa continua exibível e completável.
 */
class MissionInstance(
    val instanceId: String,
    val cycle: MissionCycle,
    val definitionId: String,
    val action: MissionAction,
    val difficulty: String,
    val target: MissionTargetValue?,
    val quantity: Int,
    val rewardRolls: Int,
    val bucketId: String,
    val sequence: Boolean,
    var progress: Int = 0,
    var completed: Boolean = false,
    var completedBy: UUID? = null
) {
    val isComplete: Boolean get() = completed || progress >= quantity

    fun toTag(): CompoundTag {
        val tag = CompoundTag()
        tag.putString("instanceId", instanceId)
        tag.putString("cycle", cycle.id)
        tag.putString("definitionId", definitionId)
        tag.putString("action", action.id)
        tag.putString("difficulty", difficulty)
        tag.putString("bucketId", bucketId)
        tag.putInt("quantity", quantity)
        tag.putInt("rewardRolls", rewardRolls)
        tag.putInt("progress", progress)
        tag.putBoolean("sequence", sequence)
        tag.putBoolean("completed", completed)
        target?.let { t ->
            t.species?.let { tag.putString("species", it) }
            t.type?.let { tag.putString("type", it) }
            t.nature?.let { tag.putString("nature", it) }
        }
        completedBy?.let { tag.putString("completedBy", it.toString()) }
        return tag
    }

    companion object {
        private fun CompoundTag.optString(name: String): String? =
            if (contains(name)) getString(name) else null

        fun fromTag(tag: CompoundTag): MissionInstance {
            val species = tag.optString("species")
            val type = tag.optString("type")
            val nature = tag.optString("nature")
            return MissionInstance(
                instanceId = tag.getString("instanceId"),
                cycle = MissionCycle.byId(tag.getString("cycle")) ?: MissionCycle.DAILY,
                definitionId = tag.getString("definitionId"),
                action = MissionAction.byId(tag.getString("action")) ?: MissionAction.CAPTURE,
                difficulty = tag.getString("difficulty"),
                target = if (species == null && type == null && nature == null) {
                    null
                } else {
                    MissionTargetValue(species = species, type = type, nature = nature)
                },
                quantity = tag.getInt("quantity"),
                rewardRolls = tag.getInt("rewardRolls"),
                bucketId = tag.getString("bucketId"),
                sequence = tag.getBoolean("sequence"),
                progress = tag.getInt("progress"),
                completed = tag.getBoolean("completed"),
                completedBy = tag.optString("completedBy")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            )
        }
    }
}