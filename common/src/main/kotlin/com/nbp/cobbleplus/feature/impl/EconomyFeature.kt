package com.nbp.cobbleplus.feature.impl

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.battles.model.actor.ActorType
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.pokemon.Pokemon
import com.nbp.cobbleplus.NbpCobblePlus
import com.nbp.cobbleplus.config.NbpConfig
import com.nbp.cobbleplus.feature.FeatureModule
import com.nbp.cobbleplus.i18n.PlayerLanguage
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.level.saveddata.SavedData
import java.math.BigInteger
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.math.roundToLong

object EconomyFeature : FeatureModule {
    override val name = "CobbleDollars economy"
    override val isEnabled get() = NbpConfig.data.economy.enabled
    private var subscribed = false
    private var nativeRewardsDisabled = false
    private var raidDensConfigured = false

    override fun onEnable() {
        if (subscribed) return
        subscribed = true
        CobblemonEvents.POKEMON_CAPTURED.subscribe { event ->
            if (!isEnabled || !NbpConfig.data.economy.rewardCaptures) return@subscribe
            reward(event.player, listOf(event.pokemon), "capture")
        }
        CobblemonEvents.BATTLE_VICTORY.subscribe { event ->
            val config = NbpConfig.data.economy
            if (!isEnabled || !config.rewardWildVictories || event.wasWildCapture) return@subscribe
            val wildLosers = event.losers.filter { it.type == ActorType.WILD }
                .flatMap { it.pokemonList }
            if (wildLosers.any(::isRaidBoss)) return@subscribe
            val defeatedPokemon = wildLosers.map { it.originalPokemon }
            if (defeatedPokemon.isEmpty()) return@subscribe
            event.winners.filterIsInstance<PlayerBattleActor>().mapNotNull { it.entity }
                .distinctBy { it.uuid }.forEach { reward(it, defeatedPokemon, "defeat") }
        }
    }

    override fun onDisable() = Unit

    fun tick(server: MinecraftServer) {
        if (!nativeRewardsDisabled && isEnabled && NbpConfig.data.economy.disableNativeCobbleDollarsRewards) {
            nativeRewardsDisabled = CobbleDollarsBridge.disableNativeRewards()
        }
        if (!raidDensConfigured && isEnabled && NbpConfig.data.economy.configureRaidDensRewards && server.tickCount % 200 == 0) {
            raidDensConfigured = RaidDensEconomyBridge.configure(NbpConfig.data.economy.raidDensRewardsByTier)
        }
    }

    private fun isRaidBoss(battlePokemon: Any): Boolean = runCatching {
        battlePokemon.javaClass.methods.firstOrNull { it.name == "crd_isRaidBoss" && it.parameterCount == 0 }
            ?.invoke(battlePokemon) as? Boolean ?: false
    }.getOrDefault(false)

    private fun reward(player: ServerPlayer, pokemon: List<Pokemon>, action: String) {
        val state = EconomySavedData.get(player.server)
        val account = state.account(player.uuid)
        account.rollDay()
        val now = System.currentTimeMillis()
        var requested = 0L
        pokemon.forEach { mon ->
            val species = mon.species.resourceIdentifier.path.lowercase()
            val repeat = account.repeatMultiplier(action, species, now)
            requested += (baseReward(mon, action) * repeat).roundToLong().coerceAtLeast(1L)
        }
        val paid = account.applyDailyLimits(requested)
        if (paid <= 0L) {
            if (NbpConfig.data.economy.showRewardActionBar)
                player.displayClientMessage(PlayerLanguage.text(player, "economy.cap_reached"), true)
            state.setDirty()
            return
        }
        val credited = CobbleDollarsBridge.earn(player, paid, NbpConfig.data.economy.applyCobbleDollarsGlobalMultiplier)
        if (credited <= 0L) return
        account.earnedToday += credited
        account.lifetimeEarned += credited
        state.setDirty()
        if (NbpConfig.data.economy.showRewardActionBar) {
            val key = if (action == "capture") "economy.capture_reward" else "economy.defeat_reward"
            player.displayClientMessage(PlayerLanguage.text(player, key, "amount" to credited), true)
        }
    }

    private fun baseReward(pokemon: Pokemon, action: String): Long {
        val config = NbpConfig.data.economy
        var value = if (action == "capture") config.captureBase + pokemon.level * config.capturePerLevel
            else config.defeatBase + pokemon.level * config.defeatPerLevel
        if (pokemon.shiny) value *= config.shinyMultiplier
        val species = pokemon.species.resourceIdentifier.path.lowercase()
        val legendary = species in config.legendaryBonusSpecies.map(String::lowercase) ||
            NbpConfig.data.legendarySpawner.legendaryPool.any { it.species.equals(species, true) }
        if (legendary) value *= config.legendaryMultiplier
        return value.roundToLong().coerceIn(1L, config.maximumRewardPerPokemon.coerceAtLeast(1L))
    }

