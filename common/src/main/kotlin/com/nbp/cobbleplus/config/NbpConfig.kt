package com.nbp.cobbleplus.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileReader
import java.io.FileWriter

data class WelcomeConfig(
    var enabled: Boolean = true,
    var enableFirstJoinMessage: Boolean = true,
    var chatMessage: String = "§a[NBP] Welcome to the NBP Cobble Plus server, {player}!",
    var firstJoinChatMessage: String = "§6[NBP] Welcome to the server for the first time, {player}!"
)

data class AutoAnnouncerConfig(
    var enabled: Boolean = true,
    var intervalSeconds: Int = 300,
    var announceInRandomOrder: Boolean = false,
    var messages: List<String> = listOf(
        "§b[NBP Tip] Use §e/nbp help §bto see the modpack commands and help!",
        "§b[NBP Tip] Join our community and keep up with tournaments and events!",
        "§b[NBP Tip] Respect other trainers and enjoy your Cobblemon journey!"
    )
)

data class BroadcastConfig(
    var enableCaptureBroadcast: Boolean = false,
    var captureMessage: String = "§f[NBP] §e{player} §fcaught §a{pokemon}§f! (Level {level})",
    var enableShinyCaptureBroadcast: Boolean = true,
    var shinyCaptureMessage: String = "§6§l[NBP SHINY] §e{player} §fcaught a SHINY Pokémon! §d★ {pokemon} ★ §f(Level {level})!",
    var enableStarterBroadcast: Boolean = true,
    var starterMessage: String = "§a[NBP] §e{player} §fchose their starter Pokémon: §b{pokemon}§f!",
    var enableTradeBroadcast: Boolean = true,
    var tradeMessage: String = "§a[NBP] Trainers §e{player1} §fand §e{player2} §fcompleted a Pokémon trade!"
)

data class PartyHealConfig(
    var enabled: Boolean = true,
    var cooldownSeconds: Int = 300,
    var healMessage: String = "§a[NBP] Your Pokémon party has been fully healed!",
    var cooldownMessage: String = "§c[NBP] Wait {seconds} seconds before healing your party again."
)

data class MeltanFurnaceConfig(
    var enabled: Boolean = true,
    var chancePerIronIngot: Double = 0.0001,
    var perfectIvCount: Int = 4,
    var horizontalSearchRadius: Int = 6,
    var verticalSearchRadius: Int = 4,
    var validFurnaces: List<String> = listOf("minecraft:furnace", "minecraft:blast_furnace")
)

