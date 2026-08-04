package com.nbp.cobbleplus.feature.impl

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.reactive.ObservableSubscription
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail
import com.cobblemon.mod.common.api.spawning.SpawnCause
import com.cobblemon.mod.common.api.spawning.spawner.SpawningZoneInput
import com.cobblemon.mod.common.api.spawning.position.calculators.SpawnablePositionCalculator
import com.cobblemon.mod.common.api.spawning.influence.SpawningInfluence
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition
import com.cobblemon.mod.common.pokemon.abilities.HiddenAbility
import com.cobblemon.mod.common.util.spawner
import com.nbp.cobbleplus.config.NbpConfig
import com.nbp.cobbleplus.config.ServerEventsConfig
import com.nbp.cobbleplus.feature.FeatureModule
import com.nbp.cobbleplus.i18n.PlayerLanguage
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.random.Random

enum class ServerEventType(val key: String) {
    SHINY_BOOST("shiny_boost"),
    EXP_BOOST("exp_boost"),
    COBBLEDOLLARS_BOOST("cobbledollars_boost"),
    BOUNTY("bounty"),
    SAFARI_DISCOUNT("safari_discount"),
    CATCH_RATE_BOOST("catch_rate_boost"),
    HORDE_INVASION("horde_invasion"),
    PERFECT_IV_RUSH("perfect_iv_rush"),
    DOUBLE_LOOT("double_loot"),
    HIDDEN_ABILITY_OUTBREAK("hidden_ability_outbreak");
}

data class ActiveServerEvent(
    val type: ServerEventType,
    val startedAtTick: Long,
    val durationTicks: Long,
    val bountySpecies: String? = null,
    val bountyReward: Long = 0L,
    val bountyClaimedBy: UUID? = null,
    val hordeSpecies: String? = null
) {
    val ticksRemaining: Long get() = (startedAtTick + durationTicks) - ServerEventsFeature.serverTick
    val isExpired: Boolean get() = ServerEventsFeature.serverTick >= startedAtTick + durationTicks
    val minutesRemaining: Int get() = (ticksRemaining / 1200).toInt().coerceAtLeast(0)
    val secondsRemainingInMinute: Int get() = ((ticksRemaining % 1200) / 20).toInt().coerceAtLeast(0)
}

object ServerEventsFeature : FeatureModule {
    override val name: String = "Server Events"
    override val isEnabled: Boolean get() = NbpConfig.data.serverEvents.enabled

    private val logger = LoggerFactory.getLogger("NBP-ServerEvents")

    // Set of legendary species names from config (to exclude from bounty/horde)
    private val legendarySpeciesNames: Set<String> get() =
        NbpConfig.data.legendarySpawner.legendaryPool.map { it.species.lowercase() }.toSet()
    private val ultraBeastSpecies = setOf(
        "nihilego", "buzzwole", "pheromosa", "xurkitree", "celesteela", "kartana",
        "guzzlord", "blacephalon", "stakataka", "poipole", "naganadel"
    )

    var activeEvent: ActiveServerEvent? = null
        private set
    private var nextEventTick: Long = -1L
    var serverTick: Long = 0L
        private set
    private var server: MinecraftServer? = null
    private val subscriptions = mutableListOf<ObservableSubscription<*>>()
    private val hordeInfluencePlayers = mutableSetOf<UUID>()
    private val perfectIvStats: List<Stat> = listOf(
        Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED
    )

    // ─── Public Accessors for hooks ───────────────────────────────────────────
    val isShinyBoostActive: Boolean get() = activeEvent?.type == ServerEventType.SHINY_BOOST
    val isExpBoostActive: Boolean get() = activeEvent?.type == ServerEventType.EXP_BOOST
    val isCobbleDollarsBoostActive: Boolean get() = activeEvent?.type == ServerEventType.COBBLEDOLLARS_BOOST
    val isSafariDiscountActive: Boolean get() = activeEvent?.type == ServerEventType.SAFARI_DISCOUNT
    val isCatchRateBoostActive: Boolean get() = activeEvent?.type == ServerEventType.CATCH_RATE_BOOST
    val isHordeInvasionActive: Boolean get() = activeEvent?.type == ServerEventType.HORDE_INVASION
    val isPerfectIvRushActive: Boolean get() = activeEvent?.type == ServerEventType.PERFECT_IV_RUSH
    val isDoubleLootActive: Boolean get() = activeEvent?.type == ServerEventType.DOUBLE_LOOT
    val isHiddenAbilityOutbreakActive: Boolean get() = activeEvent?.type == ServerEventType.HIDDEN_ABILITY_OUTBREAK

