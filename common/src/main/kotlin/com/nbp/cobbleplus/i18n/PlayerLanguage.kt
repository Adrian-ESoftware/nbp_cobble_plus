package com.nbp.cobbleplus.i18n

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

object PlayerLanguage {
    const val DEFAULT = "en_us"
    val supported = listOf("en_us", "pt_br")
    private var data: LanguageSavedData? = null

    private val en = mapOf(
        "lang.current" to "§a[NBP] Your language is §e{lang}§a. Available: §fen_us, pt_br",
        "lang.changed" to "§a[NBP] Language changed to §e{lang}§a.",
        "lang.invalid" to "§c[NBP] Unsupported language. Available: en_us, pt_br.",
        "player.only" to "Only players can use this command.",
        "command.status" to "§a[NBP Cobble Plus] Server Suite v1.0.0 is active! Type §e/nbp help §afor help.",
        "command.reloaded" to "§a[NBP Cobble Plus] Configuration and modules reloaded successfully!",
        "command.announce" to "§a[NBP] Announcement sent successfully.",
        "legend.chance" to "§6[Legendary] §fYour current host chance is §a{chance}%§f.",
        "legend.available.none" to "§7No legendary matches this dimension, time, and biome.",
        "legend.available" to "§6[Legendary] §fAvailable in this area: §e{species}",
        "legend.test.success" to "§a[NBP] Test legendary spawned near you.",
        "legend.test.failed" to "Could not find a valid species/position in this dimension and loaded chunks.",
        "legend.natural.success" to "§a[NBP] Natural cycle completed; player and legendary were selected.",
        "legend.natural.failed" to "The natural cycle found no valid player, species, or position.",
        "legend.history.reset" to "§a[NBP] Legendary history reset.",
        "party.disabled" to "§c[NBP] Party healing is disabled on this server.",
        "party.error" to "§c[NBP] An error occurred while healing your Pokémon party."
        ,"capture_cap.too_high" to "§c[Capture Cap] That Pokémon is level {level}, but your current capture cap is {cap}."
        ,"capture_cap.current" to "§b[Capture Cap] §fYou can capture Pokémon up to level §e{cap}§f."
        ,"capture_cap.increased" to "§a[Capture Cap] Your limit increased from §e{old} §ato §e{cap}§a."
        ,"capture_cap.maximum" to "§7[Capture Cap] Your capture limit is already at the maximum: {cap}."
        ,"capture_cap.admin_set" to "§a[Capture Cap] {player}'s effective limit is now {cap}."
        ,"economy.capture_reward" to "§a+{amount} CobbleDollars §7(capture)"
        ,"economy.defeat_reward" to "§a+{amount} CobbleDollars §7(victory)"
        ,"economy.cap_reached" to "§7[Economy] Your daily earning limit has been reached."
        ,"economy.status" to "§6[Economy audit] §fCobbleDollars balance: §a{balance} §7| §fNBP credited today: §a{earned}§f / §e{cap}"
        ,"economy.reset" to "§a[Economy] Daily economy data for {player} was reset."
        ,"safari.disabled" to "§c[Safari] The Safari Zone system is disabled on this server."
        ,"safari.active_session" to "§c[Safari] You already have an active session in the Safari Zone!"
        ,"safari.insufficient_balance" to "§c[Safari] Insufficient balance! A Safari ticket costs §e{price} CobbleDollars§c."
        ,"safari.entered" to "§a[Safari] Welcome to the Safari Zone! You have been sent to a random location for §e{minutes} minutes§a."
        ,"safari.no_active_session" to "§c[Safari] You do not have an active session in the Safari Zone."
        ,"safari.voluntarily_exited" to "§e[Safari] You voluntarily left the Safari Zone."
        ,"safari.left_dimension" to "§c[Safari] You left the Safari Zone dimension. Your session has ended."
        ,"safari.setspawn_info" to "§a[Safari] The Safari Zone generates 100% random spawn locations for players upon entry."
        ,"safari.boundary_reached" to "§c[Safari] You reached the boundary limit of the Safari Zone (1,000 blocks)!"
        ,"safari.unauthorized_entry" to "§c[Safari] Unauthorized entry! You must buy a Safari ticket via /safari to access this dimension."
        ,"safari.beds_prohibited" to "§c[Safari] Beds and sleeping are prohibited in the Safari Zone!"
        ,"safari.gui_title" to "§2§lSafari"
        ,"safari.gui_title_p1" to "§2§lSafari (1/2)"
        ,"safari.gui_title_p2" to "§c§lSafari (2/2)"
        ,"safari.gui.next_page" to "§eNext Page -> (Nether & End)"
        ,"safari.gui.prev_page" to "§e<- Previous Page (Overworld)"
        ,"safari.gui.click_to_enter" to "§eClick to teleport into this biome!"
        ,"safari.gui.price" to "§7Cost: §a{price} CobbleDollars"
        ,"safari.gui.duration" to "§7Duration: §e{minutes} minutes"
        ,"safari.biome.random" to "§e§lRandom Biome"
        ,"safari.biome.plains" to "§aPlains"
        ,"safari.biome.sunflower_plains" to "§eSunflower Plains"
        ,"safari.biome.forest" to "§2Forest"
        ,"safari.biome.flower_forest" to "§dFlower Forest"
        ,"safari.biome.birch_forest" to "§fBirch Forest"
        ,"safari.biome.dark_forest" to "§8Dark Forest"
        ,"safari.biome.taiga" to "§2Taiga"
        ,"safari.biome.snowy_taiga" to "§bSnowy Taiga"
        ,"safari.biome.jungle" to "§aJungle"
        ,"safari.biome.bamboo_jungle" to "§2Bamboo Jungle"
        ,"safari.biome.savanna" to "§6Savanna"
        ,"safari.biome.desert" to "§eDesert"
        ,"safari.biome.badlands" to "§6Badlands / Mesa"
        ,"safari.biome.swamp" to "§2Swamp"
        ,"safari.biome.mangrove_swamp" to "§2Mangrove Swamp"
        ,"safari.biome.cherry_grove" to "§dCherry Grove"
        ,"safari.biome.meadow" to "§aMeadow"
        ,"safari.biome.grove" to "§fSnowy Grove"
        ,"safari.biome.snowy_slopes" to "§bSnowy Slopes"
        ,"safari.biome.jagged_peaks" to "§bJagged Peaks"
        ,"safari.biome.frozen_peaks" to "§bFrozen Peaks"
        ,"safari.biome.stony_peaks" to "§7Stony Peaks"
        ,"safari.biome.windswept_hills" to "§7Windswept Hills"
        ,"safari.biome.ocean" to "§1Ocean"
        ,"safari.biome.warm_ocean" to "§bWarm Ocean / Coral"
        ,"safari.biome.frozen_ocean" to "§bFrozen Ocean"
        ,"safari.biome.beach" to "§eBeach"
        ,"safari.biome.snowy_beach" to "§bSnowy Beach"
        ,"safari.biome.stony_shore" to "§7Stony Shore"
        ,"safari.biome.river" to "§9River"
        ,"safari.biome.mushroom_fields" to "§cMushroom Fields"
        ,"safari.biome.lush_caves" to "§aLush Caves"
        ,"safari.biome.dripstone_caves" to "§7Dripstone Caves"
        ,"safari.biome.nether_wastes" to "§cNether Wastes §7(Special)"
        ,"safari.biome.crimson_forest" to "§cCrimson Forest §7(Special)"
        ,"safari.biome.warped_forest" to "§3Warped Forest §7(Special)"
        ,"safari.biome.soul_sand_valley" to "§bSoul Sand Valley §7(Special)"
        ,"safari.biome.basalt_deltas" to "§8Basalt Deltas §7(Special)"
        ,"safari.biome.the_end" to "§dThe End §7(Special)"
        ,"safari.biome.end_highlands" to "§dEnd Highlands §7(Special)"
        ,"safari.biome.end_midlands" to "§dEnd Midlands §7(Special)"
        ,"safari.biome.end_barrens" to "§dEnd Barrens §7(Special)"
        ,"safari.biome.small_end_islands" to "§dSmall End Islands §7(Special)"
        ,"wager.disabled" to "§c[Wager Battle] Wager battles are disabled on this server."
        ,"wager.usage" to "§c[Wager Battle] Usage: /duel <player> <amount> or /duel accept | deny"
        ,"wager.self_challenge" to "§c[Wager Battle] You cannot challenge yourself!"
        ,"wager.target_not_found" to "§c[Wager Battle] Player '{player}' was not found or is offline."
        ,"wager.min_max" to "§c[Wager Battle] Wager amount must be between {min} and {max} CobbleDollars!"
        ,"wager.insufficient_sender" to "§c[Wager Battle] You don't have {amount} CobbleDollars for this wager!"
        ,"wager.insufficient_target" to "§c[Wager Battle] {player} does not have {amount} CobbleDollars for this wager."
        ,"wager.pending_exists" to "§c[Wager Battle] You already have an active challenge pending!"
        ,"wager.challenge_sent" to "§a[Wager Battle] Challenge sent to §e{player} §ffor §a{amount} CobbleDollars§f!"
        ,"wager.challenge_received" to "§6§l[WAGER BATTLE CHALLENGE] §e{player} §fchallenged you to a battle for §a{amount} CobbleDollars§f! Click below or type §c/duel accept §fto accept."
        ,"wager.click_to_accept" to "§e§l[CLICK HERE TO ACCEPT WAGER BATTLE]"
        ,"wager.no_pending" to "§c[Wager Battle] You have no pending wager battle challenges."
        ,"wager.denied_sender" to "§c[Wager Battle] You denied the wager challenge from {player}."
        ,"wager.denied_target" to "§c[Wager Battle] {player} denied your wager challenge."
        ,"wager.accepted_start" to "§a[Wager Battle] Wager accepted! §e{player1} §fvs §e{player2} §ffor §a{pool} CobbleDollars §f({amount} each)! Start the battle now!"
        ,"wager.victory_winner" to "§a§l[WAGER VICTORY] §fYou won the wager battle against §e{loser} §fand earned §a+{amount} CobbleDollars§f!"
        ,"wager.defeat_loser" to "§c§l[WAGER DEFEAT] §fYou lost the wager battle against §e{winner} §fand lost §c-{amount} CobbleDollars§f."
        ,"wager.broadcast" to "§6§l[PVP WAGER] §e{winner} §fdefeated §e{loser} §fin a wager battle and won §a{amount} CobbleDollars§f!"
        ,"wager.refund_draw" to "§e[Wager Battle] The battle ended in a draw/cancel. Your bet of {amount} CobbleDollars has been refunded."
        ,"wager.not_nearby" to "§c[Wager Battle] You must be within {distance} blocks of {player}!"
    )
    private val pt = mapOf(
        "lang.current" to "§a[NBP] Seu idioma é §e{lang}§a. Disponíveis: §fen_us, pt_br",
        "lang.changed" to "§a[NBP] Idioma alterado para §e{lang}§a.",
        "lang.invalid" to "§c[NBP] Idioma não suportado. Disponíveis: en_us, pt_br.",
        "player.only" to "Apenas jogadores podem usar este comando.",
        "command.status" to "§a[NBP Cobble Plus] Suíte de Servidor v1.0.0 ativa! Digite §e/nbp help §apara ajuda.",
        "command.reloaded" to "§a[NBP Cobble Plus] Configurações e módulos recarregados com sucesso!",
        "command.announce" to "§a[NBP] Anúncio enviado manualmente com sucesso.",
        "legend.chance" to "§6[Lendário] §fSua chance atual de ser anfitrião é §a{chance}%§f.",
        "legend.available.none" to "§7Nenhum lendário compatível com esta dimensão, horário e bioma.",
        "legend.available" to "§6[Lendário] §fDisponíveis nesta área: §e{species}",
        "legend.test.success" to "§a[NBP] Spawn lendário de teste realizado próximo a você.",
        "legend.test.failed" to "Não foi possível encontrar espécie/posição válida nesta dimensão e nos chunks carregados.",
        "legend.natural.success" to "§a[NBP] Ciclo natural executado; jogador e lendário foram sorteados.",
        "legend.natural.failed" to "O ciclo natural não encontrou jogador, espécie ou posição válida.",
        "legend.history.reset" to "§a[NBP] Histórico de lendários resetado.",
        "party.disabled" to "§c[NBP] O comando de cura de party está desativado no servidor.",
        "party.error" to "§c[NBP] Ocorreu um erro ao tentar curar sua equipe Pokémon."
        ,"capture_cap.too_high" to "§c[Limite de Captura] Esse Pokémon está no nível {level}, mas seu limite atual é {cap}."
        ,"capture_cap.current" to "§b[Limite de Captura] §fVocê pode capturar Pokémon até o nível §e{cap}§f."
        ,"capture_cap.increased" to "§a[Limite de Captura] Seu limite aumentou de §e{old} §apara §e{cap}§a."
        ,"capture_cap.maximum" to "§7[Limite de Captura] Seu limite já está no máximo: {cap}."
        ,"capture_cap.admin_set" to "§a[Limite de Captura] O limite efetivo de {player} agora é {cap}."
        ,"economy.capture_reward" to "§a+{amount} CobbleDollars §7(captura)"
        ,"economy.defeat_reward" to "§a+{amount} CobbleDollars §7(vitória)"
        ,"economy.cap_reached" to "§7[Economia] Seu limite diário de ganhos foi atingido."
        ,"economy.status" to "§6[Auditoria econômica] §fSaldo CobbleDollars: §a{balance} §7| §fCreditado hoje pelo NBP: §a{earned}§f / §e{cap}"
        ,"economy.reset" to "§a[Economia] Os dados econômicos diários de {player} foram resetados."
        ,"safari.disabled" to "§c[Safari] O sistema de Safari Zone está desativado neste servidor."
        ,"safari.active_session" to "§c[Safari] Você já possui uma sessão ativa na Safari Zone!"
        ,"safari.insufficient_balance" to "§c[Safari] Saldo insuficiente! O ticket do Safari custa §e{price} CobbleDollars§c."
        ,"safari.entered" to "§a[Safari] Bem-vindo à Safari Zone! Você foi enviado para uma localização aleatória e tem §e{minutes} minutos§a."
        ,"safari.no_active_session" to "§c[Safari] Você não possui uma sessão ativa na Safari Zone."
        ,"safari.voluntarily_exited" to "§e[Safari] Você saiu voluntariamente da Safari Zone."
        ,"safari.left_dimension" to "§c[Safari] Você saiu da dimensão do Safari. Sua sessão foi finalizada."
        ,"safari.setspawn_info" to "§a[Safari] A Safari Zone gera spawns 100% aleatórios para os jogadores ao entrarem."
        ,"safari.boundary_reached" to "§c[Safari] Você atingiu o limite de borda da Safari Zone (1.000 blocos)!"
        ,"safari.unauthorized_entry" to "§c[Safari] Entrada não autorizada! Você precisa comprar um ticket via /safari para acessar esta dimensão."
        ,"safari.beds_prohibited" to "§c[Safari] Camas e dormir são proibidos na Safari Zone!"
        ,"safari.gui_title" to "§2§lSafari"
        ,"safari.gui_title_p1" to "§2§lSafari (1/2)"
        ,"safari.gui_title_p2" to "§c§lSafari (2/2)"
        ,"safari.gui.next_page" to "§ePróxima Página -> (Nether & End)"
        ,"safari.gui.prev_page" to "§e<- Página Anterior (Overworld)"
        ,"safari.gui.click_to_enter" to "§eClique para entrar neste bioma!"
        ,"safari.gui.price" to "§7Custo: §a{price} CobbleDollars"
        ,"safari.gui.duration" to "§7Duração: §e{minutes} minutos"
        ,"safari.biome.random" to "§e§lBioma Aleatório"
        ,"safari.biome.plains" to "§aPlanície (Plains)"
        ,"safari.biome.sunflower_plains" to "§eCampo de Girassóis"
        ,"safari.biome.forest" to "§2Floresta (Forest)"
        ,"safari.biome.flower_forest" to "§dFloresta de Flores"
        ,"safari.biome.birch_forest" to "§fFloresta de Bétulas"
        ,"safari.biome.dark_forest" to "§8Floresta Escura"
        ,"safari.biome.taiga" to "§2Taiga"
        ,"safari.biome.snowy_taiga" to "§bTaiga Nevada"
        ,"safari.biome.jungle" to "§aSelva (Jungle)"
        ,"safari.biome.bamboo_jungle" to "§2Selva de Bambu"
        ,"safari.biome.savanna" to "§6Savana"
        ,"safari.biome.desert" to "§eDeserto"
        ,"safari.biome.badlands" to "§6Mesa / Badlands"
        ,"safari.biome.swamp" to "§2Pântano"
        ,"safari.biome.mangrove_swamp" to "§2Pântano de Mangue"
        ,"safari.biome.cherry_grove" to "§dBosque de Cerejeiras"
        ,"safari.biome.meadow" to "§aPrado (Meadow)"
        ,"safari.biome.grove" to "§fBosque Nevado"
        ,"safari.biome.snowy_slopes" to "§bEncostas Nevadas"
        ,"safari.biome.jagged_peaks" to "§bPicos Escarpados"
        ,"safari.biome.frozen_peaks" to "§bPicos Congelados"
        ,"safari.biome.stony_peaks" to "§7Picos Pedregosos"
        ,"safari.biome.windswept_hills" to "§7Colinas Ventosas"
        ,"safari.biome.ocean" to "§1Oceano"
        ,"safari.biome.warm_ocean" to "§bOceano Morno / Corais"
        ,"safari.biome.frozen_ocean" to "§bOceano Congelado"
        ,"safari.biome.beach" to "§ePraia"
        ,"safari.biome.snowy_beach" to "§bPraia Nevada"
        ,"safari.biome.stony_shore" to "§7Costa Rochosa"
        ,"safari.biome.river" to "§9Rio"
        ,"safari.biome.mushroom_fields" to "§cIlha dos Cogumelos"
        ,"safari.biome.lush_caves" to "§aCaverna Exuberante"
        ,"safari.biome.dripstone_caves" to "§7Caverna de Espeleotemas"
        ,"safari.biome.nether_wastes" to "§cDeserto do Nether §7(Especial)"
        ,"safari.biome.crimson_forest" to "§cFloresta Carmesim §7(Especial)"
        ,"safari.biome.warped_forest" to "§3Floresta Distorcida §7(Especial)"
        ,"safari.biome.soul_sand_valley" to "§bVale de Areia de Almas §7(Especial)"
        ,"safari.biome.basalt_deltas" to "§8Deltas de Basalto §7(Especial)"
        ,"safari.biome.the_end" to "§dO End §7(Especial)"
        ,"safari.biome.end_highlands" to "§dTerras Altas do End §7(Especial)"
        ,"safari.biome.end_midlands" to "§dTerras Médias do End §7(Especial)"
        ,"safari.biome.end_barrens" to "§dDesolamento do End §7(Especial)"
        ,"safari.biome.small_end_islands" to "§dPequenas Ilhas do End §7(Especial)"
    )
    private val ptTemplates = mapOf(
        "welcome.normal" to "§a[NBP] Bem-vindo ao servidor NBP Cobble Plus, {player}!",
        "welcome.first" to "§6[NBP] Seja muito bem-vindo pela primeira vez ao servidor, {player}!",
        "party.healed" to "§a[NBP] Sua equipe Pokémon foi totalmente curada!",
        "party.cooldown" to "§c[NBP] Aguarde {seconds} segundos para curar sua equipe novamente.",
        "broadcast.capture" to "§f[NBP] §e{player} §fcapturou §a{pokemon}§f! (Nível {level})",
        "broadcast.shiny" to "§6§l[NBP SHINY] §e{player} §fcapturou um Pokémon SHINY! §d★ {pokemon} ★ §f(Nível {level})!",
        "broadcast.starter" to "§a[NBP] §e{player} §fescolheu seu Pokémon inicial: §b{pokemon}§f!",
        "legend.spawn" to "§6[Lendário] §e{pokemon} §fsurgiu em §d{dimension} §fpróximo de §a{player}§f!"
        ,"announcer.0" to "§b[Dica NBP] Use §e/nbp help §bpara ver os comandos e ajuda do modpack!"
        ,"announcer.1" to "§b[Dica NBP] Participe da nossa comunidade e fique por dentro dos torneios e eventos!"
        ,"announcer.2" to "§b[Dica NBP] Respeite os outros treinadores e aproveite sua jornada Cobblemon!"
        ,"safari.entry_broadcast" to "§a[Safari] §e{player} §fentrou na Safari Zone!"
        ,"safari.exit_message" to "§c[Safari] Sua sessão na Safari Zone foi encerrada."
        ,"safari.action_bar" to "§a[Safari Zone] §fTime remaining: §e{time} §7| Use §c/nbp safari exit §7to leave"
        ,"wager.disabled" to "§c[Batalha Apostada] O sistema de apostas em batalhas está desativado."
        ,"wager.usage" to "§c[Batalha Apostada] Uso: /batalha <player> <quantia> ou /batalha aceitar | recusar"
        ,"wager.self_challenge" to "§c[Batalha Apostada] Você não pode desafiar a si mesmo!"
        ,"wager.target_not_found" to "§c[Batalha Apostada] O jogador '{player}' não foi encontrado ou está offline."
        ,"wager.min_max" to "§c[Batalha Apostada] O valor da aposta deve ser entre {min} e {max} CobbleDollars!"
        ,"wager.insufficient_sender" to "§c[Batalha Apostada] Você não possui {amount} CobbleDollars suficientes para esta aposta!"
        ,"wager.insufficient_target" to "§c[Batalha Apostada] O jogador {player} não possui {amount} CobbleDollars."
        ,"wager.pending_exists" to "§c[Batalha Apostada] Você já possui um desafio de aposta pendente!"
        ,"wager.challenge_sent" to "§a[Batalha Apostada] Desafio enviado para §e{player} §fno valor de §a{amount} CobbleDollars§f!"
        ,"wager.challenge_received" to "§6§l[DESAFIO DE APOSTA] §e{player} §fdesafiou você para uma batalha valendo §a{amount} CobbleDollars§f! Clique abaixo ou digite §c/batalha aceitar §fpara aceitar."
        ,"wager.click_to_accept" to "§e§l[CLIQUE AQUI PARA ACEITAR O DESAFIO]"
        ,"wager.no_pending" to "§c[Batalha Apostada] Você não possui desafios de aposta pendentes."
        ,"wager.denied_sender" to "§c[Batalha Apostada] Você recusou o desafio de {player}."
        ,"wager.denied_target" to "§c[Batalha Apostada] {player} recusou o seu desafio de aposta."
        ,"wager.accepted_start" to "§a[Batalha Apostada] Aposta aceita! §e{player1} §fvs §e{player2} §fvalendo §a{pool} CobbleDollars §f({amount} cada)! Iniciem a batalha!"
        ,"wager.victory_winner" to "§a§l[VITÓRIA NA APOSTA] §fVocê venceu a batalha apostada contra §e{loser} §fe ganhou §a+{amount} CobbleDollars§f!"
        ,"wager.defeat_loser" to "§c§l[DERROTA NA APOSTA] §fVocê perdeu a batalha apostada contra §e{winner} §fe perdeu §c-{amount} CobbleDollars§f."
        ,"wager.broadcast" to "§6§l[DESAFIO PVP] §e{winner} §fderrotou §e{loser} §fem uma batalha apostada e faturou §a{amount} CobbleDollars§f!"
        ,"wager.refund_draw" to "§e[Batalha Apostada] A batalha empatou/cancelou. Sua aposta de {amount} CobbleDollars foi reembolsada."
        ,"wager.not_nearby" to "§c[Batalha Apostada] Você precisa estar a no máximo {distance} blocos de {player}!"
    )

