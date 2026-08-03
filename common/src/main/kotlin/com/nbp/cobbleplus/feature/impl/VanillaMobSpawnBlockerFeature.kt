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
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.server.MinecraftServer
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import com.nbp.cobbleplus.mixin.EndDragonFightAccessor
import net.minecraft.network.chat.Component
import net.minecraft.world.BossEvent

/**
 * Impede que mobs do Minecraft vanilla entrem no mundo.
 *
 * A decisão fica no código comum; cada loader chama [shouldBlock] no evento mais
 * precoce que oferece. Entidades de outros mods (incluindo Pokémon do Cobblemon),
 * jogadores e entidades não-Mob não são afetadas.
 */
object VanillaMobSpawnBlockerFeature : FeatureModule {
    private const val REPLACEMENT_SOURCE_TAG = "nbp_special_spawn_replacement"
    private const val ENDER_RAYQUAZA_TAG = "nbp_ender_dragon_replacement"
    private const val ENDER_CONTROLLER_TAG = "nbp_ender_dragon_controller"
    private val pendingDiscards = mutableListOf<Entity>()
    private val pendingReplacements = mutableMapOf<java.util.UUID, PokemonEntity>()
    private val activeDragonReplacements = mutableMapOf<java.util.UUID, DragonReplacement>()

    private data class DragonReplacement(
        val controller: EnderDragon,
        val rayquaza: PokemonEntity,
        val center: BlockPos
    )

    override val name: String = "Bloqueio de mobs vanilla"
    override val isEnabled: Boolean
        get() = NbpConfig.data.vanillaMobSpawnBlocker.enabled

    override fun onEnable() = Unit

    override fun onDisable() {
        activeDragonReplacements.values.forEach { encounter ->
            encounter.controller.isInvisible = false
            encounter.controller.isInvulnerable = false
            encounter.controller.isNoAi = false
            encounter.controller.isSilent = false
            encounter.controller.noPhysics = false
            encounter.controller.removeTag(ENDER_CONTROLLER_TAG)
            encounter.rayquaza.removeTag(ENDER_RAYQUAZA_TAG)
            encounter.controller.moveTo(encounter.center.x + 0.5, 128.0, encounter.center.z + 0.5)
        }
        activeDragonReplacements.clear()
    }

    fun shouldBlock(entity: Entity): Boolean {
        if (!isEnabled || entity !is Mob) return false
        if (entity.tags.contains(ENDER_CONTROLLER_TAG)) return false

        val entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.type)
        if (entityId.namespace != "minecraft") return false