    fun getSafariDiscountMultiplier(): Double = if (isSafariDiscountActive) 0.5 else 1.0
    fun getShinyMultiplier(): Double = if (isShinyBoostActive) 6.0 else 1.0
    fun getExpMultiplier(): Double = if (isExpBoostActive) 2.0 else 1.0
    fun getCobbleDollarsMultiplier(): Double = if (isCobbleDollarsBoostActive) 2.0 else 1.0
    fun getCatchRateMultiplier(): Float = if (isCatchRateBoostActive) 1.5f else 1.0f

    fun bindServer(sv: MinecraftServer) {
        server = sv
        serverTick = 0L
        nextEventTick = -1L
        activeEvent = null
    }
    fun unbindServer() {
        removeHordeInfluences()
        server = null
        activeEvent = null
        nextEventTick = -1L
    }

    override fun onEnable() {
        if (subscriptions.isNotEmpty()) return
        registerListeners()
    }

    override fun onDisable() {
        subscriptions.forEach { it.unsubscribe() }
        subscriptions.clear()
        removeHordeInfluences()
        activeEvent = null
        nextEventTick = -1L
    }

    private fun registerListeners() {
        subscriptions += CobblemonEvents.EXPERIENCE_GAINED_EVENT_PRE.subscribe { event ->
                if (!isExpBoostActive) return@subscribe
                event.experience = (event.experience * getExpMultiplier()).toInt().coerceAtLeast(0)
        }
        subscriptions += CobblemonEvents.SHINY_CHANCE_CALCULATION.subscribe { event ->
            event.addModificationFunction { chance, _, _ ->
                val eventMultiplier = if (isShinyBoostActive) getShinyMultiplier() else 1.0
                val hordeMultiplier = hordeShinyMultiplierFor(event.pokemon.species.resourceIdentifier.path)
                val multiplier = eventMultiplier * hordeMultiplier
                if (multiplier <= 1.0) chance
                else if (chance > 1f) (chance / multiplier.toFloat()).coerceAtLeast(1f)
                else (chance * multiplier.toFloat()).coerceAtMost(1f)
            }
        }
        subscriptions += CobblemonEvents.POKEMON_CATCH_RATE.subscribe { event ->
            if (isCatchRateBoostActive) event.catchRate *= getCatchRateMultiplier()
        }
        subscriptions += CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe { event ->
            val pokemon = event.entity.pokemon
            applyPersonalShinyBoost(event)
            if (isPerfectIvRushActive) guaranteePerfectIvs(pokemon, 3)
            if (isHiddenAbilityOutbreakActive) applyHiddenAbilityBoost(pokemon)
        }
        subscriptions += CobblemonEvents.LOOT_DROPPED.subscribe { event ->
            if (isDoubleLootActive && event.entity != null) event.drops.addAll(event.drops.toList())
        }
        registerBountyListener()
    }

    // ─── Main Tick ────────────────────────────────────────────────────────────
    fun tick(sv: MinecraftServer) {
        if (!isEnabled) return
        serverTick++
        sv.playerList.players.forEach(::attachHordeInfluence)
        if (isHordeInvasionActive && serverTick % 100L == 0L) spawnHordePokemon(sv)

        // Check if current event expired
        activeEvent?.let { ev ->
            if (ev.isExpired) {
                endEvent(sv, ev)
                activeEvent = null
                scheduleNextEvent()
            }
        }

        // Schedule first event
        if (nextEventTick < 0L) scheduleNextEvent()

        // Trigger next event
        if (activeEvent == null && nextEventTick > 0L && serverTick >= nextEventTick) {
            triggerRandomEvent(sv)
        }
    }

