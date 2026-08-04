package com.nbp.cobbleplus.feature.impl

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.reactive.ObservableSubscription
import com.cobblemon.mod.common.api.spawning.SpawnBucket
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail
import com.cobblemon.mod.common.api.spawning.influence.SpawningInfluence
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition
import com.cobblemon.mod.common.util.spawner
import com.nbp.cobbleplus.config.NbpConfig
import com.nbp.cobbleplus.config.SafariZoneConfig
import com.nbp.cobbleplus.feature.FeatureModule
import com.nbp.cobbleplus.i18n.PlayerLanguage
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.saveddata.SavedData
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.random.Random

data class SafariBiomeOption(
    val slot: Int,
    val biomeKey: String,
    val translationKey: String,
    val item: Item,
    val dimensionName: String,
    val biomeFilterKeys: List<String>,
    val defaultPrice: Long = 2500L
) {
    fun getEffectivePrice(): Long {
        return NbpConfig.data.safariZone.biomePrices[biomeKey] ?: defaultPrice
    }
}

object SafariZoneFeature : FeatureModule {
    override val name: String = "Safari Zone"
    override val isEnabled: Boolean get() = NbpConfig.data.safariZone.enabled

    private val logger = LoggerFactory.getLogger("NBP-SafariZone")
    private var spawnSub: ObservableSubscription<*>? = null

    private val STATS_LIST: List<Stat> = listOf(
        Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED
    )

    private val activeInfluences = mutableMapOf<UUID, SafariSpawningInfluence>()