    fun bind(server: MinecraftServer) { data = LanguageSavedData.get(server) }
    fun unbind() { data = null }
    fun get(player: ServerPlayer): String = data?.languages?.get(player.uuid) ?: DEFAULT
    fun set(player: ServerPlayer, language: String): Boolean {
        val normalized = language.lowercase()
        if (normalized !in supported) return false
        data?.languages?.set(player.uuid, normalized)
        data?.setDirty()
        return true
    }
    fun text(player: ServerPlayer?, key: String, vararg values: Pair<String, Any>): Component =
        Component.literal(string(player, key, *values))
    fun string(player: ServerPlayer?, key: String, vararg values: Pair<String, Any>): String {
        val dictionary = if (player != null && get(player) == "pt_br") pt else en
        var result = dictionary[key] ?: en[key] ?: key
        values.forEach { (name, value) -> result = result.replace("{$name}", value.toString()) }
        return result
    }
    fun template(player: ServerPlayer, key: String, englishOrCustom: String, vararg values: Pair<String, Any>): String {
        var result = if (get(player) == "pt_br") ptTemplates[key] ?: englishOrCustom else englishOrCustom
        values.forEach { (name, value) -> result = result.replace("{$name}", value.toString()) }
        return result
    }
}

private class LanguageSavedData : SavedData() {
    val languages = mutableMapOf<UUID, String>()
    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        val values = CompoundTag()
        languages.forEach { (uuid, language) -> values.putString(uuid.toString(), language) }
        tag.put("languages", values)
        return tag
    }
    companion object {
        private const val NAME = "nbp_cobble_plus_player_languages"
        private fun load(tag: CompoundTag, registries: HolderLookup.Provider) = LanguageSavedData().also { data ->
            val values = tag.getCompound("languages")
            values.allKeys.forEach { key -> runCatching { UUID.fromString(key) }.getOrNull()?.let { data.languages[it] = values.getString(key) } }
        }
        private val FACTORY = Factory(::LanguageSavedData, ::load, DataFixTypes.LEVEL)
        fun get(server: MinecraftServer) = server.overworld().dataStorage.computeIfAbsent(FACTORY, NAME)
    }
}