    // ─── Schedule ─────────────────────────────────────────────────────────────
    private fun scheduleNextEvent() {
        val cfg = NbpConfig.data.serverEvents
        val minTicks = cfg.minIntervalMinutes * 1200L
        val maxTicks = cfg.maxIntervalMinutes * 1200L
        val randomDelay = Random.nextLong(minTicks, maxTicks + 1)
        nextEventTick = serverTick + randomDelay
        val mins = (randomDelay / 1200).toInt()
        logger.info("[ServerEvents] Next event scheduled in ~$mins minutes")
    }

    // ─── Trigger ──────────────────────────────────────────────────────────────
    fun triggerRandomEvent(sv: MinecraftServer) {
        val type = ServerEventType.values().random()
        triggerEvent(sv, type)
    }

    fun triggerEvent(sv: MinecraftServer, type: ServerEventType) {
        val cfg = NbpConfig.data.serverEvents
        val durationMinutes = if (type == ServerEventType.HORDE_INVASION) {
            cfg.hordeInvasionDurationMinutes.coerceAtLeast(1)
        } else {
            cfg.eventDurationMinutes.coerceAtLeast(1)
        }
        val durationTicks = durationMinutes * 1200L
        val legends = legendarySpeciesNames

        val bountySpecies: String?
        val bountyReward: Long
        val hordeSpecies: String?

        when (type) {
            ServerEventType.BOUNTY -> {
                val species = PokemonSpecies.implemented
                    .filter { sp -> sp.catchRate > 0 && sp.resourceIdentifier.path !in legends }
                    .randomOrNull()
                bountySpecies = species?.resourceIdentifier?.toString()
                val catchRate = species?.catchRate ?: 150
                bountyReward = calculateBountyReward(catchRate, cfg)
                hordeSpecies = null
            }
            ServerEventType.HORDE_INVASION -> {
                bountySpecies = null
                bountyReward = 0L
                hordeSpecies = PokemonSpecies.implemented
                    .filter { sp ->
                        sp.catchRate > 100 &&
                            sp.resourceIdentifier.path.lowercase() !in legends &&
                            sp.resourceIdentifier.path.lowercase() !in ultraBeastSpecies
                    }
                    .randomOrNull()?.resourceIdentifier?.toString()
            }
            else -> {
                bountySpecies = null
                bountyReward = 0L
                hordeSpecies = null
            }
        }

        // Cancel any currently active event before starting the new one
        activeEvent?.let { previous ->
            broadcastToAll(sv, PlayerLanguage.string(null, "event.cancelled", "event" to PlayerLanguage.string(null, "event.name.${previous.type.key}")))
            logger.info("[ServerEvents] Event cancelled (replaced): ${previous.type.key}")
        }

        activeEvent = ActiveServerEvent(
            type = type,
            startedAtTick = serverTick,
            durationTicks = durationTicks,
            bountySpecies = bountySpecies,
            bountyReward = bountyReward,
            hordeSpecies = hordeSpecies
        )
        nextEventTick = -1L

        announceEventStart(sv, activeEvent!!)
        logger.info("[ServerEvents] Event started: ${type.key}")
    }

    fun stopEvent(sv: MinecraftServer): Boolean {
        val event = activeEvent ?: return false
        endEvent(sv, event)
        activeEvent = null
        scheduleNextEvent()
        logger.info("[ServerEvents] Event stopped by command: ${event.type.key}")
        return true
    }

    // ─── End Event ────────────────────────────────────────────────────────────
    private fun endEvent(sv: MinecraftServer, ev: ActiveServerEvent) {
        broadcastToAll(sv, PlayerLanguage.string(null, "event.end.${ev.type.key}"))
        if (ev.type == ServerEventType.BOUNTY && ev.bountyClaimedBy == null) {
            broadcastToAll(sv, PlayerLanguage.string(null, "event.bounty.unclaimed"))
        }
        logger.info("[ServerEvents] Event ended: ${ev.type.key}")
    }

