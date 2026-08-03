package com.nbp.cobbleplus.feature.impl

import com.cobblemon.mod.common.Cobblemon
import com.nbp.cobbleplus.config.NbpConfig
import com.nbp.cobbleplus.feature.FeatureModule
import net.minecraft.network.chat.Component
import com.nbp.cobbleplus.i18n.PlayerLanguage
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

object PartyHealFeature : FeatureModule {
    override val name: String = "Cura-Party Pokémon"
    override val isEnabled: Boolean
        get() = NbpConfig.data.partyHeal.enabled

    private val cooldowns = mutableMapOf<UUID, Long>()

    fun executeHeal(player: ServerPlayer): Boolean {
        if (!isEnabled) {
            player.displayClientMessage(PlayerLanguage.text(player, "party.disabled"), true)
            return false
        }

        val config = NbpConfig.data.partyHeal
        val now = System.currentTimeMillis()
        val lastUse = cooldowns[player.uuid] ?: 0L
        val cooldownMs = config.cooldownSeconds * 1000L
        val timePassed = now - lastUse

        if (timePassed < cooldownMs) {
            val secondsLeft = ((cooldownMs - timePassed) / 1000L).coerceAtLeast(1)
            val msg = PlayerLanguage.template(player, "party.cooldown", config.cooldownMessage, "seconds" to secondsLeft)
            player.displayClientMessage(Component.literal(msg), true)
            return false
        }

        try {
            val party = Cobblemon.storage.getParty(player)
            party.heal()
            cooldowns[player.uuid] = now
            player.displayClientMessage(Component.literal(PlayerLanguage.template(player, "party.healed", config.healMessage)), true)
            return true
        } catch (e: Exception) {
            player.displayClientMessage(PlayerLanguage.text(player, "party.error"), true)
            e.printStackTrace()
            return false
        }
    }

    override fun onEnable() {}
    override fun onDisable() {
        cooldowns.clear()
    }
}