    data class EconomyStatus(val balance: BigInteger, val earnedToday: Long, val dailyCap: Long)

    fun status(player: ServerPlayer): EconomyStatus {
        val data = EconomySavedData.get(player.server)
        val account = data.account(player.uuid)
        account.rollDay()
        data.setDirty()
        return EconomyStatus(CobbleDollarsBridge.balance(player), account.earnedToday, NbpConfig.data.economy.dailyEarningCap)
    }

    fun reset(player: ServerPlayer) {
        EconomySavedData.get(player.server).also { it.accounts.remove(player.uuid); it.setDirty() }
    }
}

private object RaidDensEconomyBridge {
    fun configure(rewards: List<Int>): Boolean = runCatching {
        if (rewards.size != 7) {
            NbpCobblePlus.logger.error("raidDensRewardsByTier must contain exactly seven values.")
            return false
        }
        val raidClass = Class.forName("com.necro.raid.dens.common.CobblemonRaidDens")
        val configs = raidClass.getField("TIER_CONFIG").get(null) as Map<*, *>
        if (configs.size < 7) return false
        configs.entries.forEach { (tier, config) ->
            val index = tierIndex(tier) ?: return@forEach
            config?.javaClass?.getField("currency")?.setInt(config, rewards[index].coerceAtLeast(0))
        }
        val registryClass = Class.forName("com.necro.raid.dens.common.registry.RaidRegistry")
        val bosses = registryClass.getField("RAID_LOOKUP").get(null) as Map<*, *>
        if (bosses.isEmpty()) return false
        var updated = 0
        bosses.values.forEach { boss ->
            if (boss == null) return@forEach
            val tier = boss.javaClass.getMethod("getTier").invoke(boss)
            val index = tierIndex(tier) ?: return@forEach
            boss.javaClass.getMethod("setCurrency", Integer::class.java).invoke(boss, rewards[index].coerceAtLeast(0))
            updated++
        }
        NbpCobblePlus.logger.info("Cobblemon Raid Dens CobbleDollars rewards configured for $updated registered bosses by tier: ${rewards.joinToString()}.")
        true
    }.onFailure {
        if (it !is ClassNotFoundException) NbpCobblePlus.logger.error("Could not configure Cobblemon Raid Dens rewards.", it)
    }.getOrDefault(false)

    private fun tierIndex(tier: Any?): Int? = when {
        tier == null -> null
        tier.toString().uppercase().endsWith("ONE") -> 0
        tier.toString().uppercase().endsWith("TWO") -> 1
        tier.toString().uppercase().endsWith("THREE") -> 2
        tier.toString().uppercase().endsWith("FOUR") -> 3
        tier.toString().uppercase().endsWith("FIVE") -> 4
        tier.toString().uppercase().endsWith("SIX") -> 5
        tier.toString().uppercase().endsWith("SEVEN") -> 6
        else -> null
    }
}

private object CobbleDollarsBridge {
    private val apiClass by lazy { Class.forName("fr.harmex.cobbledollars.common.utils.extensions.PlayerExtensionKt") }
    private val earnMethod by lazy {
        runCatching {
            apiClass.getMethod("earnCobbleDollars", net.minecraft.world.entity.player.Player::class.java,
                    BigInteger::class.java, Boolean::class.javaPrimitiveType)
        }.onFailure { NbpCobblePlus.logger.error("CobbleDollars API was not found; economy rewards are disabled.", it) }.getOrNull()
    }

    private val balanceMethod by lazy {
        apiClass.getMethod("getCobbleDollars", net.minecraft.world.entity.player.Player::class.java)
    }

    fun balance(player: ServerPlayer): BigInteger = runCatching {
        balanceMethod.invoke(null, player) as BigInteger
    }.onFailure { NbpCobblePlus.logger.error("Failed to read CobbleDollars balance for ${player.scoreboardName}", it) }.getOrDefault(BigInteger.ZERO)

    fun earn(player: ServerPlayer, amount: Long, multiplier: Boolean): Long = runCatching {
        val before = balance(player)
        val accepted = earnMethod?.invoke(null, player, BigInteger.valueOf(amount), multiplier) as? Boolean ?: false
        if (!accepted) 0L else balance(player).subtract(before).max(BigInteger.ZERO).min(BigInteger.valueOf(Long.MAX_VALUE)).toLong()
    }.onFailure { NbpCobblePlus.logger.error("Failed to credit CobbleDollars to ${player.scoreboardName}", it) }.getOrDefault(0L)