    // ─── Announcements ────────────────────────────────────────────────────────
    private fun announceEventStart(sv: MinecraftServer, ev: ActiveServerEvent) {
        val cfg = NbpConfig.data.serverEvents
        val durationMinutes = (ev.durationTicks / 1200L).toInt().coerceAtLeast(1)
        sv.playerList.players.forEach { player ->
            val titleStr = when (ev.type) {
                ServerEventType.BOUNTY -> PlayerLanguage.string(player, "event.title.${ev.type.key}",
                    "pokemon" to (ev.bountySpecies?.substringAfterLast(":") ?: "???").replaceFirstChar { it.uppercase() })
                ServerEventType.HORDE_INVASION -> PlayerLanguage.string(player, "event.title.${ev.type.key}",
                    "pokemon" to (ev.hordeSpecies?.substringAfterLast(":") ?: "???").replaceFirstChar { it.uppercase() })
                else -> PlayerLanguage.string(player, "event.title.${ev.type.key}")
            }
            val subtitleStr = when (ev.type) {
                ServerEventType.BOUNTY -> PlayerLanguage.string(player, "event.subtitle.${ev.type.key}",
                    "reward" to ev.bountyReward, "duration" to durationMinutes)
                else -> PlayerLanguage.string(player, "event.subtitle.${ev.type.key}",
                    "duration" to durationMinutes)
            }

            if (cfg.announceWithScreenTitle) {
                player.connection.send(ClientboundSetTitlesAnimationPacket(10, 60, 20))
                player.connection.send(ClientboundSetTitleTextPacket(Component.literal(titleStr)))
                player.connection.send(ClientboundSetSubtitleTextPacket(Component.literal(subtitleStr)))
            }

            val chatMsg = when (ev.type) {
                ServerEventType.BOUNTY -> PlayerLanguage.string(player, "event.chat.${ev.type.key}",
                    "pokemon" to (ev.bountySpecies?.substringAfterLast(":") ?: "???").replaceFirstChar { it.uppercase() },
                    "reward" to ev.bountyReward, "duration" to durationMinutes)
                ServerEventType.HORDE_INVASION -> PlayerLanguage.string(player, "event.chat.${ev.type.key}",
                    "pokemon" to (ev.hordeSpecies?.substringAfterLast(":") ?: "???").replaceFirstChar { it.uppercase() },
                    "duration" to durationMinutes)
                else -> PlayerLanguage.string(player, "event.chat.${ev.type.key}", "duration" to durationMinutes)
            }
            player.sendSystemMessage(Component.literal(chatMsg))
        }
    }

    private fun broadcastToAll(sv: MinecraftServer, message: String) {
        sv.playerList.players.forEach { it.sendSystemMessage(Component.literal(message)) }
    }

    // ─── Bounty Listener ──────────────────────────────────────────────────────
    private fun registerBountyListener() {
        subscriptions += CobblemonEvents.POKEMON_CAPTURED.subscribe { event ->
                val ev = activeEvent ?: return@subscribe
                if (ev.type != ServerEventType.BOUNTY || ev.bountyClaimedBy != null) return@subscribe
                val bountyName = ev.bountySpecies?.substringAfterLast(":") ?: return@subscribe
                val caughtName = event.pokemon.species.resourceIdentifier.path
                if (!caughtName.equals(bountyName, ignoreCase = true)) return@subscribe

                val sv = server ?: return@subscribe
                val playerEntity = sv.playerList.getPlayer(event.player.uuid) ?: return@subscribe

                // Credit bounty reward bypassing daily cap
                creditBountyReward(playerEntity, ev.bountyReward)
                activeEvent = ev.copy(bountyClaimedBy = playerEntity.uuid)

                val winMsg = PlayerLanguage.string(null, "event.bounty.claimed",
                    "player" to playerEntity.scoreboardName,
                    "pokemon" to bountyName.replaceFirstChar { it.uppercase() },
                    "reward" to ev.bountyReward)
                broadcastToAll(sv, winMsg)

                if (NbpConfig.data.serverEvents.announceWithScreenTitle) {
                    playerEntity.connection.send(ClientboundSetTitlesAnimationPacket(10, 60, 20))
                    playerEntity.connection.send(ClientboundSetTitleTextPacket(Component.literal("§6§l★ BOUNTY CLAIMED ★")))
                    playerEntity.connection.send(ClientboundSetSubtitleTextPacket(Component.literal("§e+${ev.bountyReward} CobbleDollars")))
                }

                logger.info("[ServerEvents] Bounty claimed by ${playerEntity.scoreboardName} for $bountyName (+${ev.bountyReward})")
        }
    }

