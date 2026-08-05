package com.nbp.cobbleplus.feature.impl

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition
import com.cobblemon.mod.common.pokemon.Nature
import com.nbp.cobbleplus.config.NbpConfig
import com.nbp.cobbleplus.feature.FeatureModule
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.ai.targeting.TargetingConditions
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.random.Random

/** Implements the out-of-battle effect of the Synchronize ability. */
object SynchronizedNaturesFeature : FeatureModule {
    override val name = "Synchronized natures"
    override val isEnabled: Boolean get() = NbpConfig.data.synchronizedNatures.enabled

    override fun onEnable() = Unit
    override fun onDisable() = Unit

    fun possiblyChangeNature(position: SpawnablePosition, props: PokemonProperties) {
        val config = NbpConfig.data.synchronizedNatures
        if (!config.enabled) return
        val marbles = config.marbles.coerceAtLeast(1)
        val chance = config.chance.coerceIn(0, marbles)
        if (chance == 0 || Random.nextInt(marbles) >= chance) return

        val range = config.effectiveRange.coerceAtLeast(1).toDouble()
        val naturePool = position.world.getNearbyPlayers(
            TargetingConditions.forNonCombat()
                .ignoreLineOfSight()
                .ignoreInvisibilityTesting(),
            null,
            AABB.ofSize(Vec3.atCenterOf(position.position), range, range, range)
        ).mapNotNull { player ->
            (player as? ServerPlayer)?.let { synchronizedNature(it, config.mustBeFirst) }
        }
        if (naturePool.isNotEmpty()) {
            props.nature = naturePool[Random.nextInt(naturePool.size)].name.path
        }
    }

    private fun synchronizedNature(player: ServerPlayer, mustBeFirst: Boolean): Nature? {
        val party = Cobblemon.storage.getParty(player)
        if (mustBeFirst) {
            val first = party.firstOrNull() ?: return null
            return if (first.ability.name == "synchronize") first.nature else null
        }
        return party.firstOrNull { it.ability.name == "synchronize" }?.nature
    }
}
