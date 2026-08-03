package com.nbp.cobbleplus.feature.impl

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.reactive.ObservableSubscription
import com.nbp.cobbleplus.config.NbpConfig
import com.nbp.cobbleplus.feature.FeatureModule
import com.nbp.cobbleplus.i18n.PlayerLanguage
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.saveddata.SavedData
import kotlin.math.max

object CaptureCapFeature : FeatureModule {
    override val name = "Capture level cap"
    override val isEnabled: Boolean get() = NbpConfig.data.captureCap.enabled
    private var subscription: ObservableSubscription<*>? = null
    private var store: CaptureCapSavedData? = null

    override fun onEnable() {
        if (subscription != null) return
        subscription = CobblemonEvents.THROWN_POKEBALL_HIT.subscribe { event ->
            val player = event.pokeBall.owner as? ServerPlayer ?: return@subscribe
            val level = event.pokemon.pokemon.level
            val cap = getCap(player)
            if (level > cap) {
                // In battle Cobblemon creates BattleCaptureAction before posting this event.
                // Completing the future releases the forced pass/turn when the hit is rejected.
                event.pokeBall.captureFuture.complete(false)
                event.cancel()
                player.displayClientMessage(PlayerLanguage.text(player, "capture_cap.too_high", "level" to level, "cap" to cap), true)
            }
        }
    }

    override fun onDisable() {
        subscription?.unsubscribe()
        subscription = null
    }

    fun bindServer(server: MinecraftServer) { store = CaptureCapSavedData.get(server) }
    fun unbindServer() { store = null }

    fun getCap(player: ServerPlayer): Int {
        val config = NbpConfig.data.captureCap
        val personal = store?.caps?.get(player.uuid.toString()) ?: config.defaultCap
        val advancement = config.advancements.maxOfOrNull { rule ->
            val id = ResourceLocation.tryParse(rule.advancement) ?: return@maxOfOrNull 0
            val holder = player.server.advancements.get(id) ?: return@maxOfOrNull 0
            if (player.advancements.getOrStartProgress(holder).isDone) rule.cap else 0
        } ?: 0
        return max(config.defaultCap, max(personal, advancement)).coerceAtMost(config.maximumCap.coerceAtLeast(1))
    }

    fun setCap(player: ServerPlayer, cap: Int): Int {
        val value = cap.coerceIn(1, NbpConfig.data.captureCap.maximumCap.coerceAtLeast(1))
        requireStore().caps[player.uuid.toString()] = value
        requireStore().setDirty()
        return getCap(player)
    }

    fun addCap(player: ServerPlayer, amount: Int): Int = setCap(player, getCap(player) + amount)

    fun resetCap(player: ServerPlayer): Int {
        requireStore().caps.remove(player.uuid.toString())
        requireStore().setDirty()
        return getCap(player)
    }

    fun useUpgradeItem(player: ServerPlayer, stack: ItemStack): Boolean {
        if (!isEnabled) return false
        val itemId = BuiltInRegistries.ITEM.getKey(stack.item).toString()
        val upgrade = NbpConfig.data.captureCap.itemUpgrades.firstOrNull { it.item.equals(itemId, ignoreCase = true) }
            ?: return false
        val before = getCap(player)
        val maximum = NbpConfig.data.captureCap.maximumCap
        if (before >= maximum) {
            player.displayClientMessage(PlayerLanguage.text(player, "capture_cap.maximum", "cap" to before), true)
            return false
        }
        val target = if (upgrade.cap > 0) max(before, upgrade.cap) else before + upgrade.increase.coerceAtLeast(1)
        val after = setCap(player, target)
        if (upgrade.consume && !player.isCreative) stack.shrink(1)
        player.displayClientMessage(PlayerLanguage.text(player, "capture_cap.increased", "old" to before, "cap" to after), true)
        return true
    }

    fun show(player: ServerPlayer) {
        player.displayClientMessage(PlayerLanguage.text(player, "capture_cap.current", "cap" to getCap(player)), true)
    }

    private fun requireStore(): CaptureCapSavedData = checkNotNull(store) { "Capture cap store is not bound to a server" }
}

private class CaptureCapSavedData : SavedData() {
    val caps = mutableMapOf<String, Int>()

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        val players = CompoundTag()
        caps.forEach { (uuid, cap) -> players.putInt(uuid, cap) }
        tag.put("players", players)
        return tag
    }

    companion object {
        private const val NAME = "nbp_cobble_plus_capture_caps"
        private fun load(tag: CompoundTag, registries: HolderLookup.Provider) = CaptureCapSavedData().also { data ->
            val players = tag.getCompound("players")
            players.allKeys.forEach { uuid -> data.caps[uuid] = players.getInt(uuid) }
        }
        private val FACTORY = Factory(::CaptureCapSavedData, ::load, DataFixTypes.LEVEL)
        fun get(server: MinecraftServer): CaptureCapSavedData =
            server.overworld().dataStorage.computeIfAbsent(FACTORY, NAME)
    }
}
