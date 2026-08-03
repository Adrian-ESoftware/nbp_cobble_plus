package com.nbp.cobbleplus.feature.impl

import com.nbp.cobbleplus.config.NbpConfig
import com.nbp.cobbleplus.feature.FeatureModule
import net.minecraft.network.chat.Component
import com.nbp.cobbleplus.i18n.PlayerLanguage
import net.minecraft.server.MinecraftServer
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object AutoAnnouncerFeature : FeatureModule {
    override val name: String = "Anunciador Automático"
    override val isEnabled: Boolean
        get() = NbpConfig.data.announcer.enabled

    private var scheduler: ScheduledExecutorService? = null
    private var currentServer: MinecraftServer? = null
    private var messageIndex = 0

    fun setServer(server: MinecraftServer?) {
        this.currentServer = server
    }

    override fun onEnable() {
        val config = NbpConfig.data.announcer
        if (!config.enabled || config.messages.isEmpty()) return

        stopScheduler()

        val interval = config.intervalSeconds.coerceAtLeast(10)
        scheduler = Executors.newSingleThreadScheduledExecutor()
        scheduler?.scheduleAtFixedRate({
            try {
                broadcastNextMessage()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, interval.toLong(), interval.toLong(), TimeUnit.SECONDS)
    }

    fun broadcastNextMessage() {
        val server = currentServer ?: return
        val config = NbpConfig.data.announcer
        if (!config.enabled || config.messages.isEmpty()) return

        val selectedIndex: Int
        val message = if (config.announceInRandomOrder) {
            selectedIndex = Random.nextInt(config.messages.size)
            config.messages[selectedIndex]
        } else {
            selectedIndex = messageIndex % config.messages.size
            val msg = config.messages[messageIndex % config.messages.size]
            messageIndex = (messageIndex + 1) % config.messages.size
            msg
        }

        server.execute {
            server.playerList.players.forEach { player ->
                player.sendSystemMessage(Component.literal(PlayerLanguage.template(player, "announcer.$selectedIndex", message)))
            }
        }
    }

    private fun stopScheduler() {
        scheduler?.shutdownNow()
        scheduler = null
    }

    override fun onDisable() {
        stopScheduler()
    }
}