data class VanillaMobSpawnBlockerConfig(
    var enabled: Boolean = true,
    var allowedEntityTypes: List<String> = listOf(
        "minecraft:villager",
        "minecraft:wandering_trader",
        // Bees must remain alive for vanilla beehives to keep their occupants/state.
        "minecraft:bee"
    ),
    var enablePokemonReplacements: Boolean = true,
    var replacementRadius: Double = 16.0,
    var maxNearbyReplacements: Int = 12,
    var replacementLevelVariance: Int = 5,
    var endRayquazaContainmentRadius: Double = 128.0,
    var endRayquazaMinimumY: Double = 48.0,
    var endRayquazaMaximumY: Double = 220.0,
    var pokemonReplacements: Map<String, List<String>> = mapOf(
        "minecraft:iron_golem" to listOf("species=golurk level=35", "species=golett level=25"),
        "minecraft:bee" to listOf("species=combee level=5"),
        "minecraft:snow_golem" to listOf("species=vanillite level=15", "species=vanillish level=20", "species=cryogonal level=25", "species=snom level=12"),
        "minecraft:silverfish" to listOf("species=durant level=10", "species=sizzlipede level=10", "species=nincada level=10", "species=orthworm level=15"),
        "minecraft:endermite" to listOf("species=dottler level=15", "species=nincada level=12", "species=venipede level=12", "species=orthworm level=18", "species=blipbug level=10"),
        "minecraft:spider" to listOf("species=spidops level=15", "species=ariados level=15", "species=galvantula level=15", "species=araquanid level=18", "species=tarountula level=10"),
        "minecraft:cave_spider" to listOf("species=tarountula level=10", "species=joltik level=10", "species=dewpider level=10", "species=spinarak level=10", "species=venipede level=12"),
        "minecraft:zombie" to listOf("species=duskull level=15", "species=shuppet level=15", "species=yamask level=16", "species=gastly level=14", "species=greavard level=14"),
        "minecraft:husk" to listOf("species=sandygast level=18", "species=yamask level=18", "species=cacnea level=16", "species=baltoy level=16", "species=trapinch level=15"),
        "minecraft:drowned" to listOf("species=frillish level=18", "species=dhelmise level=25", "species=skrelp level=16", "species=clauncher level=16", "species=mareanie level=18"),
        "minecraft:skeleton" to listOf("species=cubone level=15", "species=duskull level=15", "species=houndour level=16", "species=pawniard level=18", "species=greavard level=14"),
        "minecraft:stray" to listOf("species=snover level=18", "species=sneasel level=18", "species=snorunt level=15", "species=bergmite level=16", "species=cubchoo level=15"),
        "minecraft:bogged" to listOf("species=foongus level=18", "species=paras level=16", "species=morelull level=17", "species=shroomish level=16", "species=bramblin level=18"),
        "minecraft:blaze" to listOf("species=charcadet level=20", "species=magmar level=25", "species=litwick level=18", "species=slugma level=18", "species=larvesta level=20"),
        "minecraft:breeze" to listOf("species=rotom level=25", "species=castform level=22", "species=kilowattrel level=24", "species=drifloon level=20", "species=emolga level=20"),
        "minecraft:magma_cube" to listOf("species=slugma level=15", "species=magcargo level=25", "species=solosis level=15", "species=goomy level=16", "species=ditto level=18"),
        "minecraft:slime" to listOf("species=solosis level=12", "species=goomy level=14", "species=ditto level=16", "species=reuniclus level=28", "species=grimer level=16"),
        "minecraft:witch" to listOf("species=mismagius level=28", "species=hattrem level=22", "species=murkrow level=20", "species=impidimp level=18", "species=misdreavus level=20"),
        "minecraft:wither" to listOf("species=necrozma level=75"),
        "minecraft:ender_dragon" to listOf("species=rayquaza level=75")
    )
)

data class LegendarySpawnEntry(
    var species: String = "",
    var dimensions: List<String> = listOf("minecraft:overworld"),
    var biomes: List<String> = emptyList(),
    var times: List<String> = listOf("any"),
    var aquatic: Boolean = false,
    var weight: Double = 1.0
)

private fun legend(species: String, biome: String, time: String = "day", aquatic: Boolean = false) =
    LegendarySpawnEntry(species, listOf("minecraft:overworld"), listOf(biome), listOf(time), aquatic)

private fun dimensionalLegend(species: String, dimension: String, biomes: List<String>) =
    LegendarySpawnEntry(species, listOf(dimension), biomes, listOf("any"))

