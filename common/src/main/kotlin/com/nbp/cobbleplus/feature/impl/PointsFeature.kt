package com.nbp.cobbleplus.feature.impl

import com.cobblemon.mod.common.api.battles.model.actor.ActorType
import com.cobblemon.mod.common.api.events.CobblemonEvents
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

enum class PointType(val id: String, private val labelEn: String, private val labelPt: String) {
    CAPTURE("capture", "Capture", "Captura"),
    VICTORY("victory", "Victory", "Vitória"),
    BREEDING("breeding", "Breeding", "Reprodução"),
    SHINY("shiny", "Shiny", "Shiny"),
    LEGENDARY("legendary", "Legendary", "Lendário"),
    MYTHICAL("mythical", "Mythical", "Mítico"),
    ULTRA_BEAST("ultra_beast", "Ultra Beast", "Ultra Beast"),
    TYPE_NORMAL("type_normal", "Normal", "Normal"),
    TYPE_FIRE("type_fire", "Fire", "Fogo"),
    TYPE_WATER("type_water", "Water", "Água"),
    TYPE_ELECTRIC("type_electric", "Electric", "Elétrico"),
    TYPE_GRASS("type_grass", "Grass", "Planta"),
    TYPE_ICE("type_ice", "Ice", "Gelo"),
    TYPE_FIGHTING("type_fighting", "Fighting", "Lutador"),
    TYPE_POISON("type_poison", "Poison", "Venenoso"),
    TYPE_GROUND("type_ground", "Ground", "Terrestre"),
    TYPE_FLYING("type_flying", "Flying", "Voador"),
    TYPE_PSYCHIC("type_psychic", "Psychic", "Psíquico"),
    TYPE_BUG("type_bug", "Bug", "Inseto"),
    TYPE_ROCK("type_rock", "Rock", "Pedra"),
    TYPE_GHOST("type_ghost", "Ghost", "Fantasma"),
    TYPE_DRAGON("type_dragon", "Dragon", "Dragão"),
    TYPE_DARK("type_dark", "Dark", "Sombrio"),
    TYPE_STEEL("type_steel", "Steel", "Aço"),
    TYPE_FAIRY("type_fairy", "Fairy", "Fada");

    fun displayName(player: ServerPlayer?): String =
        if (player != null && PlayerLanguage.get(player) == "pt_br") labelPt else labelEn

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
            if (config.showRewardActionBar) {
                event.player.displayClientMessage(
                    PlayerLanguage.text(event.player, "points.breeding_reward", "amount" to config.breedingAmount), true
                )
            }
        }
    }

    override fun onDisable() {
        subscriptions.forEach { it.unsubscribe() }
        subscriptions.clear()
    }

    fun bindServer(server: MinecraftServer) { store = PointsSavedData.get(server) }
    fun unbindServer() { store = null }

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
        if (config.showRewardActionBar) {
            val key = if (isCapture) "points.capture_reward" else "points.victory_reward"
            player.displayClientMessage(
                PlayerLanguage.text(player, key, "pokemon" to pokemon.species.resourceIdentifier.path), true
            )
        }
    }

    fun get(player: ServerPlayer, type: PointType): Long = requireStore().accounts[player.uuid]?.get(type.id) ?: 0L

    fun getAll(player: ServerPlayer): Map<PointType, Long> {
        val account = requireStore().accounts[player.uuid] ?: emptyMap()
        return PointType.entries.associateWith { account[it.id] ?: 0L }
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