    private fun guaranteePerfectIvs(pokemon: com.cobblemon.mod.common.pokemon.Pokemon, amount: Int) {
        val available = perfectIvStats.filter { pokemon.ivs.getOrDefault(it) < 31 }
        val needed = (amount - (perfectIvStats.size - available.size)).coerceAtLeast(0)
        available.shuffled(Random(pokemon.uuid.hashCode())).take(needed).forEach { pokemon.setIV(it, 31) }
    }

    private fun applyPersonalShinyBoost(event: com.cobblemon.mod.common.api.events.entity.SpawnEvent<com.cobblemon.mod.common.entity.pokemon.PokemonEntity>) {
        val player = event.spawnablePosition.cause.entity as? ServerPlayer ?: return
        val combo = CatchComboFeature.currentShinyMultiplier(
            player,
            event.entity.pokemon.species.resourceIdentifier.path
        )
        if (combo <= 1.0 || event.entity.pokemon.shiny) return

        val base = Cobblemon.config.shinyRate.toDouble().coerceAtLeast(1.0)
        var existingFactor = getShinyMultiplier()
        existingFactor *= hordeShinyMultiplierFor(event.entity.pokemon.species.resourceIdentifier.path)
        val safari = player.level().dimension().location().toString().startsWith("nbp_cobble_plus:safari")
        if (safari && NbpConfig.data.safariZone.enabled) {
            existingFactor *= NbpConfig.data.safariZone.shinyMultiplier.coerceAtLeast(1.0)
        }
        val currentChance = (existingFactor / base).coerceIn(0.0, 1.0)
        val targetChance = (existingFactor * combo / base).coerceIn(0.0, 1.0)
        val conditional = ((targetChance - currentChance) / (1.0 - currentChance)).coerceIn(0.0, 1.0)
        if (Random.nextDouble() < conditional) event.entity.pokemon.shiny = true
    }

    private fun applyHiddenAbilityBoost(pokemon: com.cobblemon.mod.common.pokemon.Pokemon) {
        if (pokemon.ability.priority == Priority.LOW) return
        val hidden = pokemon.form.abilities.mapping[Priority.LOW]
            ?.filterIsInstance<HiddenAbility>()?.randomOrNull() ?: return
        val baseChance = NbpConfig.data.serverEvents.hiddenAbilityBaseChance.coerceIn(0.0, 1.0)
        val extraChance = (baseChance * 4.0 / (1.0 - baseChance).coerceAtLeast(0.000001))
        if (Random.nextDouble() < extraChance.coerceAtMost(1.0)) {
            pokemon.updateAbility(hidden.template.create(false))
        }
    }

    private fun attachHordeInfluence(player: ServerPlayer) {
        if (!hordeInfluencePlayers.add(player.uuid)) return
        runCatching {
            @Suppress("UNCHECKED_CAST")
            val influences = player.spawner.influences as MutableList<SpawningInfluence>
            influences += HordeSpawningInfluence
        }.onFailure {
            hordeInfluencePlayers.remove(player.uuid)
            logger.warn("[ServerEvents] Could not attach horde influence to ${player.scoreboardName}: ${it.message}")
        }
    }

    private fun removeHordeInfluences() {
        server?.playerList?.players?.forEach { player ->
            runCatching {
                @Suppress("UNCHECKED_CAST")
                val influences = player.spawner.influences as MutableList<SpawningInfluence>
                influences.removeAll { it === HordeSpawningInfluence }
            }
        }
        hordeInfluencePlayers.clear()
    }

    private object HordeSpawningInfluence : SpawningInfluence {
        override fun affectWeight(detail: com.cobblemon.mod.common.api.spawning.detail.SpawnDetail, spawnablePosition: SpawnablePosition, weight: Float): Float {
            if (!isHordeInvasionActive) return weight
            val species = (detail as? PokemonSpawnDetail)?.pokemon?.species ?: return weight
            val target = activeEvent?.hordeSpecies?.substringAfterLast(":") ?: return weight
            return if (species.substringAfterLast(":").equals(target, ignoreCase = true)) weight * 12f else weight
        }
    }