private fun defaultLegendaryPool(): List<LegendarySpawnEntry> = listOf(
    legend("articuno", "#cobblemon:is_freezing", "night"), legend("zapdos", "#cobblemon:is_mountain"),
    legend("mew", "#cobblemon:is_jungle"), legend("raikou", "#cobblemon:is_grassland"),
    legend("suicune", "#cobblemon:is_freshwater", "night", true), legend("lugia", "#cobblemon:is_ocean", "night", true),
    legend("celebi", "#cobblemon:is_forest"), legend("groudon", "#cobblemon:is_arid"),
    legend("regirock", "#cobblemon:is_arid"), legend("regice", "#cobblemon:is_freezing", "night"),
    legend("registeel", "#cobblemon:is_mountain", "night"), legend("latias", "#cobblemon:is_mountain"),
    legend("latios", "#cobblemon:is_mountain"), legend("kyogre", "#cobblemon:is_ocean", "night", true),
    legend("uxie", "#cobblemon:is_freshwater", aquatic = true), legend("mesprit", "#cobblemon:is_floral"),
    legend("azelf", "#cobblemon:is_mountain"), legend("regigigas", "#cobblemon:is_mountain"),
    legend("phione", "#cobblemon:is_freshwater", aquatic = true), legend("manaphy", "#cobblemon:is_ocean", aquatic = true),
    legend("darkrai", "#cobblemon:is_spooky", "night"), legend("shaymin", "#cobblemon:is_floral"),
    legend("victini", "#cobblemon:is_grassland"), legend("cobalion", "#cobblemon:is_forest"),
    legend("terrakion", "#cobblemon:is_mountain"), legend("virizion", "#cobblemon:is_forest"),
    legend("tornadus", "#cobblemon:is_mountain"), legend("thundurus", "#cobblemon:is_mountain", "night"),
    legend("landorus", "#cobblemon:is_grassland"), legend("kyurem", "#cobblemon:is_freezing", "night"),
    legend("keldeo", "#cobblemon:is_freshwater", aquatic = true), legend("meloetta", "#cobblemon:is_floral"),
    legend("genesect", "#cobblemon:is_taiga", "night"), legend("xerneas", "#cobblemon:is_forest"),
    legend("diancie", "#cobblemon:is_mountain"), legend("typenull", "#cobblemon:is_temperate", "night"),
    legend("silvally", "#cobblemon:is_temperate"), legend("magearna", "#cobblemon:is_temperate"),
    legend("tapukoko", "#cobblemon:is_coast"),
    legend("tapubulu", "#cobblemon:is_jungle"), legend("zeraora", "#cobblemon:is_mountain", "night"),
    legend("zacian", "#cobblemon:is_forest"), legend("zamazenta", "#cobblemon:is_mountain", "night"),
    legend("kubfu", "#cobblemon:is_mountain"), legend("zarude", "#cobblemon:is_jungle"),
    legend("regieleki", "#cobblemon:is_mountain"),
    legend("glastrier", "#cobblemon:is_freezing", "night"), legend("calyrex", "#cobblemon:is_cold", "night"),
    legend("enamorus", "#cobblemon:is_floral"), legend("wochien", "#cobblemon:is_swamp", "night"),
    legend("chienpao", "#cobblemon:is_freezing", "night"), legend("tinglu", "#cobblemon:is_arid"),
    legend("walkingwake", "#cobblemon:is_jungle"), legend("ironleaves", "#cobblemon:is_forest"),
    legend("ogerpon", "#cobblemon:is_forest"), legend("ragingbolt", "#cobblemon:is_jungle"),
    dimensionalLegend("reshiram", "minecraft:the_nether", listOf("minecraft:nether_wastes", "minecraft:crimson_forest", "minecraft:basalt_deltas")),
    dimensionalLegend("chiyu", "minecraft:the_nether", listOf("minecraft:crimson_forest", "minecraft:nether_wastes")),
    dimensionalLegend("entei", "minecraft:the_nether", listOf("minecraft:basalt_deltas", "minecraft:nether_wastes")),
    dimensionalLegend("hooh", "minecraft:the_nether", listOf("minecraft:crimson_forest", "minecraft:nether_wastes")),
    dimensionalLegend("heatran", "minecraft:the_nether", listOf("minecraft:basalt_deltas", "minecraft:nether_wastes")),
    dimensionalLegend("volcanion", "minecraft:the_nether", listOf("minecraft:basalt_deltas", "minecraft:nether_wastes")),
    dimensionalLegend("cosmog", "minecraft:the_end", listOf("minecraft:end_highlands", "minecraft:end_midlands", "minecraft:small_end_islands")),
    dimensionalLegend("ironboulder", "minecraft:the_end", listOf("minecraft:end_highlands", "minecraft:end_midlands")),
    dimensionalLegend("ironcrown", "minecraft:the_end", listOf("minecraft:end_highlands", "minecraft:end_midlands")),
    dimensionalLegend("deoxys", "minecraft:the_end", listOf("minecraft:end_barrens", "minecraft:small_end_islands")),
    dimensionalLegend("eternatus", "minecraft:the_end", listOf("minecraft:end_barrens", "minecraft:end_highlands"))
)

