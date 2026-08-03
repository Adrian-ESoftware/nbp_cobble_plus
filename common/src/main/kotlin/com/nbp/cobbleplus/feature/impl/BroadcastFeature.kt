package com.nbp.cobbleplus.feature.impl

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.reactive.ObservableSubscription
import com.nbp.cobbleplus.config.NbpConfig
import com.nbp.cobbleplus.feature.FeatureModule
import net.minecraft.network.chat.Component
import com.nbp.cobbleplus.i18n.PlayerLanguage

object BroadcastFeature : FeatureModule {
    override val name: String = "Broadcasts Cobblemon"
    override val isEnabled: Boolean
        get() = true // Módulo ativado; controlado pelas opções na config.json

    private var captureSub: ObservableSubscription<*>? = null
    private var starterSub: ObservableSubscription<*>? = null

    override fun onEnable() {
        // Broadcast de Captura de Pokémon (Normal e Shiny)
        captureSub = CobblemonEvents.POKEMON_CAPTURED.subscribe { event ->
            val player = event.player
            val pokemon = event.pokemon
            val config = NbpConfig.data.broadcast

            val speciesName = pokemon.species.name
            val playerName = player.name.string
            val level = pokemon.level

            if (pokemon.shiny && config.enableShinyCaptureBroadcast) {
                player.server.playerList.players.forEach { recipient ->
                    val message = PlayerLanguage.template(recipient, "broadcast.shiny", config.shinyCaptureMessage,
                        "player" to playerName, "pokemon" to speciesName, "level" to level)
                    recipient.sendSystemMessage(Component.literal(message))
                }
            } else if (config.enableCaptureBroadcast) {
                val formattedMsg = PlayerLanguage.template(player, "broadcast.capture", config.captureMessage,
                    "player" to playerName, "pokemon" to speciesName, "level" to level)

                player.sendSystemMessage(Component.literal(formattedMsg))
            }
        }

        // Broadcast de Escolha de Pokémon Inicial (Starter)
        starterSub = CobblemonEvents.STARTER_CHOSEN.subscribe { event ->
            val config = NbpConfig.data.broadcast
            if (config.enableStarterBroadcast) {
                val player = event.player
                val pokemon = event.pokemon
                player.server.playerList.players.forEach { recipient ->
                    val message = PlayerLanguage.template(recipient, "broadcast.starter", config.starterMessage,
                        "player" to player.name.string, "pokemon" to pokemon.species.name)
                    recipient.sendSystemMessage(Component.literal(message))
                }
            }
        }
    }

    override fun onDisable() {
        captureSub?.unsubscribe()
        starterSub?.unsubscribe()
        captureSub = null
        starterSub = null
    }
}
