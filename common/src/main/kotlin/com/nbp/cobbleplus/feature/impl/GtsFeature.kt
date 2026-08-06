package com.nbp.cobbleplus.feature.impl

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.item.PokemonItem
import com.cobblemon.mod.common.pokemon.Pokemon
import com.nbp.cobbleplus.config.NbpConfig
import com.nbp.cobbleplus.feature.FeatureModule
import com.nbp.cobbleplus.network.GtsViewRow
import com.nbp.cobbleplus.network.GtsViewSyncPayload
import com.nbp.cobbleplus.network.GtsPartyRow
import com.nbp.cobbleplus.network.GtsPartyViewPayload
import net.minecraft.core.HolderLookup
import net.minecraft.core.RegistryAccess
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.util.datafix.DataFixTypes
import java.util.UUID

object GtsFeature : FeatureModule {
    override val name = "Global Trade Station"
    override val isEnabled get() = NbpConfig.data.gts.enabled
    private var store: GtsSavedData? = null
    var viewNetworkSender: ((ServerPlayer, GtsViewSyncPayload) -> Unit)? = null
    var partyViewNetworkSender: ((ServerPlayer, GtsPartyViewPayload) -> Unit)? = null

    override fun onEnable() = Unit
    override fun onDisable() = Unit

    fun bindServer(server: MinecraftServer) { store = GtsSavedData.get(server) }
    fun unbindServer() {
        store?.setDirty()
        store = null
    }

    fun open(player: ServerPlayer) {
        if (!isEnabled) { player.displayClientMessage(Component.literal("§cThe GTS is disabled."), true); return }
        val data = requireStore()
        val listings = data.listings.take(NbpConfig.data.gts.pageSize.coerceIn(1, 45))
        if (viewNetworkSender != null) {
            sendView(player)
            return
        }
        val items = listings.map { listing ->
            val pokemon = decode(player.server.registryAccess(), listing.pokemon) ?: return@map ItemStack.EMPTY
            PokemonItem.from(pokemon).also { stack ->
                stack.set(DataComponents.CUSTOM_NAME, Component.literal("§a#${listing.id} ${displayName(pokemon)} §7Lv.${pokemon.level} §e- ${listing.price} CobbleDollars §7(${listing.sellerName})"))
            }
        }
        player.openMenu(object : MenuProvider {
            override fun getDisplayName(): Component = Component.literal("GTS - Pokémon for sale")
            override fun createMenu(id: Int, inventory: Inventory, p: Player) =
                GtsMenu(id, inventory, items, listings.map { it.id })
        })
    }

    fun sendView(player: ServerPlayer) {
        val data = requireStore()
        val rows = data.listings.take(NbpConfig.data.gts.pageSize.coerceIn(1, 45)).mapNotNull { listing ->
            val pokemon = decode(player.server.registryAccess(), listing.pokemon) ?: return@mapNotNull null
            GtsViewRow(
                listing.id, pokemon.species.resourceIdentifier.toString(), pokemon.shiny,
                listing.sellerName, listing.price, pokemon.level,
                stripNamespace(pokemon.nature.name.toString()),
                stripNamespace(pokemon.ability.name.toString()),
                calculateIvPct(pokemon), calculateEvPct(pokemon),
                stripNamespace(pokemon.gender.name.toString()),
                stripNamespace(pokemon.form.name.toString()),
                getIv(pokemon, Stats.HP), getIv(pokemon, Stats.ATTACK), getIv(pokemon, Stats.DEFENCE),
                getIv(pokemon, Stats.SPECIAL_ATTACK), getIv(pokemon, Stats.SPECIAL_DEFENCE), getIv(pokemon, Stats.SPEED),
                getEv(pokemon, Stats.HP), getEv(pokemon, Stats.ATTACK), getEv(pokemon, Stats.DEFENCE),
                getEv(pokemon, Stats.SPECIAL_ATTACK), getEv(pokemon, Stats.SPECIAL_DEFENCE), getEv(pokemon, Stats.SPEED)
            )
        }
        viewNetworkSender?.invoke(player, GtsViewSyncPayload(rows, CobbleDollarsBridge.balance(player).toString(), data.pendingPayments[player.uuid] ?: 0L))
    }

