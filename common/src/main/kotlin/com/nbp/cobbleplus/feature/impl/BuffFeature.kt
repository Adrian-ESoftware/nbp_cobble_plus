package com.nbp.cobbleplus.feature.impl

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.reactive.ObservableSubscription
import com.cobblemon.mod.common.api.spawning.influence.SpawningInfluence
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.abilities.HiddenAbility
import com.cobblemon.mod.common.util.spawner
import com.nbp.cobbleplus.NbpCobblePlus
import com.nbp.cobbleplus.config.NbpConfig
import com.nbp.cobbleplus.feature.FeatureModule
import com.nbp.cobbleplus.i18n.PlayerLanguage
import com.nbp.cobbleplus.network.ActiveBuffSync
import com.nbp.cobbleplus.network.BuffShopSyncPayload
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.level.saveddata.SavedData
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.random.Random

data class ActiveBuffState(
    val buffId: String,
    var level: Int,
    var remainingSeconds: Int,
    var flyGranted: Boolean = false
)

object BuffFeature : FeatureModule {
    override val name = "Buffs & Points Shop"
    override val isEnabled: Boolean get() = NbpConfig.data.buffs.enabled

    private val logger = LoggerFactory.getLogger("NBP-Buffs")
    private val subscriptions = mutableListOf<ObservableSubscription<*>>()
    private var store: BuffSavedData? = null
    private var serverRef: MinecraftServer? = null
    private val influenceAttachedPlayers = mutableSetOf<UUID>()