    // PÁGINA 1: Biomas do Overworld e Cavernas (2500 CobbleDollars por padrão)
    val PAGE_1_OPTIONS: Map<Int, SafariBiomeOption> = listOf(
        // Linha 1
        SafariBiomeOption(1, "plains", "safari.biome.plains", Items.GRASS_BLOCK, "nbp_cobble_plus:safari_plains", listOf("plains"), 2500L),
        SafariBiomeOption(2, "sunflower_plains", "safari.biome.sunflower_plains", Items.SUNFLOWER, "nbp_cobble_plus:safari_sunflower_plains", listOf("sunflower_plains"), 2500L),
        SafariBiomeOption(3, "forest", "safari.biome.forest", Items.OAK_SAPLING, "nbp_cobble_plus:safari_forest", listOf("forest"), 2500L),
        SafariBiomeOption(4, "flower_forest", "safari.biome.flower_forest", Items.ROSE_BUSH, "nbp_cobble_plus:safari_flower_forest", listOf("flower_forest"), 2500L),
        SafariBiomeOption(5, "birch_forest", "safari.biome.birch_forest", Items.BIRCH_SAPLING, "nbp_cobble_plus:safari_birch_forest", listOf("birch_forest", "old_growth_birch_forest"), 2500L),
        SafariBiomeOption(6, "dark_forest", "safari.biome.dark_forest", Items.DARK_OAK_SAPLING, "nbp_cobble_plus:safari_dark_forest", listOf("dark_forest"), 2500L),
        SafariBiomeOption(7, "taiga", "safari.biome.taiga", Items.SPRUCE_SAPLING, "nbp_cobble_plus:safari_taiga", listOf("taiga", "old_growth_spruce_taiga", "old_growth_pine_taiga"), 2500L),

        // Linha 2
        SafariBiomeOption(10, "snowy_taiga", "safari.biome.snowy_taiga", Items.SNOW, "nbp_cobble_plus:safari_snowy_taiga", listOf("snowy_taiga"), 2500L),
        SafariBiomeOption(11, "jungle", "safari.biome.jungle", Items.JUNGLE_SAPLING, "nbp_cobble_plus:safari_jungle", listOf("jungle", "sparse_jungle"), 2500L),
        SafariBiomeOption(12, "bamboo_jungle", "safari.biome.bamboo_jungle", Items.BAMBOO, "nbp_cobble_plus:safari_bamboo_jungle", listOf("bamboo_jungle"), 2500L),
        SafariBiomeOption(13, "savanna", "safari.biome.savanna", Items.ACACIA_SAPLING, "nbp_cobble_plus:safari_savanna", listOf("savanna", "savanna_plateau", "windswept_savanna"), 2500L),
        SafariBiomeOption(14, "desert", "safari.biome.desert", Items.CACTUS, "nbp_cobble_plus:safari_desert", listOf("desert"), 2500L),
        SafariBiomeOption(15, "badlands", "safari.biome.badlands", Items.TERRACOTTA, "nbp_cobble_plus:safari_badlands", listOf("badlands", "wooded_badlands", "eroded_badlands"), 2500L),
        SafariBiomeOption(16, "swamp", "safari.biome.swamp", Items.LILY_PAD, "nbp_cobble_plus:safari_swamp", listOf("swamp"), 2500L),

        // Linha 3
        SafariBiomeOption(19, "mangrove_swamp", "safari.biome.mangrove_swamp", Items.MANGROVE_PROPAGULE, "nbp_cobble_plus:safari_mangrove_swamp", listOf("mangrove_swamp"), 2500L),
        SafariBiomeOption(20, "cherry_grove", "safari.biome.cherry_grove", Items.CHERRY_SAPLING, "nbp_cobble_plus:safari_cherry_grove", listOf("cherry_grove"), 2500L),
        SafariBiomeOption(21, "meadow", "safari.biome.meadow", Items.ALLIUM, "nbp_cobble_plus:safari_meadow", listOf("meadow"), 2500L),
        SafariBiomeOption(22, "grove", "safari.biome.grove", Items.SNOW_BLOCK, "nbp_cobble_plus:safari_grove", listOf("grove"), 2500L),
        SafariBiomeOption(23, "snowy_slopes", "safari.biome.snowy_slopes", Items.POWDER_SNOW_BUCKET, "nbp_cobble_plus:safari_snowy_slopes", listOf("snowy_slopes"), 2500L),
        SafariBiomeOption(24, "jagged_peaks", "safari.biome.jagged_peaks", Items.PACKED_ICE, "nbp_cobble_plus:safari_jagged_peaks", listOf("jagged_peaks"), 2500L),
        SafariBiomeOption(25, "frozen_peaks", "safari.biome.frozen_peaks", Items.BLUE_ICE, "nbp_cobble_plus:safari_frozen_peaks", listOf("frozen_peaks"), 2500L),

        // Linha 4
        SafariBiomeOption(28, "stony_peaks", "safari.biome.stony_peaks", Items.CALCITE, "nbp_cobble_plus:safari_stony_peaks", listOf("stony_peaks"), 2500L),
        SafariBiomeOption(29, "windswept_hills", "safari.biome.windswept_hills", Items.EMERALD_ORE, "nbp_cobble_plus:safari_windswept_hills", listOf("windswept_hills", "windswept_forest"), 2500L),
        SafariBiomeOption(30, "ocean", "safari.biome.ocean", Items.WATER_BUCKET, "nbp_cobble_plus:safari_ocean", listOf("ocean", "deep_ocean"), 2500L),
        SafariBiomeOption(31, "warm_ocean", "safari.biome.warm_ocean", Items.BRAIN_CORAL, "nbp_cobble_plus:safari_warm_ocean", listOf("warm_ocean", "lukewarm_ocean"), 2500L),
        SafariBiomeOption(32, "frozen_ocean", "safari.biome.frozen_ocean", Items.ICE, "nbp_cobble_plus:safari_frozen_ocean", listOf("frozen_ocean", "deep_frozen_ocean"), 2500L),
        SafariBiomeOption(33, "beach", "safari.biome.beach", Items.SAND, "nbp_cobble_plus:safari_beach", listOf("beach"), 2500L),
        SafariBiomeOption(34, "snowy_beach", "safari.biome.snowy_beach", Items.SNOW_BLOCK, "nbp_cobble_plus:safari_snowy_beach", listOf("snowy_beach"), 2500L),

        // Linha 5
        SafariBiomeOption(37, "stony_shore", "safari.biome.stony_shore", Items.GRAVEL, "nbp_cobble_plus:safari_stony_shore", listOf("stony_shore"), 2500L),
        SafariBiomeOption(38, "river", "safari.biome.river", Items.SUGAR_CANE, "nbp_cobble_plus:safari_river", listOf("river", "frozen_river"), 2500L),
        SafariBiomeOption(39, "mushroom_fields", "safari.biome.mushroom_fields", Items.RED_MUSHROOM_BLOCK, "nbp_cobble_plus:safari_mushroom_fields", listOf("mushroom_fields"), 2500L),
        SafariBiomeOption(40, "lush_caves", "safari.biome.lush_caves", Items.MOSS_BLOCK, "nbp_cobble_plus:safari_lush_caves", listOf("lush_caves"), 2500L),
        SafariBiomeOption(41, "dripstone_caves", "safari.biome.dripstone_caves", Items.POINTED_DRIPSTONE, "nbp_cobble_plus:safari_dripstone_caves", listOf("dripstone_caves"), 2500L),

        // Linha 6 (Destaque Central)
        SafariBiomeOption(49, "random", "safari.biome.random", Items.COMPASS, "nbp_cobble_plus:safari_zone", emptyList(), 2500L)
    ).associateBy { it.slot }

