package com.nbp.cobbleplus.feature.impl

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail
import com.cobblemon.mod.common.api.spawning.influence.SpawningInfluence
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition
import com.cobblemon.mod.common.util.spawner
import com.nbp.cobbleplus.NbpCobblePlus
import com.nbp.cobbleplus.config.LegendarySpawnEntry
import com.nbp.cobbleplus.config.NbpConfig
import com.nbp.cobbleplus.feature.FeatureModule
import com.nbp.cobbleplus.i18n.PlayerLanguage
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.FluidTags
import net.minecraft.tags.TagKey
import net.minecraft.util.RandomSource
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.saveddata.SavedData
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** Spawn global de lendários sem depender da área física ocupada por cada bioma. */
object LegendarySpawnerFeature : FeatureModule {
    override val name: String = "Spawn de lendários"
    override val isEnabled: Boolean get() = NbpConfig.data.legendarySpawner.enabled

    private var ticks = 0L
    private var history: LegendarySpawnHistory? = null
    private var server: MinecraftServer? = null
    private val blockedInfluencePlayers = mutableSetOf<java.util.UUID>()

    override fun onEnable() = Unit
    override fun onDisable() {
        removeNaturalSpawnBlockers()
    }

    fun bindServer(server: MinecraftServer) {
        this.server = server
        history = LegendarySpawnHistory.get(server)
        ticks = 0L
    }

    fun unbindServer() {
        removeNaturalSpawnBlockers()
        server = null
        history = null
        ticks = 0L
    }

    fun tick(server: MinecraftServer) {
        if (!isEnabled) return
        server.playerList.players.forEach(::attachNaturalSpawnBlocker)
        ticks++
        val config = NbpConfig.data.legendarySpawner
        val interval = config.intervalMinutes.coerceAtLeast(1) * 60L * 20L
        if (ticks % interval != 0L) return
        if (server.overworld().random.nextDouble() > config.spawnChance.coerceIn(0.0, 1.0)) return

        trySpawn(server)
    }

    private fun attachNaturalSpawnBlocker(player: ServerPlayer) {
        val config = NbpConfig.data.legendarySpawner
        if (!config.blockNaturalPokemonSpawns || blockedNaturalSpecies(config).isEmpty()) return
        if (!blockedInfluencePlayers.add(player.uuid)) return
        runCatching {
            player.spawner.influences += NaturalSpawnBlocker
        }.onFailure {
            blockedInfluencePlayers.remove(player.uuid)
            NbpCobblePlus.logger.warn("Não foi possível bloquear spawns naturais para ${player.scoreboardName}", it)
        }
    }

    private fun removeNaturalSpawnBlockers() {
        history?.let { /* keep saved history independent from runtime influences */ }
        // Player references are only available while the server is bound; the normal
        // server stop path calls this before clearing the feature's server reference.
        server?.playerList?.players?.forEach { player ->
            player.spawner.influences.removeAll { it === NaturalSpawnBlocker }
        }
        blockedInfluencePlayers.clear()
    }

    private object NaturalSpawnBlocker : SpawningInfluence {
        override fun affectWeight(detail: SpawnDetail, spawnablePosition: SpawnablePosition, weight: Float): Float {
            val config = NbpConfig.data.legendarySpawner
            if (!config.blockNaturalPokemonSpawns) return weight
            val species = (detail as? PokemonSpawnDetail)?.pokemon?.species ?: return weight
            val id = species.substringAfterLast(":").lowercase()
            return if (blockedNaturalSpecies(config).any { it.substringAfterLast(":").equals(id, true) }) 0f else weight
        }
    }

    /** Handles old config files generated before the block list existed. */
    @Suppress("UNCHECKED_CAST")
    private fun blockedNaturalSpecies(config: com.nbp.cobbleplus.config.LegendarySpawnerConfig): List<String> =
        runCatching { config.blockedNaturalSpecies as? List<String> }
            .getOrNull()
            ?: config.legendaryPool.map { it.species }

    fun forceSpawn(player: ServerPlayer, species: String? = null): Boolean =
        trySpawn(player.server, player, species?.lowercase()?.takeIf { it.isNotBlank() })

    fun forceNaturalSpawn(server: MinecraftServer): Boolean = trySpawn(server)