        return NbpConfig.data.vanillaMobSpawnBlocker.allowedEntityTypes.none {
            it.equals(entityId.toString(), ignoreCase = true)
        }
    }

    fun hasConfiguredReplacement(entity: Entity): Boolean {
        val id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString()
        val isBossReplacement = id == "minecraft:wither" || id == "minecraft:ender_dragon"
        if (!shouldBlock(entity) || (!entity.tags.contains(REPLACEMENT_SOURCE_TAG) && !isBossReplacement) ||
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
                if (original is EnderDragon) {
                    activateDragonReplacement(original, replacement)
                    return@forEach
                }
                // Mantém o UUID que o Trial Spawner registrou. A recompensa só
                // avança quando este Pokémon deixar de existir no mundo.
                replacement.uuid = original.uuid
                if ((replacement.level() as ServerLevel).addFreshEntity(replacement)) {
                    val originalId = BuiltInRegistries.ENTITY_TYPE.getKey(original.type).toString()
                    if (originalId == "minecraft:wither") {
                        LegendaryVisuals.apply(replacement, replacement.pokemon.species.resourceIdentifier.path)
                    }
                }
            }
        }
    }

    fun tickBossReplacements(server: MinecraftServer) {
        recoverDragonReplacement(server)
        if (activeDragonReplacements.isEmpty()) return
        val iterator = activeDragonReplacements.iterator()
        while (iterator.hasNext()) {
            val (_, encounter) = iterator.next()
            val rayquaza = encounter.rayquaza
            val controller = encounter.controller
            if (rayquaza.isRemoved) {
                completeDragonReplacement(server, encounter)
                iterator.remove()
                continue
            }

            controller.moveTo(encounter.center.x + 0.5, -96.0, encounter.center.z + 0.5)
            controller.deltaMovement = Vec3.ZERO
            controller.isInvisible = true
            controller.isInvulnerable = true
            controller.isNoAi = true
            controller.isSilent = true
            controller.noPhysics = true
            containRayquaza(rayquaza, encounter.center)
        }
    }

    private fun activateDragonReplacement(dragon: EnderDragon, rayquaza: PokemonEntity) {
        val level = dragon.level() as? ServerLevel ?: return
        val center = dragon.fightOrigin ?: BlockPos.ZERO
        rayquaza.addTag(ENDER_RAYQUAZA_TAG)
        rayquaza.setPersistenceRequired()
        rayquaza.uuid = java.util.UUID.randomUUID()
        if (!level.addFreshEntity(rayquaza)) return
        LegendaryVisuals.apply(rayquaza, "rayquaza")

        dragon.isInvisible = true
        dragon.isInvulnerable = true
        dragon.isNoAi = true
        dragon.isSilent = true
        dragon.noPhysics = true
        dragon.addTag(ENDER_CONTROLLER_TAG)
        renameDragonBossBar(dragon)
        dragon.moveTo(center.x + 0.5, -96.0, center.z + 0.5)
        activeDragonReplacements[rayquaza.uuid] = DragonReplacement(dragon, rayquaza, center)
    }

    private fun recoverDragonReplacement(server: MinecraftServer) {
        if (activeDragonReplacements.isNotEmpty()) return
        if (server.tickCount % 100 != 0) return
        val level = server.getLevel(net.minecraft.world.level.Level.END) ?: return
        val controllers = level.allEntities.filterIsInstance<EnderDragon>()
            .filter { it.tags.contains(ENDER_CONTROLLER_TAG) && !it.isRemoved }
        val rayquazas = level.allEntities.filterIsInstance<PokemonEntity>()
            .filter { it.tags.contains(ENDER_RAYQUAZA_TAG) && !it.isRemoved }
        controllers.forEach { controller ->
            if (activeDragonReplacements.values.any { it.controller.uuid == controller.uuid }) return@forEach
            val rayquaza = rayquazas.minByOrNull { it.distanceToSqr(controller) } ?: return@forEach
            if (activeDragonReplacements.containsKey(rayquaza.uuid)) return@forEach
            activeDragonReplacements[rayquaza.uuid] = DragonReplacement(
                controller,
                rayquaza,
                controller.fightOrigin ?: BlockPos.ZERO
            )
            renameDragonBossBar(controller)
        }
    }

    private fun renameDragonBossBar(dragon: EnderDragon) {
        val fight = dragon.dragonFight ?: return
        val bossEvent = (fight as EndDragonFightAccessor).nbpDragonEvent
        bossEvent.name = Component.literal("Rayquaza")
        bossEvent.color = BossEvent.BossBarColor.GREEN
    }

    private fun completeDragonReplacement(server: MinecraftServer, encounter: DragonReplacement) {
        val controller = encounter.controller
        val fight = controller.dragonFight
        if (fight != null) {
            // Entity-join callbacks can run before EndDragonFight finishes assigning its UUID.
            // Set it explicitly so setDragonKilled cannot silently ignore this controller.
            (fight as EndDragonFightAccessor).setNbpDragonUUID(controller.uuid)
            fight.setDragonKilled(controller)
        }
        grantDragonProgress(server, encounter.center)
        controller.removeTag(ENDER_CONTROLLER_TAG)
        controller.discard()
    }

    private fun containRayquaza(rayquaza: PokemonEntity, center: BlockPos) {
        val config = NbpConfig.data.vanillaMobSpawnBlocker
        val radius = config.endRayquazaContainmentRadius.coerceAtLeast(32.0)
        val dx = rayquaza.x - (center.x + 0.5)
        val dz = rayquaza.z - (center.z + 0.5)
        val outside = dx * dx + dz * dz > radius * radius
        val unsafeY = rayquaza.y < config.endRayquazaMinimumY || rayquaza.y > config.endRayquazaMaximumY
        if (!outside && !unsafeY) return

        val level = rayquaza.level() as ServerLevel
        val safe = LegendarySpawnSafety.find(level, center.above(80), 24) ?: center.above(80)
        rayquaza.teleportTo(safe.x + 0.5, safe.y + 2.0, safe.z + 0.5)
        rayquaza.deltaMovement = Vec3.ZERO
    }

    private fun grantDragonProgress(server: MinecraftServer, center: BlockPos) {
        val advancement = server.advancements.get(net.minecraft.resources.ResourceLocation.parse("minecraft:end/kill_dragon")) ?: return
        server.playerList.players.filter { player ->
            player.level().dimension() == net.minecraft.world.level.Level.END &&
                player.distanceToSqr(center.x + 0.5, player.y, center.z + 0.5) <= 192.0 * 192.0
        }.forEach { player ->
            val progress = player.advancements.getOrStartProgress(advancement)
            progress.remainingCriteria.toList().forEach { player.advancements.award(advancement, it) }
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