    // PÁGINA 2: Biomas do Nether e End (3500 CobbleDollars por padrão)
    val PAGE_2_OPTIONS: Map<Int, SafariBiomeOption> = listOf(
        // Linha 2: NETHER
        SafariBiomeOption(10, "nether_wastes", "safari.biome.nether_wastes", Items.NETHERRACK, "nbp_cobble_plus:safari_nether_wastes", listOf("nether_wastes"), 3500L),
        SafariBiomeOption(11, "crimson_forest", "safari.biome.crimson_forest", Items.CRIMSON_NYLIUM, "nbp_cobble_plus:safari_crimson_forest", listOf("crimson_forest"), 3500L),
        SafariBiomeOption(12, "warped_forest", "safari.biome.warped_forest", Items.WARPED_NYLIUM, "nbp_cobble_plus:safari_warped_forest", listOf("warped_forest"), 3500L),
        SafariBiomeOption(13, "soul_sand_valley", "safari.biome.soul_sand_valley", Items.SOUL_SAND, "nbp_cobble_plus:safari_soul_sand_valley", listOf("soul_sand_valley"), 3500L),
        SafariBiomeOption(14, "basalt_deltas", "safari.biome.basalt_deltas", Items.BASALT, "nbp_cobble_plus:safari_basalt_deltas", listOf("basalt_deltas"), 3500L),

        // Linha 4: THE END
        SafariBiomeOption(28, "the_end", "safari.biome.the_end", Items.END_STONE, "nbp_cobble_plus:safari_the_end", listOf("the_end"), 3500L),
        SafariBiomeOption(29, "end_highlands", "safari.biome.end_highlands", Items.END_STONE_BRICKS, "nbp_cobble_plus:safari_end_highlands", listOf("end_highlands"), 3500L),
        SafariBiomeOption(30, "end_midlands", "safari.biome.end_midlands", Items.PURPUR_BLOCK, "nbp_cobble_plus:safari_end_midlands", listOf("end_midlands"), 3500L),
        SafariBiomeOption(31, "end_barrens", "safari.biome.end_barrens", Items.CHORUS_FLOWER, "nbp_cobble_plus:safari_end_barrens", listOf("end_barrens"), 3500L),
        SafariBiomeOption(32, "small_end_islands", "safari.biome.small_end_islands", Items.POPPED_CHORUS_FRUIT, "nbp_cobble_plus:safari_small_end_islands", listOf("small_end_islands"), 3500L),

        // Linha 6 (Destaque Central)
        SafariBiomeOption(49, "random", "safari.biome.random", Items.COMPASS, "nbp_cobble_plus:safari_zone", emptyList(), 2500L)
    ).associateBy { it.slot }

    override fun onEnable() {
        if (spawnSub != null) return
        spawnSub = CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe { event ->
            if (!isEnabled) return@subscribe
            val entity = event.entity
            val level = entity.level()
            val dimId = level.dimension().location().toString()

            if (!dimId.startsWith("nbp_cobble_plus:safari")) return@subscribe

            val pokemon = entity.pokemon
            val config = NbpConfig.data.safariZone

            // Taxa de Shiny no Safari (4.0x por padrão)
            if (!pokemon.shiny && config.shinyMultiplier > 1.0) {
                val baseShinyRate = Cobblemon.config.shinyRate.toDouble().coerceAtLeast(1.0)
                val extraChance = (config.shinyMultiplier - 1.0) / (baseShinyRate - 1.0).coerceAtLeast(1.0)
                if (Random.nextDouble() < extraChance) {
                    pokemon.shiny = true
                }
            }

            // Garante pelo menos 2 IVs Perfeitos
            if (config.perfectIvCount > 0) {
                val count = config.perfectIvCount.coerceIn(1, 6)
                val ivs = pokemon.ivs
                val (perfect, available) = STATS_LIST.partition { ivs.getOrDefault(it) >= 31 }
                val needed = count - perfect.size
                if (needed > 0) {
                    available.shuffled().take(needed).forEach { stat ->
                        pokemon.setIV(stat, 31)
                    }
                }
            }
        }
    }