data class LegendarySpawnerConfig(
    var enabled: Boolean = true,
    /** Prevents these species from being selected by Cobblemon's natural spawner. */
    var blockNaturalPokemonSpawns: Boolean = true,
    var blockedNaturalSpecies: MutableList<String> = defaultLegendaryPool().map { it.species }.toMutableList(),
    var intervalMinutes: Int = 30,
    var spawnChance: Double = 0.25,
    var minimumDistanceFromPlayer: Int = 48,
    var maximumDistanceFromPlayer: Int = 128,
    var locationAttempts: Int = 48,
    var allowBiomeFallback: Boolean = true,
    var minimumLevel: Int = 60,
    var maximumLevel: Int = 75,
    var perfectIvCount: Int = 4,
    var reduceRepeatChance: Boolean = true,
    var repeatChanceDivisor: Double = 10.0,
    var balanceSpawnsBetweenPlayers: Boolean = true,
    var playerRepeatChanceDivisor: Double = 4.0,
    var dimensionWeights: Map<String, Double> = mapOf(
        "minecraft:overworld" to 1.0,
        "minecraft:the_nether" to 1.0,
        "minecraft:the_end" to 1.0
    ),
    var announceSpawn: Boolean = true,
    var includeCoordinatesInAnnouncement: Boolean = true,
    var spawnMessage: String = "§6[Legendary] §e{pokemon} §fappeared in §d{dimension} §fnear §a{player}§f!",
    var legendaryPool: List<LegendarySpawnEntry> = defaultLegendaryPool()
)

data class CatchComboTier(
    var minCombo: Int = 0,
    var guaranteedPerfectIvs: Int = 0,
    var rareSpawnMultiplier: Double = 1.0,
    var shinyChanceMultiplier: Double = 1.0,
    var xpMultiplier: Double = 1.0
)

data class CatchComboConfig(
    var enabled: Boolean = true,
    var comboBreaksOnSpeciesChange: Boolean = true,
    var enablePerfectIvBonus: Boolean = true,
    var enableShinyBonus: Boolean = true,
    var enableExpBonus: Boolean = true,
    var enableRareSpawnBonus: Boolean = true,
    var enableSpeciesSpawnBonus: Boolean = true,
    var enableRecordMessage: Boolean = true,
    // Combo em que o ramp de "Rare Spawns" do primeiro tier termina (1x -> valor do 1º tier).
    var rareSpawnRampCombo: Int = 10,
    // Acima do último tier, o multiplicador de EXP sobe {xpMultiplierIncrementPerStep} a cada {xpMultiplierStepSize} combos.
    var xpMultiplierStepSize: Int = 10,
    var xpMultiplierIncrementPerStep: Double = 0.5,
    var rareSpawnBucketNames: List<String> = listOf("rare", "ultra-rare"),
    var comboMessage: String = "§b[Combo] §f{pokemon} §ax{count}",
    var ivHudSuffix: String = " §d| {ivs} guaranteed perfect IV(s)",
    var hudBonusLineFormat: String = "§7IVs {ivs} §7| Shiny x{shiny} §7| Spawn x{rare} §7| EXP x{xp}",
    var newRecordMessage: String = "§6[Combo] §fNew record: §e{pokemon} §fx{count}!",
    var noComboMessage: String = "§7[Combo] No active catch combo.",
    var resetMessage: String = "§c[Combo] Your current combo has been reset.",
    var hudShownMessage: String = "§a[Combo] Combo HUD enabled.",
    var hudHiddenMessage: String = "§7[Combo] Combo HUD hidden.",
    var tiers: List<CatchComboTier> = listOf(
        CatchComboTier(minCombo = 0, guaranteedPerfectIvs = 0, rareSpawnMultiplier = 3.0, shinyChanceMultiplier = 2.0, xpMultiplier = 1.1),
        CatchComboTier(minCombo = 11, guaranteedPerfectIvs = 2, rareSpawnMultiplier = 4.0, shinyChanceMultiplier = 8.0, xpMultiplier = 1.5),
        CatchComboTier(minCombo = 21, guaranteedPerfectIvs = 3, rareSpawnMultiplier = 5.0, shinyChanceMultiplier = 16.0, xpMultiplier = 2.0),
        CatchComboTier(minCombo = 31, guaranteedPerfectIvs = 4, rareSpawnMultiplier = 6.0, shinyChanceMultiplier = 24.0, xpMultiplier = 2.5)
    )
)