    private val SPEED_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, "buff_speed")
    private val STRENGTH_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, "buff_strength")
    private val RESISTANCE_ARMOR_ID = ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, "buff_resistance_armor")
    private val RESISTANCE_TOUGHNESS_ID = ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, "buff_resistance_toughness")
    private val RESISTANCE_KNOCKBACK_ID = ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, "buff_resistance_knockback")
    private val REGEN_MAX_HEALTH_ID = ResourceLocation.fromNamespaceAndPath(NbpCobblePlus.MOD_ID, "buff_regen_max_health")

    /** Platform hook to send BuffShopSyncPayload to player. Registered in Fabric and NeoForge. */
    var shopViewNetworkSender: (ServerPlayer, BuffShopSyncPayload) -> Unit = { _, _ -> }

    override fun onEnable() {
        if (subscriptions.isNotEmpty()) return
        registerCobblemonHooks()
    }

    override fun onDisable() {
        subscriptions.forEach { it.unsubscribe() }
        subscriptions.clear()
        influenceAttachedPlayers.clear()
        serverRef?.playerList?.players?.forEach { player ->
            removeAllBuffAttributes(player)
        }
    }

    fun bindServer(server: MinecraftServer) {
        store = BuffSavedData.get(server)
        serverRef = server
    }

    fun unbindServer() {
        store?.setDirty()
        store = null
        serverRef = null
        influenceAttachedPlayers.clear()
    }

    fun getActiveBuff(playerUuid: UUID, buffId: String): ActiveBuffState? =
        store?.accounts?.get(playerUuid)?.get(buffId)

    fun getAllActiveBuffs(playerUuid: UUID): Map<String, ActiveBuffState> =
        store?.accounts?.get(playerUuid) ?: emptyMap()

    fun handlePlayerJoin(player: ServerPlayer) {
        attachSpawningInfluence(player)
        applyPlayerAttributes(player)
        val active = getAllActiveBuffs(player.uuid)
        if (active.containsKey("fly")) {
            player.abilities.mayfly = true
            player.onUpdateAbilities()
        }
    }

    fun openShop(player: ServerPlayer) {
        val pt = PlayerLanguage.get(player) == "pt_br"
        val balances = PointsFeature.getAll(player).mapKeys { it.key.id }
        val activeList = getAllActiveBuffs(player.uuid).values.map {
            ActiveBuffSync(it.buffId, it.level, it.remainingSeconds)
        }
        val payload = BuffShopSyncPayload(pt, balances, activeList)
        shopViewNetworkSender(player, payload)
    }

    fun purchase(player: ServerPlayer, buffId: String, level: Int): Boolean {
        val pt = PlayerLanguage.get(player) == "pt_br"
        if (!isEnabled) {
            val msg = if (pt) "§cO sistema de buffs está desativado." else "§cThe buffs system is disabled."
            player.sendSystemMessage(Component.literal(msg))
            return false
        }
        val buff = BuffCatalog.get(buffId) ?: run {
            val msg = if (pt) "§cBuff desconhecido: $buffId" else "§cUnknown buff: $buffId"
            player.sendSystemMessage(Component.literal(msg))
            return false
        }
        if (level < 1 || level > buff.maxLevel) {
            val msg = if (pt) "§cNível inválido: $level" else "§cInvalid level: $level"
            player.sendSystemMessage(Component.literal(msg))
            return false
        }

        val cost = buff.cost(level)
        val balance = PointsFeature.get(player, buff.pointType)
        if (balance < cost) {
            val typeName = buff.pointType.displayName(pt)
            val msg = if (pt) {
                "§cSaldo insuficiente de pontos de $typeName. Você tem §e$balance§c, necessário: §e$cost§c."
            } else {
                "§cInsufficient $typeName points. You have §e$balance§c, required: §e$cost§c."
            }
            player.sendSystemMessage(Component.literal(msg))
            return false
        }

        val durationSec = buff.durationSeconds(level)
        val currentStore = store ?: run {
            player.sendSystemMessage(Component.literal("§cServidor ainda não carregou os dados de buffs."))
            return false
        }

        val playerBuffs = currentStore.accounts.getOrPut(player.uuid) { mutableMapOf() }
        val previous = playerBuffs[buffId]

        // Deduct points safely
        PointsFeature.remove(player, buff.pointType, cost)

        val flyGranted = (buff.id == "fly") || (previous?.flyGranted ?: false)
        val newState = ActiveBuffState(buffId, level, durationSec, flyGranted)
        playerBuffs[buffId] = newState
        currentStore.setDirty()

        // Apply immediately
        applyPlayerAttributes(player)
        if (buff.id == "fly") {
            player.abilities.mayfly = true
            player.onUpdateAbilities()
        }

        val buffName = buff.displayName(pt)
        val romanLevel = if (buff.maxLevel > 1) " " + roman(level) else ""
        val typeName = buff.pointType.displayName(pt)
        val successMsg = if (pt) {
            "§aBuff ativado: §f$buffName$romanLevel §a(30m) por §e$cost §apontos de $typeName!"
        } else {
            "§aBuff activated: §f$buffName$romanLevel §a(30m) for §e$cost §a$typeName points!"
        }
        player.sendSystemMessage(Component.literal(successMsg))

        // Refresh UI
        openShop(player)
        return true
    }

    fun tick(server: MinecraftServer) {
        if (!isEnabled) return
        val currentStore = store ?: return

        server.playerList.players.forEach { player ->
            val buffs = currentStore.accounts[player.uuid] ?: return@forEach

            // Passive regeneration healing based on level
            val regenBuff = buffs["regeneration"]
            if (regenBuff != null) {
                val interval = when (regenBuff.level) {
                    1 -> 50
                    2 -> 40
                    3 -> 30
                    4 -> 20
                    else -> 10
                }
                if (server.tickCount % interval == 0 && player.health < player.maxHealth) {
                    player.heal(1.0f)
                }
            }

            // Once per second: decrement timers, ensure modifiers and creative flight
            if (server.tickCount % 20 == 0) {
                attachSpawningInfluence(player)
                applyPlayerAttributes(player)

                if (buffs.containsKey("fly") && !player.abilities.mayfly) {
                    player.abilities.mayfly = true
                    player.onUpdateAbilities()
                }

                var hasActive = false
                val toRemove = mutableListOf<String>()
                buffs.values.forEach { buff ->
                    buff.remainingSeconds--
                    hasActive = true
                    if (buff.remainingSeconds <= 0) {
                        toRemove.add(buff.buffId)
                    }
                }

                if (hasActive) {
                    currentStore.setDirty()
                }

                if (toRemove.isNotEmpty()) {
                    toRemove.forEach { buffId ->
                        val removed = buffs.remove(buffId)
                        if (removed != null) {
                            expireBuff(player, removed)
                        }
                    }
                    openShop(player)
                }
            }
        }
    }

    private fun expireBuff(player: ServerPlayer, state: ActiveBuffState) {
        val pt = PlayerLanguage.get(player) == "pt_br"
        val buff = BuffCatalog.get(state.buffId)
        val buffName = buff?.displayName(pt) ?: state.buffId

        when (state.buffId) {
            "fly" -> {
                if (state.flyGranted && !player.isCreative && !player.isSpectator) {
                    player.abilities.mayfly = false
                    player.abilities.flying = false
                    player.onUpdateAbilities()

                    // Air safety: apply slow falling and reset fall distance if in the air
                    if (!player.onGround()) {
                        player.addEffect(MobEffectInstance(MobEffects.SLOW_FALLING, 600, 0, false, false, true))
                        player.resetFallDistance()
                        val safetyMsg = if (pt) {
                            "§e[NBP] Seu voo criativo expirou. Queda lenta concedida por 30s para sua segurança!"
                        } else {
                            "§e[NBP] Your creative flight expired. Slow Falling granted for 30s for your safety!"
                        }
                        player.sendSystemMessage(Component.literal(safetyMsg))
                    }
                }
            }
            "speed", "strength", "resistance", "regeneration" -> {
                applyPlayerAttributes(player)
            }
        }

        val msg = if (pt) "§e[NBP] O buff §f$buffName §eexpirou." else "§e[NBP] Buff §f$buffName §ehas expired."
        player.sendSystemMessage(Component.literal(msg))
    }

    fun applyPlayerAttributes(player: ServerPlayer) {
        val active = getAllActiveBuffs(player.uuid)

        // Speed: Attributes.MOVEMENT_SPEED (+20% to +100%)
        val speedBuff = active["speed"]
        val speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED)
        if (speedBuff != null) {
            val amount = BuffCatalog.SPEED.effectValue(speedBuff.level)
            val mod = AttributeModifier(SPEED_MODIFIER_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE)
            speedAttr?.addOrUpdateTransientModifier(mod)
        } else {
            speedAttr?.removeModifier(SPEED_MODIFIER_ID)
        }

        // Strength: Attributes.ATTACK_DAMAGE (+3 to +15)
        val strengthBuff = active["strength"]
        val strengthAttr = player.getAttribute(Attributes.ATTACK_DAMAGE)
        if (strengthBuff != null) {
            val amount = BuffCatalog.STRENGTH.effectValue(strengthBuff.level)
            val mod = AttributeModifier(STRENGTH_MODIFIER_ID, amount, AttributeModifier.Operation.ADD_VALUE)
            strengthAttr?.addOrUpdateTransientModifier(mod)
        } else {
            strengthAttr?.removeModifier(STRENGTH_MODIFIER_ID)
        }

        // Resistance: Armor (+4..20), Toughness (+2..10), Knockback (+0.1..0.5)
        val resBuff = active["resistance"]
        val armorAttr = player.getAttribute(Attributes.ARMOR)
        val toughAttr = player.getAttribute(Attributes.ARMOR_TOUGHNESS)
        val knockAttr = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE)
        if (resBuff != null) {
            val lvl = resBuff.level
            val armorMod = AttributeModifier(RESISTANCE_ARMOR_ID, 4.0 * lvl, AttributeModifier.Operation.ADD_VALUE)
            val toughMod = AttributeModifier(RESISTANCE_TOUGHNESS_ID, 2.0 * lvl, AttributeModifier.Operation.ADD_VALUE)
            val knockMod = AttributeModifier(RESISTANCE_KNOCKBACK_ID, 0.10 * lvl, AttributeModifier.Operation.ADD_VALUE)
            armorAttr?.addOrUpdateTransientModifier(armorMod)
            toughAttr?.addOrUpdateTransientModifier(toughMod)
            knockAttr?.addOrUpdateTransientModifier(knockMod)
        } else {
            armorAttr?.removeModifier(RESISTANCE_ARMOR_ID)
            toughAttr?.removeModifier(RESISTANCE_TOUGHNESS_ID)
            knockAttr?.removeModifier(RESISTANCE_KNOCKBACK_ID)
        }

        // Regeneration: Max Health (+4..20 HP / +2..10 hearts)
        val regenBuff = active["regeneration"]
        val healthAttr = player.getAttribute(Attributes.MAX_HEALTH)
        if (regenBuff != null) {
            val lvl = regenBuff.level
            val healthMod = AttributeModifier(REGEN_MAX_HEALTH_ID, 4.0 * lvl, AttributeModifier.Operation.ADD_VALUE)
            healthAttr?.addOrUpdateTransientModifier(healthMod)
        } else {
            healthAttr?.removeModifier(REGEN_MAX_HEALTH_ID)
        }
    }

    fun removeAllBuffAttributes(player: ServerPlayer) {
        player.getAttribute(Attributes.MOVEMENT_SPEED)?.removeModifier(SPEED_MODIFIER_ID)
        player.getAttribute(Attributes.ATTACK_DAMAGE)?.removeModifier(STRENGTH_MODIFIER_ID)
        player.getAttribute(Attributes.ARMOR)?.removeModifier(RESISTANCE_ARMOR_ID)
        player.getAttribute(Attributes.ARMOR_TOUGHNESS)?.removeModifier(RESISTANCE_TOUGHNESS_ID)
        player.getAttribute(Attributes.KNOCKBACK_RESISTANCE)?.removeModifier(RESISTANCE_KNOCKBACK_ID)
        player.getAttribute(Attributes.MAX_HEALTH)?.removeModifier(REGEN_MAX_HEALTH_ID)
    }

    private fun attachSpawningInfluence(player: ServerPlayer) {
        if (!influenceAttachedPlayers.add(player.uuid)) return
        runCatching {
            @Suppress("UNCHECKED_CAST")
            val influences = player.spawner.influences as? MutableList<SpawningInfluence> ?: return
            influences.add(BuffSpawningInfluence(player.uuid))
        }.onFailure {
            influenceAttachedPlayers.remove(player.uuid)
        }
    }

    private class BuffSpawningInfluence(private val playerUuid: UUID) : SpawningInfluence {
        override fun affectBucketWeights(bucketWeights: MutableMap<String, Float>) {
            val active = getActiveBuff(playerUuid, "rare_spawn") ?: return
            val bonus = BuffCatalog.RARE_SPAWN.effectValue(active.level).toFloat()
            val multiplier = 1.0f + bonus
            val targets = NbpConfig.data.buffs.rareSpawnBuckets
            targets.forEach { bucket ->
                if (bucketWeights.containsKey(bucket)) {
                    bucketWeights[bucket] = bucketWeights.getValue(bucket) * multiplier
                }
            }
        }
    }

    private fun registerCobblemonHooks() {
        // Battle EXP buff
        subscriptions += CobblemonEvents.EXPERIENCE_GAINED_EVENT_PRE.subscribe { event ->
            if (!isEnabled || !event.source.isBattle()) return@subscribe
            val player = event.pokemon.getOwnerPlayer() ?: return@subscribe
            val active = getActiveBuff(player.uuid, "battle_exp") ?: return@subscribe
            val bonus = BuffCatalog.BATTLE_EXP.effectValue(active.level)
            event.experience = (event.experience * (1.0 + bonus)).toInt().coerceAtLeast(0)
        }

        // Catch Rate buff
        subscriptions += CobblemonEvents.POKEMON_CATCH_RATE.subscribe { event ->
            if (!isEnabled) return@subscribe
            val player = event.thrower as? ServerPlayer ?: return@subscribe
            val active = getActiveBuff(player.uuid, "catch_rate") ?: return@subscribe
            val bonus = BuffCatalog.CATCH_RATE.effectValue(active.level).toFloat()
            event.catchRate *= (1.0f + bonus)
        }

        // Shiny Rate calculation hook
        subscriptions += CobblemonEvents.SHINY_CHANCE_CALCULATION.subscribe { event ->
            if (!isEnabled) return@subscribe
            event.addModificationFunction { chance, player, _ ->
                if (player == null) return@addModificationFunction chance
                val active = getActiveBuff(player.uuid, "shiny_rate") ?: return@addModificationFunction chance
                val bonus = BuffCatalog.SHINY_RATE.effectValue(active.level)
                val multiplier = 1.0 + bonus
                if (multiplier <= 1.0) {
                    chance
                } else if (chance > 1.0f) {
                    (chance / multiplier.toFloat()).coerceAtLeast(1.0f)
                } else {
                    (chance * multiplier.toFloat()).coerceAtMost(1.0f)
                }
            }
        }

        // Entity spawn hook (Personal Shiny boost fallback & Hidden Ability boost)
        subscriptions += CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe { event ->
            if (!isEnabled) return@subscribe
            val player = event.spawnablePosition.cause.entity as? ServerPlayer
                ?: findNearestPlayer(event.entity) ?: return@subscribe

            // Shiny boost on spawn
            val shinyBuff = getActiveBuff(player.uuid, "shiny_rate")
            if (shinyBuff != null && !event.entity.pokemon.shiny) {
                val base = Cobblemon.config.shinyRate.toDouble().coerceAtLeast(1.0)
                val bonus = BuffCatalog.SHINY_RATE.effectValue(shinyBuff.level)
                val currentChance = 1.0 / base
                val targetChance = (1.0 + bonus) / base
                val conditional = ((targetChance - currentChance) / (1.0 - currentChance)).coerceIn(0.0, 1.0)
                if (Random.nextDouble() < conditional) {
                    event.entity.pokemon.shiny = true
                }
            }

            // Hidden ability boost on spawn
            val haBuff = getActiveBuff(player.uuid, "hidden_ability")
            if (haBuff != null) {
                applyHiddenAbility(event.entity.pokemon, haBuff.level)
            }
        }

        // Item drops buff
        subscriptions += CobblemonEvents.LOOT_DROPPED.subscribe { event ->
            if (!isEnabled) return@subscribe
            val player = event.player ?: return@subscribe
            val active = getActiveBuff(player.uuid, "item_drops") ?: return@subscribe
            val bonus = BuffCatalog.ITEM_DROPS.effectValue(active.level)
            if (bonus <= 0.0) return@subscribe

            val originalDrops = event.drops.toList()
            val wholeMultiplier = bonus.toInt()
            val fractionalChance = bonus - wholeMultiplier

            originalDrops.forEach { drop ->
                repeat(wholeMultiplier) {
                    event.drops.add(drop)
                }
                if (fractionalChance > 0.0 && Random.nextDouble() < fractionalChance) {
                    event.drops.add(drop)
                }
            }
        }

        // EV Gain buff
        subscriptions += CobblemonEvents.EV_GAINED_EVENT_PRE.subscribe { event ->
            if (!isEnabled) return@subscribe
            val player = event.pokemon.getOwnerPlayer() ?: return@subscribe
            val active = getActiveBuff(player.uuid, "ev_gain") ?: return@subscribe
            val bonus = BuffCatalog.EV_GAIN.effectValue(active.level)
            if (bonus <= 0.0) return@subscribe

            val raw = event.amount * (1.0 + bonus)
            val baseGain = raw.toInt()
            val fraction = raw - baseGain
            val extra = if (Random.nextDouble() < fraction) 1 else 0
            event.amount = baseGain + extra
        }
    }

    private fun applyHiddenAbility(pokemon: com.cobblemon.mod.common.pokemon.Pokemon, level: Int) {
        if (pokemon.ability.priority == Priority.LOW) return
        val hidden = pokemon.form.abilities.mapping[Priority.LOW]
            ?.filterIsInstance<HiddenAbility>()?.randomOrNull() ?: return
        val baseChance = NbpConfig.data.buffs.hiddenAbilityBaseChance.coerceIn(0.001, 1.0)
        val bonus = BuffCatalog.HIDDEN_ABILITY.effectValue(level)
        val totalChance = (baseChance * (1.0 + bonus)).coerceIn(0.0, 1.0)
        if (Random.nextDouble() < totalChance) {
            pokemon.updateAbility(hidden.template.create(false, Priority.LOW))
        }
    }

    private fun findNearestPlayer(entity: PokemonEntity): ServerPlayer? {
        val level = entity.level() as? net.minecraft.server.level.ServerLevel ?: return null
        return level.getNearestPlayer(entity, 32.0) as? ServerPlayer
    }
}

