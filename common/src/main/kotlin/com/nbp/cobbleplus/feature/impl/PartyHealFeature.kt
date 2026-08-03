package com.nbp.cobbleplus.feature.impl

import com.cobblemon.mod.common.Cobblemon
import com.nbp.cobbleplus.config.NbpConfig
import com.nbp.cobbleplus.feature.FeatureModule
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

object PartyHealFeature : FeatureModule {
    override val name: String = "Cura-Party Pokémon"
    override val isEnabled: Boolean
        get() = NbpConfig.data.partyHeal.enabled

    private val cooldowns = mutableMapOf<UUID, Long>()

    fun executeHeal(player: ServerPlayer): Boolean {
        if (!isEnabled) {
            player.sendSystemMessage(Component.literal("§c[NBP] O comando de cura de party está desativado no servidor."))
            return false
        }

        val config = NbpConfig.data.partyHeal
        val now = System.currentTimeMillis()
        val lastUse = cooldowns[player.uuid] ?: 0L
        val cooldownMs = config.cooldownSeconds * 1000L
        val timePassed = now - lastUse

        if (timePassed < cooldownMs) {
            val secondsLeft = ((cooldownMs - timePassed) / 1000L).coerceAtLeast(1)
            val msg = config.cooldownMessage.replace("{seconds}", secondsLeft.toString())
            player.sendSystemMessage(Component.literal(msg))
            return false
        }

        try {
            val party = Cobblemon.storage.getParty(player)
            party.heal()
            cooldowns[player.uuid] = now
            player.sendSystemMessage(Component.literal(config.healMessage))
            return true
        } catch (e: Exception) {
            player.sendSystemMessage(Component.literal("§c[NBP] Ocorreu um erro ao tentar curar sua equipe Pokémon."))
            e.printStackTrace()
            return false
        }
    }

    override fun onEnable() {}
    override fun onDisable() {
        cooldowns.clear()
    }
}
