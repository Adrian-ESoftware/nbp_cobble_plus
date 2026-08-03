package com.nbp.cobbleplus.feature.impl

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.battles.model.actor.ActorType
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.nbp.cobbleplus.NbpCobblePlus
import com.nbp.cobbleplus.feature.FeatureModule
import com.nbp.cobbleplus.item.ModItems
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import java.util.UUID
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.level.saveddata.SavedData

object ItemMechanicsFeature : FeatureModule {
    override val name = "Ritual items"
    override val isEnabled = true
    private var subscribed = false
    private data class Pending(val level: ServerLevel, val pos: BlockPos, val species: String, val perfectIvs: Int, val due: Long, val player: UUID)
    private val pending = mutableListOf<Pending>()

    override fun onEnable() {
        if (subscribed) return
        subscribed = true
        CobblemonEvents.BATTLE_VICTORY.subscribe { event ->
            if (event.wasWildCapture || event.losers.none { it.type == ActorType.WILD }) return@subscribe
            val winners = event.winners.mapNotNull { (it as? com.cobblemon.mod.common.battles.actor.PlayerBattleActor)?.entity }.distinctBy { it.uuid }
            val defeated = event.losers.flatMap { it.pokemonList }.map { it.originalPokemon }
            winners.forEach { player ->
                defeated.forEach { pokemon ->
                    val types = pokemon.types.map { it.name.lowercase() }.toSet()
                    if ("electric" in types) chargeUrn(player, ModItems.URN_OF_THUNDER)
                    if ("ice" in types) chargeUrn(player, ModItems.URN_OF_FREEZING)
                    if ("fire" in types) chargeUrn(player, ModItems.URN_OF_BURNING)
                }
            }
        }
    }
    override fun onDisable() = Unit

    fun startSummon(player: Player, stack: ItemStack, species: String, perfectIvs: Int) {
        val serverPlayer = player as? ServerPlayer ?: return
        consume(serverPlayer, stack)
        val look = serverPlayer.lookAngle
        val pos = BlockPos.containing(serverPlayer.x + look.x * 5, serverPlayer.y + 1, serverPlayer.z + look.z * 5)
        pending += Pending(serverPlayer.serverLevel(), pos, species, perfectIvs, serverPlayer.serverLevel().gameTime + 60, serverPlayer.uuid)
        serverPlayer.serverLevel().playSound(null, serverPlayer.blockPosition(), SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(0).value(), SoundSource.MASTER, 2f, .8f)
        serverPlayer.displayClientMessage(Component.translatable("message.nbp_cobble_plus.item.summon_start", species), true)
    }

    fun useUrn(player: Player, stack: ItemStack, species: String) {
        val serverPlayer = player as? ServerPlayer ?: return
        val charge = charge(stack)
        if (charge < 100) {
            serverPlayer.displayClientMessage(Component.translatable("message.nbp_cobble_plus.urn.charge", charge, 100), true)
            return
        }
        val failure = urnFailure(serverPlayer, species)
        if (failure != null) {
            serverPlayer.displayClientMessage(Component.translatable(failure), true)
            return
        }
        startSummon(serverPlayer, stack, species, 3)
    }