    fun disableNativeRewards(): Boolean = runCatching {
            val mainClass = Class.forName("fr.harmex.cobbledollars.common.CobbleDollars")
            val instance = mainClass.getField("INSTANCE").get(null)
            val config = mainClass.getMethod("getConfig").invoke(instance)
            val type = config.javaClass
            type.getMethod("setEarnCobbleDollarsFromNPC", Boolean::class.javaPrimitiveType).invoke(config, false)
            type.getMethod("setEarnCobbleDollarsFromWildPokemon", Boolean::class.javaPrimitiveType).invoke(config, false)
            type.getMethod("setEarnCobbleDollarsFromQuickBattle", Boolean::class.javaPrimitiveType).invoke(config, false)
            NbpCobblePlus.logger.info("CobbleDollars native NPC, wild and quick-battle rewards were disabled; NBP economy is authoritative.")
            true
        }.onFailure { NbpCobblePlus.logger.error("Could not disable CobbleDollars native rewards.", it) }.getOrDefault(false)
}

private data class RepeatCounter(var windowStart: Long = 0L, var count: Int = 0)

private class EconomyAccount {
    var day: Long = currentDay()
    var earnedToday: Long = 0L
    var lifetimeEarned: Long = 0L
    val repeats = mutableMapOf<String, RepeatCounter>()

    fun rollDay() {
        val today = currentDay()
        if (day != today) { day = today; earnedToday = 0L; repeats.clear() }
    }

    fun repeatMultiplier(action: String, species: String, now: Long): Double {
        val config = NbpConfig.data.economy
        val key = "$action:$species"
        val counter = repeats.getOrPut(key) { RepeatCounter(now, 0) }
        if (now - counter.windowStart >= config.repeatWindowMinutes.coerceAtLeast(1) * 60_000L) {
            counter.windowStart = now; counter.count = 0
        }
        counter.count++
        return if (counter.count <= config.fullRewardsPerSpeciesAndAction.coerceAtLeast(0)) 1.0
            else config.repeatedRewardMultiplier.coerceIn(0.0, 1.0)
    }

    fun applyDailyLimits(value: Long): Long {
        val config = NbpConfig.data.economy
        val cap = config.dailyEarningCap.coerceAtLeast(0L)
        val remaining = (cap - earnedToday).coerceAtLeast(0L)
        if (remaining == 0L) return 0L
        val softStart = config.dailySoftCapStart.coerceIn(0L, cap)
        val beforeSoft = (softStart - earnedToday).coerceAtLeast(0L).coerceAtMost(value)
        val afterSoft = (value - beforeSoft).coerceAtLeast(0L)
        val adjusted = beforeSoft + (afterSoft * config.softCapMultiplier.coerceIn(0.0, 1.0)).roundToLong()
        return adjusted.coerceAtMost(remaining)
    }

    companion object { private fun currentDay() = LocalDate.now(ZoneOffset.UTC).toEpochDay() }
}

private class EconomySavedData : SavedData() {
    val accounts = mutableMapOf<UUID, EconomyAccount>()
    fun account(uuid: UUID) = accounts.getOrPut(uuid) { EconomyAccount() }

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        val all = CompoundTag()
        accounts.forEach { (uuid, account) ->
            val value = CompoundTag()
            value.putLong("day", account.day); value.putLong("earned", account.earnedToday)
            value.putLong("lifetime", account.lifetimeEarned)
            val repeats = CompoundTag()
            account.repeats.forEach { (key, counter) -> repeats.put(key, CompoundTag().also {
                it.putLong("start", counter.windowStart); it.putInt("count", counter.count)
            }) }
            value.put("repeats", repeats); all.put(uuid.toString(), value)
        }
        tag.put("accounts", all); return tag
    }

    companion object {
        private const val NAME = "nbp_cobble_plus_economy"
        private fun load(tag: CompoundTag, registries: HolderLookup.Provider) = EconomySavedData().also { data ->
            val all = tag.getCompound("accounts")
            all.allKeys.forEach { id -> runCatching { UUID.fromString(id) }.getOrNull()?.let { uuid ->
                val value = all.getCompound(id); val account = EconomyAccount()
                account.day = value.getLong("day"); account.earnedToday = value.getLong("earned")
                account.lifetimeEarned = value.getLong("lifetime")
                val repeats = value.getCompound("repeats")
                repeats.allKeys.forEach { key -> repeats.getCompound(key).let {
                    account.repeats[key] = RepeatCounter(it.getLong("start"), it.getInt("count"))
                } }
                data.accounts[uuid] = account
            } }
        }
        private val FACTORY = Factory(::EconomySavedData, ::load, DataFixTypes.LEVEL)
        fun get(server: MinecraftServer) = server.overworld().dataStorage.computeIfAbsent(FACTORY, NAME)
    }
}
