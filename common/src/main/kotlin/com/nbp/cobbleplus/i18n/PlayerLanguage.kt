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
        ,"points.status_header" to "§b--- Your NBP Points ---"
        ,"points.status_header_other" to "§b--- {player}'s NBP Points ---"
        ,"points.capture_reward" to "§a[Points] §fYou earned points for capturing §e{pokemon}§f!"
        ,"points.victory_reward" to "§a[Points] §fYou earned points for defeating a wild §e{pokemon}§f!"
        ,"points.breeding_reward" to "§a[Points] §f+{amount} Breeding point for hatching an egg!"
        ,"points.invalid_type" to "§c[Points] Invalid point type. Use §e/nbp points §fwith no arguments to see the valid ids."
        ,"points.pay_self" to "§c[Points] You cannot pay points to yourself."
        ,"points.pay_insufficient" to "§c[Points] You do not have enough points of that type."
        ,"points.pay_success" to "§a[Points] You sent §e{amount} {type} §ato §e{player}§a."
        ,"points.pay_received" to "§a[Points] You received §e{amount} {type} §afrom §e{player}§a."
        ,"points.admin_give" to "§a[Points] Gave §e{amount} {type} §ato §e{player}§a. New total: §f{total}"
        ,"points.admin_set" to "§a[Points] Set §e{player}§a's §e{type} §apoints to §f{total}§a."
        ,"points.admin_remove" to "§a[Points] Removed §e{amount} {type} §afrom §e{player}§a. New total: §f{total}"
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
        ,"points.status_header" to "§b--- Seus Pontos NBP ---"
        ,"points.status_header_other" to "§b--- Pontos NBP de {player} ---"
        ,"points.capture_reward" to "§a[Pontos] §fVocê ganhou pontos por capturar §e{pokemon}§f!"
        ,"points.victory_reward" to "§a[Pontos] §fVocê ganhou pontos por derrotar um §e{pokemon} §fselvagem!"
        ,"points.breeding_reward" to "§a[Pontos] §f+{amount} ponto de Reprodução por chocar um ovo!"
        ,"points.invalid_type" to "§c[Pontos] Tipo de ponto inválido. Use §e/nbp points §fsem argumentos para ver os ids válidos."
        ,"points.pay_self" to "§c[Pontos] Você não pode enviar pontos para si mesmo."
        ,"points.pay_insufficient" to "§c[Pontos] Você não possui pontos suficientes desse tipo."
        ,"points.pay_success" to "§a[Pontos] Você enviou §e{amount} {type} §apara §e{player}§a."
        ,"points.pay_received" to "§a[Pontos] Você recebeu §e{amount} {type} §ade §e{player}§a."
        ,"points.admin_give" to "§a[Pontos] Deu §e{amount} {type} §apara §e{player}§a. Novo total: §f{total}"
        ,"points.admin_set" to "§a[Pontos] Definiu os pontos de §e{type} §ade §e{player} §apara §f{total}§a."
        ,"points.admin_remove" to "§a[Pontos] Removeu §e{amount} {type} §ade §e{player}§a. Novo total: §f{total}"
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