    fun interact(player: ServerPlayer, hand: InteractionHand, target: Entity): InteractionResult {
        val pokemonEntity = target as? PokemonEntity
        val stack = player.getItemInHand(hand)
        if (pokemonEntity != null) {
            val species = pokemonEntity.pokemon.species.resourceIdentifier.path.lowercase()
            if (stack.`is`(ModItems.DNA_SYRINGE_EMPTY) && species == "mew") return extractMewDna(player, stack, pokemonEntity)
            if (stack.`is`(ModItems.RUBY) && species in setOf("uxie", "mesprit", "azelf")) return chargeRuby(player, stack, pokemonEntity, species)
            if (BuiltInRegistries.ITEM.getKey(stack.item).toString() == "create:empty_blaze_burner" && species == "slugma") {
                val filled = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse("create:blaze_burner")).orElse(null) ?: return InteractionResult.PASS
                consume(player, stack); give(player, ItemStack(filled))
                player.displayClientMessage(Component.translatable("message.nbp_cobble_plus.burner.lit"), true)
                return InteractionResult.SUCCESS
            }
        }
        return InteractionResult.PASS
    }

    fun tryAssembleRedChain(player: Player, held: ItemStack) {
        val serverPlayer = player as? ServerPlayer ?: return
        val required = setOf("uxie", "mesprit", "azelf")
        val slots = mutableMapOf<String, Int>()
        serverPlayer.inventory.items.forEachIndexed { index, stack ->
            if (stack.`is`(ModItems.RUBY)) {
                val guardian = custom(stack).getString("lake_guardian")
                if (guardian in required && guardian !in slots) slots[guardian] = index
            }
        }
        if (!required.all(slots::containsKey)) {
            if (custom(held).contains("lake_guardian")) serverPlayer.displayClientMessage(Component.translatable("message.nbp_cobble_plus.ruby.chain_missing"), true)
            return
        }
        if (!serverPlayer.abilities.instabuild) slots.values.forEach { serverPlayer.inventory.items[it].shrink(1) }
        give(serverPlayer, ItemStack(ModItems.RED_CHAIN))
        serverPlayer.displayClientMessage(Component.translatable("message.nbp_cobble_plus.ruby.chain_created"), true)
        serverPlayer.serverLevel().playSound(null, serverPlayer.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1f, .8f)
    }

    fun tick(server: MinecraftServer) {
        val ready = pending.filter { it.level.gameTime >= it.due }
        pending.removeAll(ready.toSet())
        ready.forEach { task ->
            try {
                val entity = PokemonProperties.parse("species=${task.species} level=60 min_perfect_ivs=${task.perfectIvs}").createEntity(task.level)
                val spawnPos = LegendarySpawnSafety.find(task.level, task.pos, 10) ?: task.pos.above(2)
                entity.moveTo(spawnPos.x + .5, spawnPos.y.toDouble(), spawnPos.z + .5, task.level.random.nextFloat() * 360f, 0f)
                entity.setPersistenceRequired()
                if (task.level.addFreshEntity(entity)) LegendaryVisuals.apply(entity, task.species)
                task.level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, spawnPos.x + .5, spawnPos.y + 1.0, spawnPos.z + .5, 80, .7, 1.0, .7, .1)
                server.playerList.getPlayer(task.player)?.displayClientMessage(Component.translatable("message.nbp_cobble_plus.item.summoned", task.species), true)
            } catch (e: Exception) { NbpCobblePlus.logger.error("Failed ritual item summon for ${task.species}", e) }
        }
    }

    private fun extractMewDna(player: ServerPlayer, stack: ItemStack, entity: PokemonEntity): InteractionResult {
        if (entity.ownerUUID != player.uuid) return message(player, "message.nbp_cobble_plus.dna.not_owned")
        if (entity.pokemon.level < 80) return message(player, "message.nbp_cobble_plus.item.level_required", 80, entity.pokemon.level)
        val worldData = player.server.overworld().dataStorage.computeIfAbsent(ItemWorldData.FACTORY, "nbp_item_mechanics")
        val rare = !worldData.mewtwoCreated && player.random.nextDouble() < .33
        if (rare) { worldData.mewtwoCreated = true; worldData.setDirty() }
        consume(player, stack); give(player, ItemStack(if (rare) ModItems.DNA_SYRINGE_MEWTWO else ModItems.DNA_SYRINGE_MEW))
        entity.pokemon.level = entity.pokemon.level - 40
        return message(player, "message.nbp_cobble_plus.dna.filled")
    }

    private fun chargeRuby(player: ServerPlayer, stack: ItemStack, entity: PokemonEntity, species: String): InteractionResult {
        if (custom(stack).contains("lake_guardian")) return message(player, "message.nbp_cobble_plus.ruby.already_charged")
        if (entity.ownerUUID != player.uuid) return message(player, "message.nbp_cobble_plus.ruby.not_owned")
        if (entity.pokemon.level < 80) return message(player, "message.nbp_cobble_plus.item.level_required", 80, entity.pokemon.level)
        val charged = ItemStack(ModItems.RUBY)
        CustomData.update(DataComponents.CUSTOM_DATA, charged) { it.putString("lake_guardian", species) }
        charged.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
        consume(player, stack); give(player, charged); entity.pokemon.level = entity.pokemon.level - 40
        return message(player, "message.nbp_cobble_plus.ruby.charged", species)
    }

    private fun chargeUrn(player: ServerPlayer, item: net.minecraft.world.item.Item) {
        val stack = player.inventory.items.firstOrNull { it.`is`(item) && charge(it) < 100 } ?: return
        val next = (charge(stack) + 1).coerceAtMost(100)
        CustomData.update(DataComponents.CUSTOM_DATA, stack) { it.putInt("legendary_birds_urn_charge", next) }
        if (next == 100 || next % 10 == 0) player.displayClientMessage(Component.translatable("message.nbp_cobble_plus.urn.charge", next, 100), true)
    }

    private fun urnFailure(player: ServerPlayer, species: String): String? {
        val biome = player.serverLevel().getBiome(player.blockPosition()).unwrapKey().map { it.location().toString().lowercase() }.orElse("")
        val time = player.serverLevel().dayTime % 24000
        return when (species) {
            "articuno" -> if (listOf("snow", "frozen", "ice", "frost", "tundra", "cold", "grove", "peak").none(biome::contains)) "message.nbp_cobble_plus.urn.articuno_biome" else if (time !in 13000..23000) "message.nbp_cobble_plus.urn.articuno_night" else if (!player.serverLevel().isRaining) "message.nbp_cobble_plus.urn.articuno_weather" else null
            "zapdos" -> if (listOf("mountain", "peak", "stony", "windswept", "highland", "cliff").none(biome::contains) && !(player.y >= 90 && listOf("plateau", "meadow", "plains").any(biome::contains))) "message.nbp_cobble_plus.urn.zapdos_biome" else if (!player.serverLevel().isThundering) "message.nbp_cobble_plus.urn.zapdos_weather" else null
            else -> if (listOf("badlands", "desert", "savanna", "hot", "arid", "volcan", "mesa", "stony", "rocky").none(biome::contains)) "message.nbp_cobble_plus.urn.moltres_biome" else if (time !in 5000..7000) "message.nbp_cobble_plus.urn.moltres_time" else null
        }
    }

    private fun custom(stack: ItemStack) = stack.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: net.minecraft.nbt.CompoundTag()
    private fun charge(stack: ItemStack) = custom(stack).getInt("legendary_birds_urn_charge").coerceIn(0, 100)
    private fun consume(player: ServerPlayer, stack: ItemStack) { if (!player.abilities.instabuild) stack.shrink(1) }
    private fun give(player: ServerPlayer, stack: ItemStack) { if (!player.inventory.add(stack)) player.drop(stack, false) }
    private fun message(player: ServerPlayer, key: String, vararg args: Any): InteractionResult { player.displayClientMessage(Component.translatable(key, *args), true); return InteractionResult.SUCCESS }
}

class ItemWorldData(var mewtwoCreated: Boolean = false) : SavedData() {
    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag = tag.apply { putBoolean("mewtwoCreated", mewtwoCreated) }
    companion object {
        val FACTORY = Factory(::ItemWorldData, { tag, _ -> ItemWorldData(tag.getBoolean("mewtwoCreated")) }, DataFixTypes.LEVEL)
    }
}