    override fun onDisable() {
        spawnSub?.unsubscribe()
        spawnSub = null
        activeInfluences.clear()
    }

    fun tick(server: MinecraftServer) {
        if (!isEnabled) return
        val state = SafariSavedData.get(server)
        val config = NbpConfig.data.safariZone

        // Proteção Global: expulsão imediata para quem entra sem pagar (/tpa) e proibição de camas
        server.playerList.players.forEach { p ->
            val dimId = p.level().dimension().location().toString()
            if (dimId.startsWith("nbp_cobble_plus:safari")) {
                if (!state.sessions.containsKey(p.uuid)) {
                    val overworld = server.overworld()
                    val spawnPos = overworld.sharedSpawnPos
                    teleportPlayer(p, overworld, spawnPos.x.toDouble() + 0.5, spawnPos.y.toDouble() + 1.0, spawnPos.z.toDouble() + 0.5, 0f, 0f)
                    p.sendSystemMessage(PlayerLanguage.text(p, "safari.unauthorized_entry"))
                } else if (p.isSleeping) {
                    p.stopSleepInBed(true, true)
                    p.sendSystemMessage(PlayerLanguage.text(p, "safari.beds_prohibited"))
                }
            }
        }

        val sessions = state.sessions
        if (sessions.isEmpty()) return

        val iterator = sessions.entries.iterator()
        var dirty = false

        while (iterator.hasNext()) {
            val (uuid, session) = iterator.next()
            val player = server.playerList.getPlayer(uuid)

            if (player == null) continue

            val currentDim = player.level().dimension().location().toString()
            if (!currentDim.startsWith("nbp_cobble_plus:safari")) {
                iterator.remove()
                removeSafariInfluence(player)
                dirty = true
                player.sendSystemMessage(PlayerLanguage.text(player, "safari.left_dimension"))
                continue
            }

            // Limite de Forcefield de 1.000 blocos por dimensão Safari
            val maxBoundary = config.randomSpawnMaxRadius.toDouble().coerceAtLeast(300.0).coerceAtMost(1000.0)
            if (kotlin.math.abs(player.x) > maxBoundary || kotlin.math.abs(player.z) > maxBoundary) {
                val clampedX = player.x.coerceIn(-maxBoundary + 5.0, maxBoundary - 5.0)
                val clampedZ = player.z.coerceIn(-maxBoundary + 5.0, maxBoundary - 5.0)
                val topY = player.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, clampedX.toInt(), clampedZ.toInt())
                teleportPlayer(player, player.serverLevel(), clampedX, topY.toDouble() + 1.0, clampedZ, player.yRot, player.xRot)
                player.sendSystemMessage(PlayerLanguage.text(player, "safari.boundary_reached"))
            }

            applySafariInfluence(player, config)

            session.remainingTicks--
            dirty = true

            if (server.tickCount % 20 == 0) {
                val remainingSeconds = (session.remainingTicks / 20).coerceAtLeast(0)
                val formattedTime = formatTime(remainingSeconds)
                val msg = PlayerLanguage.template(player, "safari.action_bar", config.tickActionBarFormat, "time" to formattedTime)
                player.displayClientMessage(Component.literal(msg), true)
            }

            if (session.remainingTicks <= 0) {
                iterator.remove()
                removeSafariInfluence(player)
                dirty = true
                exitSafariPlayer(server, player, session, config.exitMessage)
            }
        }

