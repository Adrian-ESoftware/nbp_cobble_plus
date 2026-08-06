package com.nbp.cobbleplus.feature.impl

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.ActorType
import com.cobblemon.mod.common.api.battles.model.actor.EntityBackedBattleActor
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.nbp.cobbleplus.config.NbpConfig
import com.nbp.cobbleplus.feature.FeatureModule
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Bridges RCT trainer ids to permanent Showdown field conditions.  RCT is optional;
 * NPCs which do not expose getTrainerId() are simply ignored.
 */
object RctBattleConditionFeature : FeatureModule {
    override val name = "RCT permanent battle conditions"
    override val isEnabled get() = NbpConfig.data.rctBattleCondition.enabled
    private val active = mutableMapOf<UUID, Pair<PokemonBattle, String>>()
    private var subscribed = false
    private val logger = LoggerFactory.getLogger("NBP-RCTConditions")

    override fun onEnable() {
        if (subscribed) return
        subscribed = true
        CobblemonEvents.BATTLE_STARTED_POST.subscribe { event ->
            val npcActors = event.battle.actors.filter { it.type == ActorType.NPC }
            val condition = npcActors.firstNotNullOfOrNull { actor ->
                val entity = (actor as? EntityBackedBattleActor<*>)?.entity
                entity?.let(::trainerCondition)
            }
            if (condition == null) {
                logger.info("RCT battle {} has no configured trainer condition (NPC actors: {})", event.battle.battleId, npcActors.count())
                return@subscribe
            }
            active[event.battle.battleId] = event.battle to condition
            logger.info("Applying permanent RCT condition '{}' to battle {}", condition, event.battle.battleId)
            apply(event.battle, condition)
        }
    }

    override fun onDisable() {
        active.clear()
        subscribed = false
    }

    fun tick(server: MinecraftServer) {
        if (!isEnabled) return
        val expired = active.filterValues { it.first.ended }.keys
        expired.forEach(active::remove)
        // Re-apply every second so moves/abilities cannot remove the configured condition.
        if (server.tickCount % 20 != 0) return
        active.values.forEach { (battle, condition) -> apply(battle, condition) }
    }

    private fun trainerCondition(npc: Any): String? {
        val id = runCatching {
            npc.javaClass.methods.firstOrNull { it.name == "getTrainerId" && it.parameterCount == 0 }
                ?.invoke(npc) as? String
        }.getOrNull() ?: return null
        logger.info("Detected RCT trainer id '{}'", id)
        return NbpConfig.data.rctBattleCondition.trainerConditions.entries
            .firstOrNull { it.key.equals(id, ignoreCase = true) }
            ?.value
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun apply(battle: PokemonBattle, condition: String) {
        val id = when (condition) {
            "rain", "raindance" -> "raindance"
            "sun", "sunny", "sunnyday" -> "sunnyday"
            "sand", "sandstorm" -> "sandstorm"
            "hail" -> "hail"
            "snow" -> "snow"
            "electric", "electricterrain" -> "electricterrain"
            "grassy", "grassyterrain" -> "grassyterrain"
            "misty", "mistyterrain" -> "mistyterrain"
            "psychic", "psychicterrain" -> "psychicterrain"
            else -> return
        }
        // The eval command is handled by Cobblemon's bundled Showdown service.
        // A very large duration makes the effect permanent for this battle.
        val script = if (id.endsWith("terrain"))
            "battle.field.setTerrain('$id', 'debug'); battle.field.terrainState.duration = 999999"
        else
            "battle.field.setWeather('$id', 'debug'); battle.field.weatherState.duration = 999999"
        runCatching { battle.writeShowdownAction(">eval $script") }
            .onFailure { logger.warn("Could not apply RCT condition '{}'", condition, it) }
    }
}
