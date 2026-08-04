package com.nbp.cobbleplus.feature.impl

import com.cobblemon.mod.common.api.battles.model.actor.ActorType
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.api.reactive.ObservableSubscription
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.pokemon.Pokemon
import com.nbp.cobbleplus.config.NbpConfig
import com.nbp.cobbleplus.feature.FeatureModule
import com.nbp.cobbleplus.i18n.PlayerLanguage
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

enum class PointType(val id: String, private val labelEn: String, private val labelPt: String, val color: String) {
    CAPTURE("capture", "Capture", "Captura", "§a"),
    VICTORY("victory", "Victory", "Vitória", "§6"),
    BREEDING("breeding", "Breeding", "Reprodução", "§d"),
    SHINY("shiny", "Shiny", "Shiny", "§e"),
    LEGENDARY("legendary", "Legendary", "Lendário", "§5"),
    MYTHICAL("mythical", "Mythical", "Mítico", "§c"),
    ULTRA_BEAST("ultra_beast", "Ultra Beast", "Ultra Beast", "§b"),
    TYPE_NORMAL("type_normal", "Normal", "Normal", "§7"),
    TYPE_FIRE("type_fire", "Fire", "Fogo", "§6"),
    TYPE_WATER("type_water", "Water", "Água", "§9"),
    TYPE_ELECTRIC("type_electric", "Electric", "Elétrico", "§e"),
    TYPE_GRASS("type_grass", "Grass", "Planta", "§2"),
    TYPE_ICE("type_ice", "Ice", "Gelo", "§b"),
    TYPE_FIGHTING("type_fighting", "Fighting", "Lutador", "§4"),
    TYPE_POISON("type_poison", "Poison", "Venenoso", "§5"),
    TYPE_GROUND("type_ground", "Ground", "Terrestre", "§6"),
    TYPE_FLYING("type_flying", "Flying", "Voador", "§3"),
    TYPE_PSYCHIC("type_psychic", "Psychic", "Psíquico", "§d"),
    TYPE_BUG("type_bug", "Bug", "Inseto", "§a"),
    TYPE_ROCK("type_rock", "Rock", "Pedra", "§8"),
    TYPE_GHOST("type_ghost", "Ghost", "Fantasma", "§1"),
    TYPE_DRAGON("type_dragon", "Dragon", "Dragão", "§9"),
    TYPE_DARK("type_dark", "Dark", "Sombrio", "§8"),
    TYPE_STEEL("type_steel", "Steel", "Aço", "§7"),
    TYPE_FAIRY("type_fairy", "Fairy", "Fada", "§d");

    fun displayName(portuguese: Boolean): String = if (portuguese) labelPt else labelEn

    fun displayName(player: ServerPlayer?): String =
        displayName(player != null && PlayerLanguage.get(player) == "pt_br")

    companion object {
        fun fromId(id: String): PointType? = entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
        fun typeOf(elementalTypeName: String): PointType? = fromId("type_${elementalTypeName.lowercase()}")
    }
}

object PointsFeature : FeatureModule {
    override val name = "Points system"
    override val isEnabled get() = NbpConfig.data.points.enabled
    private val subscriptions = mutableListOf<ObservableSubscription<*>>()
    private var store: PointsSavedData? = null
    private var serverRef: MinecraftServer? = null

    /** Envia a linha de recompensa (texto pronto, com códigos de cor `§`) pro HUD do cliente. Ligado por cada plataforma. */
    var networkSender: (ServerPlayer, String, Int) -> Unit = { _, _, _ -> }

    /** Envia o extrato completo (idioma resolvido + valores em ordem de [PointType.entries]) pra abrir a tela de pontos. */
    var viewNetworkSender: (ServerPlayer, Boolean, LongArray) -> Unit = { _, _, _ -> }

    override fun onEnable() {
        if (subscriptions.isNotEmpty()) return
        subscriptions += CobblemonEvents.POKEMON_CAPTURED.subscribe { event ->
            if (!isEnabled || !NbpConfig.data.points.rewardCaptures) return@subscribe
            grant(event.player, event.pokemon, isCapture = true)
        }
        subscriptions += CobblemonEvents.BATTLE_VICTORY.subscribe { event ->
            val config = NbpConfig.data.points
            if (!isEnabled || !config.rewardWildVictories || event.wasWildCapture) return@subscribe
            val wildLosers = event.losers.filter { it.type == ActorType.WILD }.flatMap { it.pokemonList }
            if (wildLosers.any(::isRaidBoss)) return@subscribe
            val defeated = wildLosers.map { it.originalPokemon }
            if (defeated.isEmpty()) return@subscribe
            event.winners.filterIsInstance<PlayerBattleActor>().mapNotNull { it.entity }
                .distinctBy { it.uuid }
                .forEach { winner -> defeated.forEach { mon -> grant(winner, mon, isCapture = false) } }
        }
        subscriptions += CobblemonEvents.HATCH_EGG_POST.subscribe { event ->
            val config = NbpConfig.data.points
            if (!isEnabled || !config.rewardBreeding) return@subscribe
            add(event.player, PointType.BREEDING, config.breedingAmount)
            showReward(event.player, listOf(PointType.BREEDING to config.breedingAmount))
        }
        subscriptions += CobblemonEvents.POKEDEX_DATA_CHANGED_POST.subscribe { event ->
            val config = NbpConfig.data.points
            // The dex only ever transitions a given species+form to ENCOUNTERED once, so this
            // naturally grants type points a single time per species and variation scanned.
            if (!isEnabled || !config.rewardPokedexScans || event.knowledge != PokedexEntryProgress.ENCOUNTERED) return@subscribe
            val player = serverRef?.playerList?.getPlayer(event.playerUUID) ?: return@subscribe
            val types = event.dataSource.pokemon.types.mapNotNull { PointType.typeOf(it.name) }.distinct()
            if (types.isEmpty()) return@subscribe
            val gains = types.map { it to config.typePointsOnScanAmount }
            gains.forEach { (type, amount) -> add(player, type, amount) }
            showReward(player, gains)
        }
    }