data class CaptureCapAdvancement(
    var advancement: String = "",
    var cap: Int = 13
)

data class CaptureCapItemUpgrade(
    var item: String = "",
    var increase: Int = 0,
    var cap: Int = 0,
    var consume: Boolean = true
)

data class CaptureCapConfig(
    var enabled: Boolean = true,
    var defaultCap: Int = 13,
    var maximumCap: Int = 100,
    var itemUpgrades: List<CaptureCapItemUpgrade> = listOf(
        CaptureCapItemUpgrade("nbp_cobble_plus:capture_permit", increase = 5)
    ),
    var advancements: List<CaptureCapAdvancement> = listOf(
        CaptureCapAdvancement("minecraft:story/mine_diamond", 25),
        CaptureCapAdvancement("minecraft:nether/obtain_blaze_rod", 35),
        CaptureCapAdvancement("minecraft:end/kill_dragon", 50),
        CaptureCapAdvancement("minecraft:nether/summon_wither", 70),
        CaptureCapAdvancement("minecraft:end/elytra", 100)
    )
)

data class PokemonApiaryConfig(
    var enabled: Boolean = true,
    var checkIntervalTicks: Int = 200,
    var producerRadius: Double = 16.0,
    var requireDaytime: Boolean = true,
    var requireFlowers: Boolean = true,
    var flowerRadius: Int = 6,
    var combeeMinimumProductionTicks: Int = 4800,
    var combeeMaximumProductionTicks: Int = 7200,
    var vespiquenMinimumProductionTicks: Int = 2400,
    var vespiquenMaximumProductionTicks: Int = 3600,
    var enablePastureItemProduction: Boolean = true,
    var combeePastureMinimumDropTicks: Int = 6000,
    var combeePastureMaximumDropTicks: Int = 12000,
    var vespiquenPastureMinimumDropTicks: Int = 3600,
    var vespiquenPastureMaximumDropTicks: Int = 7200
)

data class EconomyConfig(
    var enabled: Boolean = true,
    var disableNativeCobbleDollarsRewards: Boolean = true,
    var rewardCaptures: Boolean = true,
    var rewardWildVictories: Boolean = true,
    var captureBase: Double = 8.0,
    var capturePerLevel: Double = 0.35,
    var defeatBase: Double = 5.0,
    var defeatPerLevel: Double = 0.25,
    var shinyMultiplier: Double = 3.0,
    var legendaryMultiplier: Double = 4.0,
    var maximumRewardPerPokemon: Long = 250,
    var dailyEarningCap: Long = 5000,
    var dailySoftCapStart: Long = 3000,
    var softCapMultiplier: Double = 0.25,
    var repeatWindowMinutes: Int = 30,
    var fullRewardsPerSpeciesAndAction: Int = 5,
    var repeatedRewardMultiplier: Double = 0.20,
    var applyCobbleDollarsGlobalMultiplier: Boolean = false,
    var showRewardActionBar: Boolean = true,
    var legendaryBonusSpecies: List<String> = listOf("necrozma", "rayquaza"),
    var configureRaidDensRewards: Boolean = true,
    var raidDensRewardsByTier: List<Int> = listOf(200, 400, 750, 1250, 4500, 10000, 20000)
)

