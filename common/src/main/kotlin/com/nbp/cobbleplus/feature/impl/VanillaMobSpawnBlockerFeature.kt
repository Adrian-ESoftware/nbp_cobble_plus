package com.nbp.cobbleplus.feature.impl

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.nbp.cobbleplus.NbpCobblePlus
import com.nbp.cobbleplus.config.NbpConfig
import com.nbp.cobbleplus.feature.FeatureModule
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Mob

/**
 * Impede que mobs do Minecraft vanilla entrem no mundo.
 *
 * A decisão fica no código comum; cada loader chama [shouldBlock] no evento mais
 * precoce que oferece. Entidades de outros mods (incluindo Pokémon do Cobblemon),
 * jogadores e entidades não-Mob não são afetadas.
 */
object VanillaMobSpawnBlockerFeature : FeatureModule {
    private const val REPLACEMENT_SOURCE_TAG = "nbp_special_spawn_replacement"
    private val pendingDiscards = mutableListOf<Entity>()
    private val pendingReplacements = mutableMapOf<java.util.UUID, PokemonEntity>()

    override val name: String = "Bloqueio de mobs vanilla"
    override val isEnabled: Boolean
        get() = NbpConfig.data.vanillaMobSpawnBlocker.enabled

    override fun onEnable() = Unit

    override fun onDisable() = Unit

    fun shouldBlock(entity: Entity): Boolean {
        if (!isEnabled || entity !is Mob) return false

        val entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.type)
        if (entityId.namespace != "minecraft") return false

        return NbpConfig.data.vanillaMobSpawnBlocker.allowedEntityTypes.none {
            it.equals(entityId.toString(), ignoreCase = true)
        }
    }

    fun hasConfiguredReplacement(entity: Entity): Boolean {
        if (!shouldBlock(entity) || !entity.tags.contains(REPLACEMENT_SOURCE_TAG) ||
            !NbpConfig.data.vanillaMobSpawnBlocker.enablePokemonReplacements) {
            return false
        }

        val entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString()
        return replacementCandidates(entityId).isNotEmpty()
    }

    @JvmStatic
    fun markReplacementSource(entity: Entity) {
        entity.addTag(REPLACEMENT_SOURCE_TAG)
    }

    /**
     * Remove no próximo trabalho do servidor, depois que spawners vanilla e trial
     * spawners tiveram tempo de registrar o spawn como bem-sucedido e aplicar seu
     * intervalo normal. Cancelar o evento imediatamente faz o trial spawner tentar
     * novamente a cada tick.
     */
    fun discardAfterSpawnerRegistration(entity: Entity) {
        pendingDiscards += entity
    }

    fun flushPendingDiscards() {
        if (pendingDiscards.isEmpty()) return
        val entities = pendingDiscards.toList()
        pendingDiscards.clear()
        entities.forEach { original ->
            val replacement = pendingReplacements.remove(original.uuid)
            original.discard()

            if (replacement != null) {
                // Mantém o UUID que o Trial Spawner registrou. A recompensa só
                // avança quando este Pokémon deixar de existir no mundo.
                replacement.uuid = original.uuid
                (replacement.level() as ServerLevel).addFreshEntity(replacement)
            }
        }
    }

    /**
     * Prepara a substituição configurada antes que o loader rejeite o mob vanilla.
     * Retorna false quando o tipo deve apenas ser bloqueado ou quando o limite
     * local de Pokémon equivalentes já foi alcançado.
     */
    fun spawnReplacement(entity: Entity): Boolean {
        if (!shouldBlock(entity)) return false

        val level = entity.level() as? ServerLevel ?: return false
        val config = NbpConfig.data.vanillaMobSpawnBlocker
        if (!config.enablePokemonReplacements) return false

        val entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString()
        val candidates = replacementCandidates(entityId)
        if (candidates.isEmpty()) return false

        val radius = config.replacementRadius.coerceAtLeast(1.0)
        val maxNearby = config.maxNearbyReplacements.coerceAtLeast(0)
        if (maxNearby == 0) return false

        // Faz o parse uma única vez por tentativa. A versão anterior repetia o
        // parse para cada Pokémon próximo x cada candidato, agravando trials cheias.
        val candidateSpecies = candidates.mapNotNull { PokemonProperties.parse(it).species }
        val nearbyReplacements = level.getEntitiesOfClass(
            PokemonEntity::class.java,
            entity.boundingBox.inflate(radius)
        ) { pokemon ->
            candidateSpecies.any { species ->
                species.equals(pokemon.pokemon.species.resourceIdentifier.toString(), ignoreCase = true) ||
                    species.substringAfter(':').equals(pokemon.pokemon.species.name, ignoreCase = true)
            }
        }
        if (nearbyReplacements.size >= maxNearby) return false

        return try {
            val properties = PokemonProperties.parse(candidates[level.random.nextInt(candidates.size)])
            properties.level?.let { baseLevel ->
                val variance = config.replacementLevelVariance.coerceAtLeast(0)
                val minimum = (baseLevel - variance).coerceAtLeast(1)
                val maximum = (baseLevel + variance).coerceAtMost(100)
                properties.level = minimum + level.random.nextInt(maximum - minimum + 1)
            }
            val pokemon = properties.createEntity(level)
            pokemon.moveTo(entity.x, entity.y, entity.z, entity.yRot, entity.xRot)
            // Adicionada somente após o original sair, permitindo reutilizar o
            // UUID acompanhado pelo Trial Spawner sem colisão de entidades.
            pendingReplacements[entity.uuid] = pokemon
            true
        } catch (exception: Exception) {
            NbpCobblePlus.logger.error("Falha ao substituir $entityId por Pokémon", exception)
            false
        }
    }

    private fun replacementCandidates(entityId: String): List<String> =
        NbpConfig.data.vanillaMobSpawnBlocker.pokemonReplacements.entries
            .firstOrNull { it.key.equals(entityId, ignoreCase = true) }
            ?.value
            ?.filter { it.isNotBlank() }
            .orEmpty()
}