    override fun onDisable() {
        subscriptions.forEach { it.unsubscribe() }
        subscriptions.clear()
    }

    fun bindServer(server: MinecraftServer) {
        store = PointsSavedData.get(server)
        serverRef = server
    }

    fun unbindServer() {
        store = null
        serverRef = null
    }

    private fun isRaidBoss(battlePokemon: Any): Boolean = runCatching {
        battlePokemon.javaClass.methods.firstOrNull { it.name == "crd_isRaidBoss" && it.parameterCount == 0 }
            ?.invoke(battlePokemon) as? Boolean ?: false
    }.getOrDefault(false)

    private fun grant(player: ServerPlayer, pokemon: Pokemon, isCapture: Boolean) {
        val config = NbpConfig.data.points
        val gains = mutableListOf<Pair<PointType, Long>>()
        gains += if (isCapture) PointType.CAPTURE to config.captureAmount else PointType.VICTORY to config.victoryAmount
        val typeAmount = if (isCapture) config.typePointsOnCaptureAmount else config.typePointsOnVictoryAmount
        pokemon.types.mapNotNull { PointType.typeOf(it.name) }.distinct().forEach { gains += it to typeAmount }
        if (pokemon.shiny) gains += PointType.SHINY to config.shinyAmount
        if (pokemon.isLegendary()) gains += PointType.LEGENDARY to config.legendaryAmount
        if (pokemon.isMythical()) gains += PointType.MYTHICAL to config.mythicalAmount
        if (pokemon.isUltraBeast()) gains += PointType.ULTRA_BEAST to config.ultraBeastAmount
        gains.forEach { (type, amount) -> add(player, type, amount) }
        showReward(player, gains)
    }

    private fun showReward(player: ServerPlayer, gains: List<Pair<PointType, Long>>) {
        if (gains.isEmpty() || !NbpConfig.data.points.showRewardBar) return
        val text = gains.joinToString(" ") { (type, amount) -> "${type.color}+$amount ${type.displayName(player)}" }
        val durationTicks = NbpConfig.data.points.rewardBarDurationTicks.let { if (it <= 0) 60 else it }
        networkSender(player, text, durationTicks)
    }

    fun get(player: ServerPlayer, type: PointType): Long = requireStore().accounts[player.uuid]?.get(type.id) ?: 0L

    fun getAll(player: ServerPlayer): Map<PointType, Long> {
        val account = requireStore().accounts[player.uuid] ?: emptyMap()
        return PointType.entries.associateWith { account[it.id] ?: 0L }
    }

    /** Pede pro cliente desse jogador abrir a tela com o extrato completo de pontos. */
    fun openView(player: ServerPlayer) {
        val portuguese = PlayerLanguage.get(player) == "pt_br"
        val values = getAll(player).values.toLongArray()
        viewNetworkSender(player, portuguese, values)
    }

    fun add(player: ServerPlayer, type: PointType, amount: Long) {
        if (amount == 0L) return
        val store = requireStore()
        val account = store.accounts.getOrPut(player.uuid) { mutableMapOf() }
        account[type.id] = ((account[type.id] ?: 0L) + amount).coerceAtLeast(0L)
        store.setDirty()
    }

    fun set(player: ServerPlayer, type: PointType, amount: Long) {
        val store = requireStore()
        store.accounts.getOrPut(player.uuid) { mutableMapOf() }[type.id] = amount.coerceAtLeast(0L)
        store.setDirty()
    }

    fun remove(player: ServerPlayer, type: PointType, amount: Long) = add(player, type, -amount)

    fun pay(from: ServerPlayer, to: ServerPlayer, type: PointType, amount: Long): Boolean {
        if (amount <= 0L || get(from, type) < amount) return false
        add(from, type, -amount)
        add(to, type, amount)
        return true
    }

    private fun requireStore(): PointsSavedData = checkNotNull(store) { "Points store is not bound to a server" }
}

private class PointsSavedData : SavedData() {
    val accounts = mutableMapOf<UUID, MutableMap<String, Long>>()

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        val all = CompoundTag()
        accounts.forEach { (uuid, points) ->
            val value = CompoundTag()
            points.forEach { (type, amount) -> value.putLong(type, amount) }
            all.put(uuid.toString(), value)
        }
        tag.put("players", all)
        return tag
    }

    companion object {
        private const val NAME = "nbp_cobble_plus_points"
        private fun load(tag: CompoundTag, registries: HolderLookup.Provider) = PointsSavedData().also { data ->
            val all = tag.getCompound("players")
            all.allKeys.forEach { id ->
                runCatching { UUID.fromString(id) }.getOrNull()?.let { uuid ->
                    val value = all.getCompound(id)
                    val points = mutableMapOf<String, Long>()
                    value.allKeys.forEach { key -> points[key] = value.getLong(key) }
                    data.accounts[uuid] = points
                }
            }
        }
        private val FACTORY = Factory(::PointsSavedData, ::load, DataFixTypes.LEVEL)
        fun get(server: MinecraftServer): PointsSavedData = server.overworld().dataStorage.computeIfAbsent(FACTORY, NAME)
    }
}
