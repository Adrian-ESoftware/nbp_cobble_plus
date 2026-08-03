package com.nbp.cobbleplus.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileReader
import java.io.FileWriter

data class WelcomeConfig(
    var enabled: Boolean = true,
    var enableFirstJoinMessage: Boolean = true,
    var chatMessage: String = "§a[NBP] Bem-vindo ao servidor NBP Cobble Plus, {player}!",
    var firstJoinChatMessage: String = "§6[NBP] Seja muito bem-vindo pela primeira vez ao servidor, {player}!"
)

data class AutoAnnouncerConfig(
    var enabled: Boolean = true,
    var intervalSeconds: Int = 300,
    var announceInRandomOrder: Boolean = false,
    var messages: List<String> = listOf(
        "§b[Dica NBP] Use §e/nbp help §bpara ver os comandos e ajuda do modpack!",
        "§b[Dica NBP] Participe da nossa comunidade e fique por dentro dos torneios e eventos!",
        "§b[Dica NBP] Respeite os outros treinadores e aproveite sua jornada Cobblemon!"
    )
)

data class BroadcastConfig(
    var enableCaptureBroadcast: Boolean = true,
    var captureMessage: String = "§f[NBP] O jogador §e{player} §fcapturou um §a{pokemon}§f! (Level {level})",
    var enableShinyCaptureBroadcast: Boolean = true,
    var shinyCaptureMessage: String = "§6§l[NBP SHINY] §e{player} §fcapturou um Pokémon SHINY! §d★ {pokemon} ★ §f(Level {level})!",
    var enableStarterBroadcast: Boolean = true,
    var starterMessage: String = "§a[NBP] O jogador §e{player} §fescolheu seu Pokémon inicial: §b{pokemon}§f!",
    var enableTradeBroadcast: Boolean = true,
    var tradeMessage: String = "§a[NBP] Os treinadores §e{player1} §fe §e{player2} §fconcluíram uma troca de Pokémon!"
)

data class PartyHealConfig(
    var enabled: Boolean = true,
    var cooldownSeconds: Int = 300,
    var healMessage: String = "§a[NBP] Sua equipe Pokémon foi totalmente curada!",
    var cooldownMessage: String = "§c[NBP] Aguarde {seconds} segundos para usar o cura-party novamente."
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
    var enableRecordMessage: Boolean = true,
    // Combo em que o ramp de "Rare Spawns" do primeiro tier termina (1x -> valor do 1º tier).
    var rareSpawnRampCombo: Int = 10,
    // Acima do último tier, o multiplicador de EXP sobe {xpMultiplierIncrementPerStep} a cada {xpMultiplierStepSize} combos.
    var xpMultiplierStepSize: Int = 10,
    var xpMultiplierIncrementPerStep: Double = 0.5,
    var rareSpawnBucketNames: List<String> = listOf("rare", "ultra-rare"),
    var comboMessage: String = "§b[Combo] §f{pokemon} §ax{count}",
    var hudBonusLineFormat: String = "§7IVs {ivs} §7| Shiny x{shiny} §7| Spawn x{rare} §7| EXP x{xp}",
    var perfectIvSuffix: String = " §d| +{amount} IV(s) perfeito(s)",
    var newRecordMessage: String = "§6[Combo] §fNovo recorde: §e{pokemon} §fx{count}!",
    var noComboMessage: String = "§7[Combo] Nenhum combo ativo no momento.",
    var resetMessage: String = "§c[Combo] Seu combo atual foi resetado.",
    var tiers: List<CatchComboTier> = listOf(
        CatchComboTier(minCombo = 0, guaranteedPerfectIvs = 0, rareSpawnMultiplier = 3.0, shinyChanceMultiplier = 2.0, xpMultiplier = 1.1),
        CatchComboTier(minCombo = 11, guaranteedPerfectIvs = 2, rareSpawnMultiplier = 4.0, shinyChanceMultiplier = 8.0, xpMultiplier = 1.5),
        CatchComboTier(minCombo = 21, guaranteedPerfectIvs = 3, rareSpawnMultiplier = 5.0, shinyChanceMultiplier = 16.0, xpMultiplier = 2.0),
        CatchComboTier(minCombo = 31, guaranteedPerfectIvs = 4, rareSpawnMultiplier = 6.0, shinyChanceMultiplier = 24.0, xpMultiplier = 2.5)
    )
)

data class ModConfigData(
    var welcome: WelcomeConfig = WelcomeConfig(),
    var announcer: AutoAnnouncerConfig = AutoAnnouncerConfig(),
    var broadcast: BroadcastConfig = BroadcastConfig(),
    var partyHeal: PartyHealConfig = PartyHealConfig(),
    var catchCombo: CatchComboConfig = CatchComboConfig()
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
            } else {
                save()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            data = ModConfigData()
        }
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