    private fun spawnHordePokemon(sv: MinecraftServer) {
        val species = activeEvent?.hordeSpecies?.substringAfterLast(":") ?: return
        if (species.lowercase() in legendarySpeciesNames || species.lowercase() in ultraBeastSpecies) return
        sv.playerList.players.forEach { player ->
            val nearby = player.serverLevel().getEntitiesOfClass(
                com.cobblemon.mod.common.entity.pokemon.PokemonEntity::class.java,
                player.boundingBox.inflate(24.0)
            ).count { it.pokemon.species.resourceIdentifier.path.equals(species, true) }
            if (nearby >= 6) return@forEach
            repeat(2) {
                val x = player.x + Random.nextDouble(-12.0, 13.0)
                val z = player.z + Random.nextDouble(-12.0, 13.0)
                val blockX = x.toInt()
                val blockZ = z.toInt()
                val blockY = player.serverLevel().getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                    blockX,
                    blockZ
                )
                val pos = net.minecraft.core.BlockPos(blockX, blockY, blockZ)
                runCatching {
                    val level = normalSpawnLevel(player, species)
                    val entity = PokemonProperties.parse("species=$species level=$level").createEntity(player.serverLevel())
                    entity.moveTo(pos.x + .5, pos.y.toDouble(), pos.z + .5, Random.nextFloat() * 360f, 0f)
                    entity.setPersistenceRequired()
                    player.serverLevel().addFreshEntity(entity)
                }.onFailure { logger.warn("[ServerEvents] Failed to spawn horde $species near ${player.scoreboardName}: ${it.message}") }
            }
        }
    }

    private fun hordeShinyMultiplierFor(species: String): Double {
        val event = activeEvent ?: return 1.0
        if (event.type != ServerEventType.HORDE_INVASION) return 1.0
        val target = event.hordeSpecies?.substringAfterLast(":") ?: return 1.0
        return if (species.substringAfterLast(":").equals(target, ignoreCase = true)) {
            NbpConfig.data.serverEvents.hordeShinyMultiplier.coerceAtLeast(1.0)
        } else 1.0
    }

    private fun normalSpawnLevel(player: ServerPlayer, species: String): Int {
        return runCatching {
            val spawner = player.spawner
            val cause = SpawnCause(spawner, player)
            val config = Cobblemon.config
            val zone = Cobblemon.spawningZoneGenerator.generate(
                spawner,
                SpawningZoneInput(
                    cause,
                    player.serverLevel(),
                    (player.x - config.spawningZoneDiameter / 2f).toInt(),
                    (player.y - config.spawningZoneHeight / 2f).toInt(),
                    (player.z - config.spawningZoneDiameter / 2f).toInt(),
                    config.spawningZoneDiameter,
                    config.spawningZoneHeight,
                    config.spawningZoneDiameter
                )
            )
            val positions = spawner.resolver.resolve(
                spawner,
                SpawnablePositionCalculator.prioritizedAreaCalculators,
                zone
            )
            val target = species.substringAfterLast(":")
            val detail = Cobblemon.bestSpawner.config.buckets.asSequence()
                .flatMap { bucket -> positions.asSequence().flatMap { position -> spawner.getMatchingSpawns(bucket, position).asSequence() } }
                .filterIsInstance<PokemonSpawnDetail>()
                .firstOrNull { it.pokemon.species?.substringAfterLast(":")?.equals(target, true) == true }
                ?: return@runCatching 20
            detail.pokemon.deriveLevelRange(detail.levelRange).random()
        }.getOrDefault(20).coerceIn(1, Cobblemon.config.maxPokemonLevel)
    }

    fun shinyOddsDenominator(player: ServerPlayer): Int {
        var multiplier = getShinyMultiplier()
        multiplier *= CatchComboFeature.currentShinyMultiplier(player)
        val inSafari = player.level().dimension().location().toString().startsWith("nbp_cobble_plus:safari")
        if (inSafari && NbpConfig.data.safariZone.enabled) multiplier *= NbpConfig.data.safariZone.shinyMultiplier
        return (Cobblemon.config.shinyRate.toDouble().coerceAtLeast(1.0) / multiplier.coerceAtLeast(1.0))
            .toInt().coerceAtLeast(1)
    }

    fun getShinyStatusMessages(player: ServerPlayer): List<Component> {
        val messages = mutableListOf<Component>()
        val baseRate = Cobblemon.config.shinyRate.toDouble().coerceAtLeast(1.0).toInt()
        messages += PlayerLanguage.text(player, "shiny.chance", "chance" to shinyOddsDenominator(player))
        messages += PlayerLanguage.text(player, "shiny.base", "chance" to baseRate)

        if (isShinyBoostActive) {
            messages += PlayerLanguage.text(player, "shiny.source.event", "multiplier" to formatMultiplier(getShinyMultiplier()))
        }

        CatchComboFeature.currentShinyBonus(player)?.let { bonus ->
            messages += PlayerLanguage.text(player, "shiny.source.combo",
                "pokemon" to bonus.species,
                "count" to bonus.count,
                "multiplier" to formatMultiplier(bonus.multiplier))
        }

        if (player.level().dimension().location().toString().startsWith("nbp_cobble_plus:safari") &&
            NbpConfig.data.safariZone.enabled && NbpConfig.data.safariZone.shinyMultiplier > 1.0) {
            messages += PlayerLanguage.text(player, "shiny.source.safari",
                "multiplier" to formatMultiplier(NbpConfig.data.safariZone.shinyMultiplier))
        }

        if (messages.size == 2) messages += PlayerLanguage.text(player, "shiny.source.none")
        return messages
    }

    private fun formatMultiplier(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else "%.2f".format(value)

    private fun creditBountyReward(player: ServerPlayer, amount: Long) {
        val credited = CobbleDollarsBridge.earn(player, amount, false)
        logger.info("[ServerEvents] Credited $credited CobbleDollars to ${player.scoreboardName} for bounty reward ($amount requested).")
    }

    fun handleBountyClaim(player: ServerPlayer) {
        val ev = activeEvent
        if (ev == null || ev.type != ServerEventType.BOUNTY) {
            player.sendSystemMessage(PlayerLanguage.text(player, "event.bounty.no_active"))
            return
        }
        if (ev.bountyClaimedBy != null) {
            player.sendSystemMessage(PlayerLanguage.text(player, "event.bounty.already_claimed"))
            return
        }
        player.sendSystemMessage(PlayerLanguage.text(player, "event.bounty.capture_first",
            "pokemon" to (ev.bountySpecies?.substringAfterLast(":") ?: "???")))
    }

    // ─── Bounty Reward Calculation ────────────────────────────────────────────
    private fun calculateBountyReward(catchRate: Int, cfg: ServerEventsConfig): Long {
        return when {
            catchRate >= 200 -> Random.nextLong(cfg.bountyRewardCommonMin, cfg.bountyRewardCommonMax + 1)
            catchRate >= 100 -> Random.nextLong(cfg.bountyRewardUncommonMin, cfg.bountyRewardUncommonMax + 1)
            catchRate >= 45  -> Random.nextLong(cfg.bountyRewardRareMin, cfg.bountyRewardRareMax + 1)
            else             -> Random.nextLong(cfg.bountyRewardUltraRareMin, cfg.bountyRewardUltraRareMax + 1)
        }
    }

    // ─── Status for /event ────────────────────────────────────────────────────
    fun getStatusMessage(player: ServerPlayer): String {
        val ev = activeEvent
        if (ev == null) {
            val minsUntilNext = if (nextEventTick > 0L) ((nextEventTick - serverTick) / 1200L).coerceAtLeast(0L) else 0L
            return PlayerLanguage.string(player, "event.no_active", "minutes" to minsUntilNext)
        }
        val eventName = PlayerLanguage.string(player, "event.name.${ev.type.key}")
        return when (ev.type) {
            ServerEventType.BOUNTY -> PlayerLanguage.string(player, "event.status.bounty",
                "event" to eventName,
                "pokemon" to (ev.bountySpecies?.substringAfterLast(":") ?: "???").replaceFirstChar { it.uppercase() },
                "reward" to ev.bountyReward,
                "minutes" to ev.minutesRemaining,
                "seconds" to ev.secondsRemainingInMinute,
                "claimed" to if (ev.bountyClaimedBy != null) "YES" else "NO")
            else -> PlayerLanguage.string(player, "event.status",
                "event" to eventName,
                "minutes" to ev.minutesRemaining,
                "seconds" to ev.secondsRemainingInMinute)
        }
    }
}