internal class BuffSavedData : SavedData() {
    val accounts = mutableMapOf<UUID, MutableMap<String, ActiveBuffState>>()

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        val playersTag = CompoundTag()
        accounts.forEach { (uuid, buffs) ->
            val list = ListTag()
            buffs.values.forEach { buff ->
                val buffTag = CompoundTag()
                buffTag.putString("buffId", buff.buffId)
                buffTag.putInt("level", buff.level)
                buffTag.putInt("remainingSeconds", buff.remainingSeconds)
                buffTag.putBoolean("flyGranted", buff.flyGranted)
                list.add(buffTag)
            }
            playersTag.put(uuid.toString(), list)
        }
        tag.put("players", playersTag)
        return tag
    }

    companion object {
        const val NAME = "nbp_cobble_plus_buffs"

        internal fun load(tag: CompoundTag, registries: HolderLookup.Provider): BuffSavedData {
            val data = BuffSavedData()
            val playersTag = tag.getCompound("players")
            playersTag.allKeys.forEach { uuidStr ->
                runCatching { UUID.fromString(uuidStr) }.getOrNull()?.let { uuid ->
                    val list = playersTag.getList(uuidStr, Tag.TAG_COMPOUND.toInt())
                    val buffsMap = mutableMapOf<String, ActiveBuffState>()
                    for (i in 0 until list.size) {
                        val buffTag = list.getCompound(i)
                        val id = buffTag.getString("buffId")
                        val level = buffTag.getInt("level")
                        val rem = buffTag.getInt("remainingSeconds")
                        val fly = buffTag.getBoolean("flyGranted")
                        if (id.isNotBlank() && rem > 0) {
                            buffsMap[id] = ActiveBuffState(id, level, rem, fly)
                        }
                    }
                    data.accounts[uuid] = buffsMap
                }
            }
            return data
        }

        private val FACTORY = Factory(::BuffSavedData, ::load, DataFixTypes.LEVEL)
        fun get(server: MinecraftServer): BuffSavedData =
            server.overworld().dataStorage.computeIfAbsent(FACTORY, NAME)
    }
}