data class SafariZoneConfig(
    var enabled: Boolean = true,
    var dimension: String = "nbp_cobble_plus:safari_zone",
    var ticketPrice: Long = 2500,
    var sessionDurationMinutes: Int = 15,
    var shinyMultiplier: Double = 4.0,
    var perfectIvCount: Int = 2,
    var uncommonSpawnMultiplier: Float = 5.0f,
    var rareSpawnMultiplier: Float = 8.0f,
    var ultraRareSpawnMultiplier: Float = 10.0f,
    var randomSpawnMinRadius: Int = 200,
    var randomSpawnMaxRadius: Int = 1000,
    var announceEntry: Boolean = true,
    var entryMessage: String = "§a[Safari] §e{player} §fentered the Safari Zone!",
    var exitMessage: String = "§c[Safari] Your Safari Zone session has ended.",
    var tickActionBarFormat: String = "§a[Safari Zone] §fTime remaining: §e{time} §7| Use §c/safari exit §7to leave",
    var biomePrices: MutableMap<String, Long> = mutableMapOf(
        "plains" to 2500L, "sunflower_plains" to 2500L, "forest" to 2500L, "flower_forest" to 2500L,
        "birch_forest" to 2500L, "dark_forest" to 2500L, "taiga" to 2500L, "snowy_taiga" to 2500L,
        "jungle" to 2500L, "bamboo_jungle" to 2500L, "savanna" to 2500L, "desert" to 2500L,
        "badlands" to 2500L, "swamp" to 2500L, "mangrove_swamp" to 2500L, "cherry_grove" to 2500L,
        "meadow" to 2500L, "grove" to 2500L, "snowy_slopes" to 2500L, "jagged_peaks" to 2500L,
        "frozen_peaks" to 2500L, "stony_peaks" to 2500L, "windswept_hills" to 2500L, "ocean" to 2500L,
        "warm_ocean" to 2500L, "frozen_ocean" to 2500L, "beach" to 2500L, "snowy_beach" to 2500L,
        "stony_shore" to 2500L, "river" to 2500L, "mushroom_fields" to 2500L, "lush_caves" to 2500L,
        "dripstone_caves" to 2500L, "nether_wastes" to 3500L, "crimson_forest" to 3500L,
        "warped_forest" to 3500L, "soul_sand_valley" to 3500L, "basalt_deltas" to 3500L,
        "the_end" to 3500L, "end_highlands" to 3500L, "end_midlands" to 3500L, "end_barrens" to 3500L,
        "small_end_islands" to 3500L, "random" to 2500L
    )
)

data class ServerEventsConfig(
    var enabled: Boolean = true,
    var minIntervalMinutes: Int = 45,
    var maxIntervalMinutes: Int = 180,
    var eventDurationMinutes: Int = 30,
    var hordeInvasionDurationMinutes: Int = 2,
    var hordeShinyMultiplier: Double = 2.0,
    var announceWithScreenTitle: Boolean = true,
    var hiddenAbilityBaseChance: Double = 0.01,
    var bountyRewardCommonMin: Long = 500L,
    var bountyRewardCommonMax: Long = 1000L,
    var bountyRewardUncommonMin: Long = 1500L,
    var bountyRewardUncommonMax: Long = 2500L,
    var bountyRewardRareMin: Long = 3000L,
    var bountyRewardRareMax: Long = 4500L,
    var bountyRewardUltraRareMin: Long = 5000L,
    var bountyRewardUltraRareMax: Long = 7000L
)

data class WagerBattleConfig(
    var enabled: Boolean = true,
    var minWagerAmount: Long = 10L,
    var maxWagerAmount: Long = 100000L,
    var maxChallengeDistance: Int = 30,
    var challengeTimeoutSeconds: Int = 60,
    var announceVictories: Boolean = true
)

