package com.nbp.cobbleplus.feature.impl

import com.nbp.cobbleplus.mission.MissionInstance
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

/**
 * Store world-level das missões (mesmo padrão de PointsSavedData).
 *
 * Diárias são por jogador; o progresso semanal também é por jogador, mas as instâncias
 * semanais são compartilhadas (server-wide) e a primeira conclusão trava a missão.
 */
internal class MissionsSavedData : SavedData() {
    val daily = mutableMapOf<UUID, MutableMap<String, MissionInstance>>()
    val dailyWindow = mutableMapOf<UUID, Long>()
    val weekly = mutableMapOf<String, MissionInstance>()
    val weeklyProgress = mutableMapOf<UUID, MutableMap<String, Int>>()
    var weeklyWindow = 0L

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        val dailyTag = CompoundTag()
        daily.forEach { (uuid, instances) ->
            val inner = CompoundTag()
            instances.forEach { (instId, instance) -> inner.put(instId, instance.toTag()) }
            dailyTag.put(uuid.toString(), inner)
        }
        tag.put("daily", dailyTag)

        val windowTag = CompoundTag()
        dailyWindow.forEach { (uuid, window) -> windowTag.putLong(uuid.toString(), window) }
        tag.put("dailyWindow", windowTag)

        val weeklyTag = CompoundTag()
        weekly.forEach { (instId, instance) -> weeklyTag.put(instId, instance.toTag()) }
        tag.put("weekly", weeklyTag)

        val weeklyProgressTag = CompoundTag()
        weeklyProgress.forEach { (uuid, values) ->
            val inner = CompoundTag()
            values.forEach { (instId, progress) -> inner.putInt(instId, progress) }
            weeklyProgressTag.put(uuid.toString(), inner)
        }
        tag.put("weeklyProgress", weeklyProgressTag)

        tag.putLong("weeklyWindow", weeklyWindow)
        return tag
    }

    companion object {
        private const val NAME = "nbp_cobble_plus_missions"

        private fun load(tag: CompoundTag, registries: HolderLookup.Provider): MissionsSavedData {
            val data = MissionsSavedData()
            val dailyTag = tag.getCompound("daily")
            dailyTag.allKeys.forEach { key ->
                runCatching { UUID.fromString(key) }.getOrNull()?.let { uuid ->
                    val inner = dailyTag.getCompound(key)
                    data.daily[uuid] = inner.allKeys.associate { instId -> instId to MissionInstance.fromTag(inner.getCompound(instId)) }.toMutableMap()
                }
            }
            val windowTag = tag.getCompound("dailyWindow")
            windowTag.allKeys.forEach { key ->
                runCatching { UUID.fromString(key) }.getOrNull()?.let { uuid ->
                    data.dailyWindow[uuid] = windowTag.getLong(key)
                }
            }
            val weeklyTag = tag.getCompound("weekly")
            weeklyTag.allKeys.forEach { instId ->
                data.weekly[instId] = MissionInstance.fromTag(weeklyTag.getCompound(instId))
            }
            val weeklyProgressTag = tag.getCompound("weeklyProgress")
            weeklyProgressTag.allKeys.forEach { key ->
                runCatching { UUID.fromString(key) }.getOrNull()?.let { uuid ->
                    val inner = weeklyProgressTag.getCompound(key)
                    data.weeklyProgress[uuid] = inner.allKeys.associate { instId -> instId to inner.getInt(instId) }.toMutableMap()
                }
            }
            data.weeklyWindow = tag.getLong("weeklyWindow")
            return data
        }

        private val FACTORY = Factory(::MissionsSavedData, ::load, DataFixTypes.LEVEL)
        fun get(server: MinecraftServer): MissionsSavedData =
            server.overworld().dataStorage.computeIfAbsent(FACTORY, NAME)
    }
}

internal class MissionsStore {
    private var saved: MissionsSavedData? = null

    fun bind(server: MinecraftServer) { saved = MissionsSavedData.get(server) }
    fun unbind() { saved = null }
    fun require(): MissionsSavedData = checkNotNull(saved) { "Missions store is not bound to a server" }
    fun getOrNull(): MissionsSavedData? = saved
}