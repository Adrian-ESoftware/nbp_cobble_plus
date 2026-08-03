package com.nbp.cobbleplus.feature.impl

import com.cobblemon.mod.common.api.drop.ItemDropEntry
import com.cobblemon.mod.common.api.drop.DropTable
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.reactive.ObservableSubscription
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.nbp.cobbleplus.NbpCobblePlus
import com.nbp.cobbleplus.config.PokemonLootConfig
import com.nbp.cobbleplus.config.PokemonLootRule
import com.nbp.cobbleplus.feature.FeatureModule
import net.minecraft.resources.ResourceLocation
import java.util.IdentityHashMap

object PokemonLootModifierFeature : FeatureModule {
    override val name = "Pokémon loot modifiers"
    override val isEnabled: Boolean get() = PokemonLootConfig.data.enabled
    private var subscription: ObservableSubscription<*>? = null
    private val originalTables = IdentityHashMap<DropTable, List<com.cobblemon.mod.common.api.drop.DropEntry>>()

    override fun onEnable() {
        PokemonLootConfig.load()
        if (!PokemonLootConfig.data.enabled || subscription != null) return
        subscription = CobblemonEvents.LOOT_DROPPED.subscribe { event ->
            val pokemon = event.entity as? PokemonEntity ?: return@subscribe
            val species = normalizeSpecies(pokemon.pokemon.species.resourceIdentifier.toString())
            val rule = findRule(species) ?: return@subscribe
            applyRule(event.drops, rule, pokemon.random)
        }
        refreshDisplayTables()
    }

    override fun onDisable() {
        subscription?.unsubscribe()
        subscription = null
        restoreDisplayTables()
        invalidateCobblemonIntegrationsCache()
    }

    override fun onReload() {
        PokemonLootConfig.load()
        onDisable()
        if (PokemonLootConfig.data.enabled) onEnable()
    }

    /**
     * Exposes configured changes through the native FormData drop tables read by Cobblemon Integrations.
     * Added entries are display-only, so the real drop continues to be handled once by LOOT_DROPPED.
     */
    fun refreshDisplayTables() {
        restoreDisplayTables()
        if (!PokemonLootConfig.data.enabled) return

        PokemonSpecies.species.forEach { species ->
            val rule = findRule(normalizeSpecies(species.resourceIdentifier.toString())) ?: return@forEach
            val forms = species.forms.ifEmpty { listOf(species.standardForm) }
            forms.forEach { form ->
                val table = form.drops
                originalTables.putIfAbsent(table, table.entries.toList())
                val removals = rule.remove.mapNotNull(ResourceLocation::tryParse).toSet()
                table.entries.removeIf { it is ItemDropEntry && it.item in removals }
            }

            // Cobblemon Integrations combines every form into one JEI recipe. Publishing the
            // configured entry on every form would therefore show it two, four, or more times.
            val displayTable = forms.first().drops
            originalTables.putIfAbsent(displayTable, displayTable.entries.toList())
            rule.add.forEach additions@{ addition ->
                val id = ResourceLocation.tryParse(addition.item) ?: return@additions
                displayTable.entries += DisplayOnlyItemDropEntry(
                    id,
                    addition.chance.coerceIn(0.0, 1.0).toFloat() * 100f,
                    addition.minQuantity.coerceAtLeast(1)..addition.maxQuantity.coerceAtLeast(addition.minQuantity.coerceAtLeast(1))
                )
            }
        }
        invalidateCobblemonIntegrationsCache()
        NbpCobblePlus.logger.info("Published configured Pokémon loot changes to Cobblemon Integrations")
    }

    private fun restoreDisplayTables() {
        originalTables.forEach { (table, entries) ->
            table.entries.clear()
            table.entries.addAll(entries)
        }
        originalTables.clear()
    }

    /**
     * Cobblemon Integrations caches FormData drops on the server. Keep this optional and reflection-only
     * so the mod still loads normally when that integration is not installed.
     */
    private fun invalidateCobblemonIntegrationsCache() {
        try {
            val dataSync = Class.forName("com.arcaryx.cobblemonintegrations.data.DataSync")
            dataSync.getDeclaredField("computedDrops").apply {
                isAccessible = true
                set(null, null)
            }
        } catch (_: ClassNotFoundException) {
            // Optional integration is not installed.
        } catch (error: ReflectiveOperationException) {
            NbpCobblePlus.logger.warn("Could not invalidate the Cobblemon Integrations drop cache", error)
        }
    }

    private fun findRule(species: String): PokemonLootRule? = PokemonLootConfig.data.species.entries
        .firstOrNull { normalizeSpecies(it.key) == species }
        ?.value
        ?.takeIf { it.enabled }

    internal fun applyRule(
        drops: MutableList<com.cobblemon.mod.common.api.drop.DropEntry>,
        rule: PokemonLootRule,
        random: net.minecraft.util.RandomSource
    ) {
        val removals = rule.remove.mapNotNull(ResourceLocation::tryParse).toSet()
        if (removals.isNotEmpty()) {
            drops.removeIf { drop -> drop is ItemDropEntry && drop.item in removals }
        }

        rule.add.forEach { addition ->
            if (random.nextDouble() >= addition.chance.coerceIn(0.0, 1.0)) return@forEach
            val id = ResourceLocation.tryParse(addition.item)
            if (id == null) {
                NbpCobblePlus.logger.warn("Ignoring invalid configured Pokémon drop item ID: ${addition.item}")
                return@forEach
            }
            val min = addition.minQuantity.coerceAtLeast(1)
            val max = addition.maxQuantity.coerceAtLeast(min)
            drops += ItemDropEntry().apply {
                item = id
                quantity = min + random.nextInt(max - min + 1)
                percentage = 100f
            }
        }
    }

    internal fun normalizeSpecies(value: String): String {
        val trimmed = value.trim().lowercase()
        return if (':' in trimmed) trimmed else "cobblemon:$trimmed"
    }
}

private class DisplayOnlyItemDropEntry(
    override var item: ResourceLocation,
    override var percentage: Float,
    override var quantityRange: IntRange?
) : ItemDropEntry() {
    init { quantity = quantityRange?.first ?: 1 }
    override fun canDrop(pokemon: Pokemon?): Boolean = false
}