        if (dirty) {
            state.setDirty()
        }
    }

    fun openSafariGui(player: ServerPlayer, page: Int = 1) {
        if (!isEnabled) {
            player.sendSystemMessage(PlayerLanguage.text(player, "safari.disabled"))
            return
        }

        val config = NbpConfig.data.safariZone
        val titleKey = if (page == 2) "safari.gui_title_p2" else "safari.gui_title_p1"
        val titleText = PlayerLanguage.string(player, titleKey)

        val currentOptions = if (page == 2) PAGE_2_OPTIONS else PAGE_1_OPTIONS

        player.openMenu(SimpleMenuProvider({ containerId, playerInv, _ ->
            val container = SimpleContainer(54)
            val borderStack = ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
                set(DataComponents.CUSTOM_NAME, Component.literal(" ").setStyle(Style.EMPTY.withItalic(false)))
            }
            for (i in 0 until 54) {
                container.setItem(i, borderStack.copy())
            }

            // Popula os biomas da página atual
            currentOptions.values.forEach { opt ->
                val stack = ItemStack(opt.item)
                val nameComp = Component.literal(PlayerLanguage.string(player, opt.translationKey)).setStyle(Style.EMPTY.withItalic(false))
                val priceComp = Component.literal(PlayerLanguage.string(player, "safari.gui.price", "price" to opt.getEffectivePrice())).setStyle(Style.EMPTY.withItalic(false))
                val durationComp = Component.literal(PlayerLanguage.string(player, "safari.gui.duration", "minutes" to config.sessionDurationMinutes)).setStyle(Style.EMPTY.withItalic(false))
                val clickComp = Component.literal(PlayerLanguage.string(player, "safari.gui.click_to_enter")).setStyle(Style.EMPTY.withItalic(false))

                stack.set(DataComponents.CUSTOM_NAME, nameComp)
                stack.set(DataComponents.LORE, ItemLore(listOf(priceComp, durationComp, clickComp)))
                container.setItem(opt.slot, stack)
            }

            // Botão de navegação entre páginas (Seta)
            if (page == 1) {
                val nextPageStack = ItemStack(Items.ARROW).apply {
                    set(DataComponents.CUSTOM_NAME, Component.literal(PlayerLanguage.string(player, "safari.gui.next_page")).setStyle(Style.EMPTY.withItalic(false)))
                }
                container.setItem(53, nextPageStack)
            } else {
                val prevPageStack = ItemStack(Items.ARROW).apply {
                    set(DataComponents.CUSTOM_NAME, Component.literal(PlayerLanguage.string(player, "safari.gui.prev_page")).setStyle(Style.EMPTY.withItalic(false)))
                }
                container.setItem(45, prevPageStack)
            }

            SafariChestMenu(containerId, playerInv, container, page)
        }, Component.literal(titleText)))
    }

    fun handleGuiClick(player: ServerPlayer, slotNum: Int, page: Int = 1) {
        // Troca de página
        if (page == 1 && slotNum == 53) {
            openSafariGui(player, 2)
            return
        }
        if (page == 2 && slotNum == 45) {
            openSafariGui(player, 1)
            return
        }

        val currentOptions = if (page == 2) PAGE_2_OPTIONS else PAGE_1_OPTIONS
        val option = currentOptions[slotNum] ?: return
        player.closeContainer()
        enterSafariWithBiome(player, option)
    }

    private fun enterSafariWithBiome(player: ServerPlayer, option: SafariBiomeOption): Boolean {
        val server = player.server ?: return false
        val config = NbpConfig.data.safariZone
        val state = SafariSavedData.get(server)

        if (state.sessions.containsKey(player.uuid)) {
            player.sendSystemMessage(PlayerLanguage.text(player, "safari.active_session"))
            return false
        }

        val ticketPrice = option.getEffectivePrice()
        if (ticketPrice > 0) {
            val balance = CobbleDollarsBridge.balance(player)
            if (balance < java.math.BigInteger.valueOf(ticketPrice)) {
                player.sendSystemMessage(PlayerLanguage.text(player, "safari.insufficient_balance", "price" to ticketPrice))
                return false
            }
            val spent = CobbleDollarsBridge.spend(player, ticketPrice)
            if (!spent) {
                player.sendSystemMessage(PlayerLanguage.text(player, "safari.insufficient_balance", "price" to ticketPrice))
                return false
            }
        }

        val targetDimLocation = ResourceLocation.parse(option.dimensionName)
        val targetDimKey = ResourceKey.create(Registries.DIMENSION, targetDimLocation)
        val safariLevel = server.getLevel(targetDimKey) ?: run {
            logger.warn("Dimensão exclusiva '${option.dimensionName}' não encontrada. Usando 'nbp_cobble_plus:safari_zone' como fallback.")
            val fallbackKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(config.dimension))
            server.getLevel(fallbackKey) ?: server.overworld()
        }

        val session = SafariSession(
            remainingTicks = config.sessionDurationMinutes * 60 * 20L,
            returnDimension = player.level().dimension().location().toString(),
            returnX = player.x,
            returnY = player.y,
            returnZ = player.z,
            returnYaw = player.yRot,
            returnPitch = player.xRot
        )

        state.sessions[player.uuid] = session
        state.setDirty()

        // Sorteia uma localização aleatória dentro do limite de 1.000 blocos da dimensão exclusiva
        val randomPos = findSafeRandomSpawnPosInBiome(safariLevel, config.randomSpawnMinRadius, config.randomSpawnMaxRadius, option.biomeFilterKeys)

        teleportPlayer(player, safariLevel, randomPos.x + 0.5, randomPos.y + 1.0, randomPos.z + 0.5, player.yRot, player.xRot)
        applySafariInfluence(player, config)

        player.sendSystemMessage(PlayerLanguage.text(player, "safari.entered", "minutes" to config.sessionDurationMinutes))
        if (config.announceEntry) {
            server.playerList.players.forEach { p ->
                val msg = PlayerLanguage.template(p, "safari.entry_broadcast", config.entryMessage, "player" to player.scoreboardName)
                p.sendSystemMessage(Component.literal(msg))
            }
        }
        return true
    }

    fun exitSafari(player: ServerPlayer): Boolean {
        val server = player.server ?: return false
        val state = SafariSavedData.get(server)
        val session = state.sessions.remove(player.uuid) ?: run {
            player.sendSystemMessage(PlayerLanguage.text(player, "safari.no_active_session"))
            return false
        }
        state.setDirty()
        removeSafariInfluence(player)
        val exitMsg = PlayerLanguage.string(player, "safari.voluntarily_exited")
        exitSafariPlayer(server, player, session, exitMsg)
        return true
    }

    private fun applySafariInfluence(player: ServerPlayer, config: SafariZoneConfig) {
        if (activeInfluences.containsKey(player.uuid)) return
        val influence = SafariSpawningInfluence(
            config.uncommonSpawnMultiplier,
            config.rareSpawnMultiplier,
            config.ultraRareSpawnMultiplier
        )
        activeInfluences[player.uuid] = influence
        runCatching {
            player.spawner.influences.add(influence)
        }
    }

    private fun removeSafariInfluence(player: ServerPlayer) {
        val influence = activeInfluences.remove(player.uuid) ?: return
        runCatching {
            player.spawner.influences.remove(influence)
        }
    }

    private fun findSafeRandomSpawnPosInBiome(level: ServerLevel, minRadius: Int, maxRadius: Int, biomeFilterKeys: List<String>): BlockPos {
        if (biomeFilterKeys.isEmpty()) {
            return findSafeRandomSpawnPos(level, minRadius, maxRadius)
        }

        val maxR = maxRadius.coerceAtLeast(300).coerceAtMost(1000)

        for (attempt in 0 until 150) {
            val rx = Random.nextInt(-maxR, maxR)
            val rz = Random.nextInt(-maxR, maxR)

            val topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, rx, rz)
            val pos = BlockPos(rx, topY, rz)
            val holder = level.getBiome(pos)
            val path = holder.unwrapKey().map { it.location().path.lowercase() }.orElse("")

            if (biomeFilterKeys.any { key -> path.contains(key.lowercase()) }) {
                val stateBelow = level.getBlockState(pos.below())
                if (!stateBelow.isAir && stateBelow.fluidState.isEmpty) {
                    return pos
                }
            }
        }

        return findSafeRandomSpawnPos(level, minRadius, maxRadius)
    }

    private fun findSafeRandomSpawnPos(level: ServerLevel, minRadius: Int, maxRadius: Int): BlockPos {
        val maxR = maxRadius.coerceAtLeast(300).coerceAtMost(1000)

        for (attempt in 0 until 20) {
            val rx = Random.nextInt(-maxR, maxR)
            val rz = Random.nextInt(-maxR, maxR)

            val topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, rx, rz)
            val pos = BlockPos(rx, topY, rz)
            val stateBelow = level.getBlockState(pos.below())
            if (!stateBelow.isAir && stateBelow.fluidState.isEmpty) {
                return pos
            }
        }
        return BlockPos(0, 100, 0)
    }

    private fun exitSafariPlayer(server: MinecraftServer, player: ServerPlayer, session: SafariSession, message: String) {
        val returnDimLocation = ResourceLocation.parse(session.returnDimension)
        val returnDimKey = ResourceKey.create(Registries.DIMENSION, returnDimLocation)
        val returnLevel = server.getLevel(returnDimKey) ?: server.overworld()

        teleportPlayer(player, returnLevel, session.returnX, session.returnY, session.returnZ, session.returnYaw, session.returnPitch)
        val finalMsg = PlayerLanguage.template(player, "safari.exit_message", message)
        player.sendSystemMessage(Component.literal(finalMsg))
    }

    private fun teleportPlayer(player: ServerPlayer, level: ServerLevel, x: Double, y: Double, z: Double, yaw: Float, pitch: Float) {
        player.teleportTo(level, x, y, z, emptySet(), yaw, pitch)
    }

    private fun formatTime(totalSeconds: Long): String {
        val mins = totalSeconds / 60
        val secs = totalSeconds % 60
        return String.format("%02d:%02d", mins, secs)
    }
}