    fun playerChance(server: MinecraftServer, player: ServerPlayer): Double =
        (currentPlayerChances(server)[player.uuid] ?: 0.0) * 100.0

    fun availableSpecies(player: ServerPlayer): List<String> {
        val dimension = player.level().dimension().location().toString()
        return NbpConfig.data.legendarySpawner.legendaryPool
            .filter { entry ->
                entry.dimensions.any { it.equals(dimension, true) } &&
                    timeMatches(player.serverLevel(), entry) &&
                    biomeMatches(player.serverLevel(), player.blockPosition(), entry.biomes)
            }
            .map { it.species }
            .distinct()
            .sorted()
    }

    fun historySummary(server: MinecraftServer): String {
        val chances = currentPlayerChances(server)
        val lines = server.playerList.players.joinToString("\n") { player ->
            val chance = (chances[player.uuid] ?: 0.0) * 100.0
            "§7- §f${player.scoreboardName}: §e${history?.playerSpawnCount(player.uuid) ?: 0} spawn(s) §7| §a${"%.2f".format(java.util.Locale.US, chance)}%"
        }
        return "§6Histórico de anfitriões online:\n${lines.ifBlank { "§7Nenhum jogador online." }}\n" +
            "§7Espécies que já nasceram: §e${history?.speciesCount() ?: 0}"
    }

    private fun currentPlayerChances(server: MinecraftServer): Map<java.util.UUID, Double> {
        val config = NbpConfig.data.legendarySpawner
        val dimensions = server.allLevels.mapNotNull { level ->
            val id = level.dimension().location().toString()
            val players = level.players().filter { !it.isSpectator }
            val hasEligibleSpecies = config.legendaryPool.any { entry ->
                entry.dimensions.any { it.equals(id, true) } && timeMatches(level, entry)
            }
            if (players.isEmpty() || !hasEligibleSpecies) null
            else DimensionCandidate(level, id, players, emptyList(), config.dimensionWeights[id] ?: 1.0)
        }.filter { it.weight > 0.0 }
        if (dimensions.isEmpty()) return emptyMap()

        val players = dimensions.flatMap { it.players }.distinctBy { it.uuid }
        val minimum = players.minOfOrNull { history?.playerSpawnCount(it.uuid) ?: 0 } ?: 0
        val dimensionWeights = dimensions.associateWith { candidate ->
            candidate.weight * candidate.players.map { playerWeight(it, minimum) }.average()
        }
        val totalDimensionWeight = dimensionWeights.values.sum()
        if (totalDimensionWeight <= 0.0) return emptyMap()

        val result = mutableMapOf<java.util.UUID, Double>()
        dimensions.forEach { candidate ->
            val dimensionChance = (dimensionWeights[candidate] ?: 0.0) / totalDimensionWeight
            val playerWeights = candidate.players.associateWith { playerWeight(it, minimum) }
            val totalPlayerWeight = playerWeights.values.sum()
            if (totalPlayerWeight > 0.0) {
                playerWeights.forEach { (player, weight) ->
                    result[player.uuid] = (result[player.uuid] ?: 0.0) + dimensionChance * weight / totalPlayerWeight
                }
            }
        }
        return result
    }

    fun resetHistory() {
        history?.reset()
    }