data class PointsConfig(
    var enabled: Boolean = true,
    var rewardCaptures: Boolean = true,
    var rewardWildVictories: Boolean = true,
    var rewardBreeding: Boolean = true,
    var rewardPokedexScans: Boolean = true,
    var captureAmount: Long = 1,
    var victoryAmount: Long = 1,
    var breedingAmount: Long = 1,
    var typePointsOnCaptureAmount: Long = 1,
    var typePointsOnVictoryAmount: Long = 1,
    var typePointsOnScanAmount: Long = 1,
    var shinyAmount: Long = 5,
    var legendaryAmount: Long = 10,
    var mythicalAmount: Long = 10,
    var ultraBeastAmount: Long = 10,
    var showRewardBar: Boolean = true,
    var rewardBarDurationTicks: Int = 60
)

data class ModConfigData(
    var welcome: WelcomeConfig = WelcomeConfig(),
    var announcer: AutoAnnouncerConfig = AutoAnnouncerConfig(),
    var broadcast: BroadcastConfig = BroadcastConfig(),
    var partyHeal: PartyHealConfig = PartyHealConfig(),
    var meltanFurnace: MeltanFurnaceConfig = MeltanFurnaceConfig(),
    var vanillaMobSpawnBlocker: VanillaMobSpawnBlockerConfig = VanillaMobSpawnBlockerConfig(),
    var legendarySpawner: LegendarySpawnerConfig = LegendarySpawnerConfig(),
    var catchCombo: CatchComboConfig = CatchComboConfig(),
    var captureCap: CaptureCapConfig = CaptureCapConfig(),
    var pokemonApiary: PokemonApiaryConfig = PokemonApiaryConfig(),
    var economy: EconomyConfig = EconomyConfig(),
    var safariZone: SafariZoneConfig = SafariZoneConfig(),
    var wagerBattle: WagerBattleConfig = WagerBattleConfig(),
    var serverEvents: ServerEventsConfig = ServerEventsConfig(),
    var points: PointsConfig = PointsConfig()
)

object NbpConfig {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val configFile = File("config/nbp_cobble_plus.json")
    
    var data: ModConfigData = ModConfigData()
        private set

    fun load() {
        try {
            if (!configFile.parentFile.exists()) {
                configFile.parentFile.mkdirs()
            }
            if (configFile.exists()) {
                FileReader(configFile).use { reader ->
                    data = gson.fromJson(reader, ModConfigData::class.java) ?: ModConfigData()
                }
                val changed = addMissingBossReplacements() or migrateRaidEconomyDefaults() or ensurePointsConfig()
                if (changed) save()
            } else {
                save()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            data = ModConfigData()
        }
    }

    private fun addMissingBossReplacements(): Boolean {
        val config = data.vanillaMobSpawnBlocker
        val replacements = config.pokemonReplacements.toMutableMap()
        val allowed = config.allowedEntityTypes.toMutableList()
        var changed = false
        if (allowed.none { it.equals("minecraft:bee", ignoreCase = true) }) {
            allowed += "minecraft:bee"
            changed = true
        }
        if ("minecraft:wither" !in replacements) {
            replacements["minecraft:wither"] = listOf("species=necrozma level=75")
            changed = true
        }
        if ("minecraft:ender_dragon" !in replacements) {
            replacements["minecraft:ender_dragon"] = listOf("species=rayquaza level=75")
            changed = true
        }
        if ("minecraft:bee" !in replacements) {
            replacements["minecraft:bee"] = listOf("species=combee level=5")
            changed = true
        }
        if (changed) {
            config.pokemonReplacements = replacements
            config.allowedEntityTypes = allowed
        }
        return changed
    }

    private fun migrateRaidEconomyDefaults(): Boolean {
        val oldDefaults = listOf(200, 400, 750, 1250, 2000, 3500, 6000)
        if (data.economy.raidDensRewardsByTier != oldDefaults) return false
        data.economy.raidDensRewardsByTier = listOf(200, 400, 750, 1250, 4500, 10000, 20000)
        return true
    }

    private fun ensurePointsConfig(): Boolean {
        if (data.points == null) {
            data.points = PointsConfig()
            return true
        }
        return false
    }

    fun save() {
        try {
            if (!configFile.parentFile.exists()) {
                configFile.parentFile.mkdirs()
            }
            FileWriter(configFile).use { writer ->
                gson.toJson(data, writer)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