class SafariChestMenu(
    containerId: Int,
    playerInventory: Inventory,
    container: Container = SimpleContainer(54),
    val page: Int = 1
) : ChestMenu(MenuType.GENERIC_9x6, containerId, playerInventory, container, 6) {

    override fun stillValid(player: Player): Boolean = true

    override fun clicked(slotNum: Int, button: Int, clickType: ClickType, player: Player) {
        if (slotNum in 0 until 54 && player is ServerPlayer) {
            SafariZoneFeature.handleGuiClick(player, slotNum, page)
            return
        }
        super.clicked(slotNum, button, clickType, player)
    }
}

class SafariSpawningInfluence(
    private val uncommonMult: Float,
    private val rareMult: Float,
    private val ultraRareMult: Float
) : SpawningInfluence {
    override fun affectBucketWeights(bucketWeights: MutableMap<SpawnBucket, Float>) {
        for ((bucket, weight) in bucketWeights.entries.toList()) {
            val name = bucket.name.lowercase()
            val mult = when {
                name.contains("ultra-rare") || name.contains("ultrarare") -> ultraRareMult
                name.contains("rare") -> rareMult
                name.contains("uncommon") -> uncommonMult
                name.contains("common") -> 3.0f
                else -> 2.5f
            }
            if (mult > 1.0f) {
                bucketWeights[bucket] = weight * mult
            }
        }
    }

    override fun affectWeight(detail: SpawnDetail, spawnablePosition: SpawnablePosition, weight: Float): Float {
        // Ignora restrições estritas de estruturas no Safari (ex: mansões para Mimikyu)
        if (weight <= 0f) {
            return 10.0f
        }
        return weight
    }
}