    private fun trySpawn(server: MinecraftServer, forcedPlayer: ServerPlayer? = null, forcedSpecies: String? = null): Boolean {
        val config = NbpConfig.data.legendarySpawner
        val dimensions = server.allLevels.mapNotNull { level ->
            val id = level.dimension().location().toString()
            val players = level.players().filter { !it.isSpectator && (forcedPlayer == null || it.uuid == forcedPlayer.uuid) }
            val entries = config.legendaryPool.filter { entry ->
                entry.dimensions.any { it.equals(id, true) } &&
                    (forcedSpecies == null || entry.species.equals(forcedSpecies, true)) &&
                    (forcedPlayer != null || timeMatches(level, entry))
            }
            if (players.isEmpty() || entries.isEmpty()) null
            else DimensionCandidate(level, id, players, entries, config.dimensionWeights[id] ?: 1.0)
        }.filter { it.weight > 0.0 }
        if (dimensions.isEmpty()) return false

        val onlinePlayers = dimensions.flatMap { it.players }.distinctBy { it.uuid }
        val minimumPlayerSpawns = onlinePlayers.minOfOrNull { history?.playerSpawnCount(it.uuid) ?: 0 } ?: 0
        val dimension = weighted(
            dimensions,
            { candidate ->
                val averageFairness = candidate.players.map { playerWeight(it, minimumPlayerSpawns) }.average()
                candidate.weight * averageFairness
            },
            server.overworld().random
        ) ?: return false
        val entry = weighted(dimension.entries, { spawnWeight(it) }, dimension.level.random) ?: return false
        val player = weighted(
            dimension.players,
            { playerWeight(it, minimumPlayerSpawns) },
            dimension.level.random
        ) ?: return false
        val position = findPosition(dimension.level, player, entry, true)
            ?: (if (config.allowBiomeFallback) findPosition(dimension.level, player, entry, false) else null)
            ?: return false

        val minLevel = config.minimumLevel.coerceIn(1, 100)
        val maxLevel = config.maximumLevel.coerceIn(minLevel, 100)
        val pokemonLevel = minLevel + dimension.level.random.nextInt(maxLevel - minLevel + 1)
        val properties = PokemonProperties.parse(
            "species=${entry.species} level=$pokemonLevel min_perfect_ivs=${config.perfectIvCount.coerceIn(0, 6)}"
        )
        val pokemon = properties.createEntity(dimension.level)
        pokemon.moveTo(position.x + 0.5, position.y.toDouble(), position.z + 0.5, dimension.level.random.nextFloat() * 360F, 0F)
        pokemon.setPersistenceRequired()
        if (!dimension.level.addFreshEntity(pokemon)) return false
        LegendaryVisuals.apply(pokemon, entry.species)

        history?.markSpawned(entry.species, player.uuid)
        if (config.announceSpawn) {
            server.playerList.players.forEach { recipient ->
                var message = PlayerLanguage.template(recipient, "legend.spawn", config.spawnMessage,
                    "pokemon" to entry.species, "dimension" to dimension.id, "player" to player.scoreboardName,
                    "x" to position.x, "y" to position.y, "z" to position.z)
                if (config.includeCoordinatesInAnnouncement &&
                    !config.spawnMessage.contains("{x}") && !config.spawnMessage.contains("{y}") && !config.spawnMessage.contains("{z}")) {
                    message += " §7[§fX: ${position.x} Y: ${position.y} Z: ${position.z}§7]"
                }
                recipient.sendSystemMessage(Component.literal(message))
            }
        }
        NbpCobblePlus.logger.info("Lendário ${entry.species} nasceu em ${dimension.id} em $position")
        return true
    }

    private fun spawnWeight(entry: LegendarySpawnEntry): Double {
        var weight = entry.weight.coerceAtLeast(0.0)
        val config = NbpConfig.data.legendarySpawner
        if (config.reduceRepeatChance && history?.hasSpawned(entry.species) == true) {
            weight /= config.repeatChanceDivisor.coerceAtLeast(1.0)
        }
        return weight
    }

    private fun playerWeight(player: ServerPlayer, minimumOnlineSpawns: Int): Double {
        val config = NbpConfig.data.legendarySpawner
        if (!config.balanceSpawnsBetweenPlayers) return 1.0
        val count = history?.playerSpawnCount(player.uuid) ?: 0
        val difference = (count - minimumOnlineSpawns).coerceAtLeast(0)
        return 1.0 / config.playerRepeatChanceDivisor.coerceAtLeast(1.0).pow(difference)
    }

    private fun findPosition(level: ServerLevel, player: ServerPlayer, entry: LegendarySpawnEntry, requireBiome: Boolean): BlockPos? {
        val config = NbpConfig.data.legendarySpawner
        val minDistance = config.minimumDistanceFromPlayer.coerceAtLeast(16)
        val maxDistance = config.maximumDistanceFromPlayer.coerceAtLeast(minDistance)
        repeat(config.locationAttempts.coerceIn(1, 256)) {
            val angle = level.random.nextDouble() * Math.PI * 2.0
            val distance = minDistance + level.random.nextInt(maxDistance - minDistance + 1)
            val x = player.blockX + (cos(angle) * distance).toInt()
            val z = player.blockZ + (sin(angle) * distance).toInt()
            val column = BlockPos(x, player.blockY, z)
            if (!level.hasChunkAt(column)) return@repeat

            val position = if (entry.aquatic) aquaticPosition(level, x, z) else LegendarySpawnSafety.findColumn(level, x, z, player.blockY)
            if (position != null && (!requireBiome || biomeMatches(level, position, entry.biomes))) return position
        }
        return null
    }

