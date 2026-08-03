package com.nbp.cobbleplus.feature.impl

import com.nbp.cobbleplus.config.NbpConfig
import com.nbp.cobbleplus.feature.FeatureModule
import net.minecraft.network.chat.Component
import com.nbp.cobbleplus.i18n.PlayerLanguage
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

object WelcomeFeature : FeatureModule {
    override val name: String = "Boas-Vindas ao Servidor"
    override val isEnabled: Boolean
        get() = NbpConfig.data.welcome.enabled

    // Guarda UUIDs dos jogadores que entraram durante a sessão do servidor
    private val joinedPlayers = mutableSetOf<UUID>()

    fun handlePlayerJoin(player: ServerPlayer) {
        if (!isEnabled) return

        val config = NbpConfig.data.welcome
        val isFirstJoin = !joinedPlayers.contains(player.uuid)
        joinedPlayers.add(player.uuid)

        if (isFirstJoin && config.enableFirstJoinMessage) {
            player.server.playerList.players.forEach { recipient ->
                val msg = PlayerLanguage.template(recipient, "welcome.first", config.firstJoinChatMessage, "player" to player.name.string)
                recipient.sendSystemMessage(Component.literal(msg))
            }
        } else if (config.chatMessage.isNotBlank()) {
            val msg = PlayerLanguage.template(player, "welcome.normal", config.chatMessage, "player" to player.name.string)
            player.sendSystemMessage(Component.literal(msg))
        }
    }

    override fun onEnable() {}
    override fun onDisable() {}
}