class SafariSession(
    var remainingTicks: Long = 0L,
    var returnDimension: String = "minecraft:overworld",
    var returnX: Double = 0.0,
    var returnY: Double = 64.0,
    var returnZ: Double = 0.0,
    var returnYaw: Float = 0f,
    var returnPitch: Float = 0f
)

class SafariSavedData : SavedData() {
    val sessions = mutableMapOf<UUID, SafariSession>()

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        val all = CompoundTag()
        sessions.forEach { (uuid, session) ->
            val data = CompoundTag()
            data.putLong("ticks", session.remainingTicks)
            data.putString("dim", session.returnDimension)
            data.putDouble("x", session.returnX)
            data.putDouble("y", session.returnY)
            data.putDouble("z", session.returnZ)
            data.putFloat("yaw", session.returnYaw)
            data.putFloat("pitch", session.returnPitch)
            all.put(uuid.toString(), data)
        }
        tag.put("sessions", all)
        return tag
    }

    companion object {
        private const val NAME = "nbp_cobble_plus_safari"
        private fun load(tag: CompoundTag, registries: HolderLookup.Provider) = SafariSavedData().also { savedData ->
            val all = tag.getCompound("sessions")
            all.allKeys.forEach { id ->
                runCatching { UUID.fromString(id) }.getOrNull()?.let { uuid ->
                    val data = all.getCompound(id)
                    savedData.sessions[uuid] = SafariSession(
                        remainingTicks = data.getLong("ticks"),
                        returnDimension = data.getString("dim"),
                        returnX = data.getDouble("x"),
                        returnY = data.getDouble("y"),
                        returnZ = data.getDouble("z"),
                        returnYaw = data.getFloat("yaw"),
                        returnPitch = data.getFloat("pitch")
                    )
                }
            }
        }
        private val FACTORY = Factory(::SafariSavedData, ::load, DataFixTypes.LEVEL)
        fun get(server: MinecraftServer) = server.overworld().dataStorage.computeIfAbsent(FACTORY, NAME)
    }
}
