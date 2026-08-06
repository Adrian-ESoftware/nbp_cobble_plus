package com.nbp.cobbleplus.feature.impl

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.item.PokemonItem
import com.cobblemon.mod.common.pokemon.Pokemon
import com.nbp.cobbleplus.config.NbpConfig
import com.nbp.cobbleplus.feature.FeatureModule
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

    override fun onEnable() = Unit
    override fun onDisable() = Unit

    fun bindServer(server: MinecraftServer) { store = GtsSavedData.get(server) }
    fun unbindServer() {
        store?.setDirty()
        store = null
    }

    fun open(player: ServerPlayer) {
        if (!isEnabled) { player.displayClientMessage(Component.literal("§cO GTS está desativado."), true); return }
        val data = requireStore()
        val listings = data.listings.take(NbpConfig.data.gts.pageSize.coerceIn(1, 45))
        val items = listings.map { listing ->
            val pokemon = decode(player.server.registryAccess(), listing.pokemon) ?: return@map ItemStack.EMPTY
            PokemonItem.from(pokemon).also { stack ->
                stack.set(DataComponents.CUSTOM_NAME, Component.literal("§a#${listing.id} ${displayName(pokemon)} §7Lv.${pokemon.level} §e- ${listing.price} CobbleDollars §7(${listing.sellerName})"))
            }
        }
        player.openMenu(object : MenuProvider {
            override fun getDisplayName(): Component = Component.literal("GTS - Pokémon à venda")
            override fun createMenu(id: Int, inventory: Inventory, p: Player) =
                GtsMenu(id, inventory, items, listings.map { it.id })
        })
    }

    fun sell(player: ServerPlayer, partySlot: Int, price: Long): Boolean {
        if (!isEnabled) return fail(player, "O GTS está desativado.")
        if (price <= 0) return fail(player, "O preço deve ser maior que zero.")
        val data = requireStore()
        val sellerListings = data.listings.count { it.seller == player.uuid }
        if (sellerListings >= NbpConfig.data.gts.maxListingsPerPlayer.coerceAtLeast(1))
            return fail(player, "Você atingiu o limite de anúncios.")
        val party = Cobblemon.storage.getParty(player)
        val pokemon = party.get(partySlot - 1) ?: return fail(player, "Não há Pokémon nesse slot da party.")
        if (!pokemon.tradeable) return fail(player, "Esse Pokémon não pode ser negociado.")
        val tag = pokemon.saveToNBT(player.server.registryAccess(), CompoundTag())
        party.remove(pokemon)
        val listing = GtsListing(data.nextId++, player.uuid, player.scoreboardName, price, tag)
        data.listings += listing
        data.setDirty()
        player.displayClientMessage(Component.literal("§a${displayName(pokemon)} anunciado por §e$price CobbleDollars§a. ID: §e#${listing.id}§a. Para cancelar: §e/nbp gts cancel ${listing.id}"), false)
        return true
    }

    fun purchase(player: Player, id: Long): Boolean {
        val buyer = player as? ServerPlayer ?: return false
        val data = requireStore()
        val listing = data.listings.firstOrNull { it.id == id } ?: return fail(buyer, "Esse anúncio não está mais disponível.")
        if (listing.seller == buyer.uuid) return fail(buyer, "Você não pode comprar seu próprio anúncio.")
        val party = Cobblemon.storage.getParty(buyer)
        val position = (0 until 6).firstOrNull { party.get(it) == null }
            ?: return fail(buyer, "Sua party está cheia.")
        val pokemon = decode(buyer.server.registryAccess(), listing.pokemon) ?: return fail(buyer, "Dados do Pokémon inválidos.")
        if (!CobbleDollarsBridge.spend(buyer, listing.price)) return fail(buyer, "Você não possui CobbleDollars suficientes.")

        party.set(position, pokemon)
        data.listings.removeIf { it.id == id }
        data.pendingPayments[listing.seller] = (data.pendingPayments[listing.seller] ?: 0L) + listing.price
        data.setDirty()
        buyer.displayClientMessage(Component.literal("§aVocê comprou ${displayName(pokemon)} por §e${listing.price} CobbleDollars§a."), false)
        claimPayments(buyer)
        return true
    }

    fun cancel(player: ServerPlayer, id: Long): Boolean {
        val data = requireStore()
        val listing = data.listings.firstOrNull { it.id == id && it.seller == player.uuid }
            ?: return fail(player, "Anúncio não encontrado ou não pertence a você.")
        val pokemon = decode(player.server.registryAccess(), listing.pokemon) ?: return fail(player, "Dados do Pokémon inválidos.")
        val position = (0 until 6).firstOrNull { Cobblemon.storage.getParty(player).get(it) == null }
            ?: return fail(player, "Sua party está cheia; libere um espaço antes de cancelar.")
        Cobblemon.storage.getParty(player).set(position, pokemon)
        data.listings.remove(listing)
        data.setDirty()
        player.displayClientMessage(Component.literal("§aAnúncio cancelado e Pokémon devolvido à party."), false)
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
            player.displayClientMessage(Component.literal("§aVocê recebeu §e$amount CobbleDollars§a pelas vendas do GTS."), false)
            return amount
        }
        return 0L
    }

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