    private fun aquaticPosition(level: ServerLevel, x: Int, z: Int): BlockPos? {
        val surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z)
        for (y in surface downTo maxOf(level.minBuildHeight + 2, surface - 24)) {
            val pos = BlockPos(x, y, z)
            if (level.getFluidState(pos).`is`(FluidTags.WATER)) return pos
        }
        return null
    }

    private fun biomeMatches(level: ServerLevel, pos: BlockPos, configured: List<String>): Boolean {
        if (configured.isEmpty()) return true
        val biome = level.getBiome(pos)
        return configured.any { raw ->
            val isTag = raw.startsWith('#')
            val id = ResourceLocation.tryParse(raw.removePrefix("#")) ?: return@any false
            if (isTag) biome.`is`(TagKey.create(Registries.BIOME, id))
            else biome.unwrapKey().map { it.location() == id }.orElse(false)
        }
    }

    private fun timeMatches(level: ServerLevel, entry: LegendarySpawnEntry): Boolean =
        entry.times.isEmpty() || entry.times.any {
            it.equals("any", true) || it.equals(if (level.isDay) "day" else "night", true)
        }

    private fun <T> weighted(values: List<T>, weight: (T) -> Double, random: RandomSource): T? {
        val positive = values.map { it to weight(it).coerceAtLeast(0.0) }.filter { it.second > 0.0 }
        val total = positive.sumOf { it.second }
        if (total <= 0.0) return null
        var roll = random.nextDouble() * total
        for ((value, valueWeight) in positive) {
            roll -= valueWeight
            if (roll < 0.0) return value
        }
        return positive.last().first
    }

    private data class DimensionCandidate(
        val level: ServerLevel,
        val id: String,
        val players: List<ServerPlayer>,
        val entries: List<LegendarySpawnEntry>,
        val weight: Double
    )
}

private class LegendarySpawnHistory : SavedData() {
    private val spawnedSpecies = mutableSetOf<String>()
    private val playerSpawnCounts = mutableMapOf<java.util.UUID, Int>()

    fun hasSpawned(species: String) = spawnedSpecies.contains(species.lowercase())
    fun playerSpawnCount(playerUuid: java.util.UUID) = playerSpawnCounts[playerUuid] ?: 0
    fun speciesCount() = spawnedSpecies.size
    fun markSpawned(species: String, playerUuid: java.util.UUID) {
        spawnedSpecies.add(species.lowercase())
        playerSpawnCounts[playerUuid] = playerSpawnCount(playerUuid) + 1
        setDirty()
    }

    fun reset() {
        spawnedSpecies.clear()
        playerSpawnCounts.clear()
        setDirty()
    }

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        tag.putString("species", spawnedSpecies.joinToString(","))
        val players = CompoundTag()
        playerSpawnCounts.forEach { (uuid, count) -> players.putInt(uuid.toString(), count) }
        tag.put("players", players)
        return tag
    }

    companion object {
        private const val DATA_NAME = "nbp_cobble_plus_legendary_spawns"
        private fun load(tag: CompoundTag, registries: HolderLookup.Provider) = LegendarySpawnHistory().also { data ->
            tag.getString("species").split(',').filter { it.isNotBlank() }.forEach { data.spawnedSpecies += it }
            val players = tag.getCompound("players")
            players.allKeys.forEach { key ->
                runCatching { java.util.UUID.fromString(key) }.getOrNull()?.let { uuid ->
                    data.playerSpawnCounts[uuid] = players.getInt(key)
                }
            }
        }
        private val FACTORY = Factory(::LegendarySpawnHistory, ::load, DataFixTypes.LEVEL)
        fun get(server: MinecraftServer): LegendarySpawnHistory =
            server.overworld().dataStorage.computeIfAbsent(FACTORY, DATA_NAME)
    }
}