    fun sendPartyView(player: ServerPlayer) {
        val party = Cobblemon.storage.getParty(player)
        val rows = (0 until 6).mapNotNull { slot ->
            val pokemon = party.get(slot) ?: return@mapNotNull null
            GtsPartyRow(
                slot + 1, pokemon.species.resourceIdentifier.toString(), pokemon.level,
                pokemon.shiny, stripNamespace(pokemon.nature.name.toString()),
                stripNamespace(pokemon.ability.name.toString()),
                calculateIvPct(pokemon), calculateEvPct(pokemon),
                stripNamespace(pokemon.gender.name.toString()),
                stripNamespace(pokemon.form.name.toString()),
                getIv(pokemon, Stats.HP), getIv(pokemon, Stats.ATTACK), getIv(pokemon, Stats.DEFENCE),
                getIv(pokemon, Stats.SPECIAL_ATTACK), getIv(pokemon, Stats.SPECIAL_DEFENCE), getIv(pokemon, Stats.SPEED),
                getEv(pokemon, Stats.HP), getEv(pokemon, Stats.ATTACK), getEv(pokemon, Stats.DEFENCE),
                getEv(pokemon, Stats.SPECIAL_ATTACK), getEv(pokemon, Stats.SPECIAL_DEFENCE), getEv(pokemon, Stats.SPEED)
            )
        }
        partyViewNetworkSender?.invoke(player, GtsPartyViewPayload(rows))
    }

    fun sell(player: ServerPlayer, partySlot: Int, price: Long): Boolean {
        if (!isEnabled) return fail(player, "The GTS is disabled.")
        if (price <= 0) return fail(player, "Price must be greater than zero.")
        val data = requireStore()
        val sellerListings = data.listings.count { it.seller == player.uuid }
        if (sellerListings >= NbpConfig.data.gts.maxListingsPerPlayer.coerceAtLeast(1))
            return fail(player, "You have reached the maximum number of listings.")
        val party = Cobblemon.storage.getParty(player)
        val pokemon = party.get(partySlot - 1) ?: return fail(player, "No Pokémon in that party slot.")
        if (!pokemon.tradeable) return fail(player, "This Pokémon cannot be traded.")
        val tag = pokemon.saveToNBT(player.server.registryAccess(), CompoundTag())
        party.remove(pokemon)
        val listing = GtsListing(data.nextId++, player.uuid, player.scoreboardName, price, tag)
        data.listings += listing
        data.setDirty()
        player.displayClientMessage(Component.literal("§a${displayName(pokemon)} listed for §e$price CobbleDollars§a. ID: §e#${listing.id}§a. To cancel: §e/nbp gts cancel ${listing.id}"), false)
        return true
    }

    fun purchase(player: Player, id: Long): Boolean {
        val buyer = player as? ServerPlayer ?: return false
        val data = requireStore()
        val listing = data.listings.firstOrNull { it.id == id } ?: return fail(buyer, "That listing is no longer available.")
        if (listing.seller == buyer.uuid) return fail(buyer, "You cannot buy your own listing.")
        val party = Cobblemon.storage.getParty(buyer)
        val position = (0 until 6).firstOrNull { party.get(it) == null }
            ?: return fail(buyer, "Your party is full.")
        val pokemon = decode(buyer.server.registryAccess(), listing.pokemon) ?: return fail(buyer, "Invalid Pokémon data.")
        if (!CobbleDollarsBridge.spend(buyer, listing.price)) return fail(buyer, "You don't have enough CobbleDollars.")

        party.set(position, pokemon)
        data.listings.removeIf { it.id == id }
        data.pendingPayments[listing.seller] = (data.pendingPayments[listing.seller] ?: 0L) + listing.price
        data.setDirty()
        buyer.displayClientMessage(Component.literal("§aYou bought ${displayName(pokemon)} for §e${listing.price} CobbleDollars§a."), false)
        claimPayments(buyer)
        sendView(buyer)
        return true
    }

    fun cancel(player: ServerPlayer, id: Long): Boolean {
        val data = requireStore()
        val listing = data.listings.firstOrNull { it.id == id && it.seller == player.uuid }
            ?: return fail(player, "Listing not found or doesn't belong to you.")
        val pokemon = decode(player.server.registryAccess(), listing.pokemon) ?: return fail(player, "Invalid Pokémon data.")
        val pc = Cobblemon.storage.getPC(player)
        var stored = false
        for (box in 0 until 40) {
            for (slot in 0 until 30) {
                val position = com.cobblemon.mod.common.api.storage.pc.PCPosition(box, slot)
                if (pc.get(position) == null) {
                    pc.set(position, pokemon)
                    stored = true
                    break
                }
            }
            if (stored) break
        }
        if (!stored) {
            val partyPos = (0 until 6).firstOrNull { Cobblemon.storage.getParty(player).get(it) == null }
                ?: return fail(player, "Your PC and party are both full.")
            Cobblemon.storage.getParty(player).set(partyPos, pokemon)
        }
        data.listings.remove(listing)
        data.setDirty()
        player.displayClientMessage(Component.literal("§aListing cancelled and Pokémon returned to your PC."), false)
        return true
    }

    fun claimPayments(player: ServerPlayer) {
        collect(player)
    }

    fun collect(player: ServerPlayer): Long {
        val data = requireStore()
        val amount = data.pendingPayments[player.uuid] ?: return 0L
        if (amount > 0 && CobbleDollarsBridge.earn(player, amount, false) > 0) {
            data.pendingPayments.remove(player.uuid)
            data.setDirty()
            player.displayClientMessage(Component.literal("§aYou received §e$amount CobbleDollars§a from GTS sales."), false)
            sendView(player)
            return amount
        }
        return 0L
    }

    private fun calculateIvPct(pokemon: Pokemon): Float {
        val allStats = listOf(Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED)
        val total = allStats.sumOf { pokemon.ivs.getOrDefault(it) }.toFloat()
        val max = 31f * 6
        return (total / max) * 100f
    }

    private fun calculateEvPct(pokemon: Pokemon): Float {
        val allStats = listOf(Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED)
        val total = allStats.sumOf { pokemon.evs.getOrDefault(it) }.toFloat()
        return (total / 510f) * 100f
    }

    private fun getIv(pokemon: Pokemon, stat: com.cobblemon.mod.common.api.pokemon.stats.Stat): Int =
        pokemon.ivs.getOrDefault(stat)

    private fun getEv(pokemon: Pokemon, stat: com.cobblemon.mod.common.api.pokemon.stats.Stat): Int =
        pokemon.evs.getOrDefault(stat)

    private fun stripNamespace(s: String): String = if (s.contains(':')) s.substringAfter(':') else s

    private fun decode(registries: RegistryAccess, tag: CompoundTag): Pokemon? =
        runCatching { Pokemon().loadFromNBT(registries, tag.copy()) }.getOrNull()

    private fun displayName(pokemon: Pokemon): String = pokemon.getDisplayName(false).string
    private fun requireStore() = checkNotNull(store) { "GTS store is not bound to a server" }
    private fun fail(player: ServerPlayer, message: String): Boolean { player.displayClientMessage(Component.literal("§c$message"), true); return false }
}

private data class GtsListing(val id: Long, val seller: UUID, val sellerName: String, val price: Long, val pokemon: CompoundTag)

private class GtsSavedData : SavedData() {
    var nextId = 1L
    val listings = mutableListOf<GtsListing>()
    val pendingPayments = mutableMapOf<UUID, Long>()

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        tag.putLong("nextId", nextId)
        val entries = net.minecraft.nbt.ListTag()
        listings.forEach { listing ->
            entries.add(CompoundTag().also {
                it.putLong("id", listing.id); it.putUUID("seller", listing.seller); it.putString("sellerName", listing.sellerName)
                it.putLong("price", listing.price); it.put("pokemon", listing.pokemon.copy())
            })
        }
        tag.put("listings", entries)
        val payments = CompoundTag()
        pendingPayments.forEach { (uuid, amount) -> payments.putLong(uuid.toString(), amount) }
        tag.put("pendingPayments", payments)
        return tag
    }

    companion object {
        private const val NAME = "nbp_cobble_plus_gts"
        private fun load(tag: CompoundTag, registries: HolderLookup.Provider) = GtsSavedData().also { data ->
            data.nextId = tag.getLong("nextId").coerceAtLeast(1L)
            tag.getList("listings", 10).forEach { raw ->
                val entry = raw as CompoundTag
                runCatching { entry.getUUID("seller") }.getOrNull()?.let { seller ->
                    data.listings += GtsListing(entry.getLong("id"), seller, entry.getString("sellerName"), entry.getLong("price"), entry.getCompound("pokemon"))
                }
            }
            tag.getCompound("pendingPayments").allKeys.forEach { key -> runCatching { data.pendingPayments[UUID.fromString(key)] = tag.getCompound("pendingPayments").getLong(key) } }
        }
        private val FACTORY = Factory(::GtsSavedData, ::load, DataFixTypes.LEVEL)
        fun get(server: MinecraftServer): GtsSavedData = server.overworld().dataStorage.computeIfAbsent(FACTORY, NAME)
    }
}
