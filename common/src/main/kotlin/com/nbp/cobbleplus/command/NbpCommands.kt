package com.nbp.cobbleplus.command

import com.mojang.brigadier.CommandDispatcher
import com.nbp.cobbleplus.config.NbpConfig
import com.nbp.cobbleplus.feature.FeatureManager
import com.nbp.cobbleplus.feature.impl.AutoAnnouncerFeature
import com.nbp.cobbleplus.feature.impl.CatchComboFeature
import com.nbp.cobbleplus.feature.impl.PartyHealFeature
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

object NbpCommands {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val root = Commands.literal("nbp")
            .executes { context ->
                val source = context.source
                source.sendSuccess({
                    Component.literal("§a[NBP Cobble Plus] Suíte de Servidor v1.0.0 ativa! Digite §e/nbp help §apara ajuda.")
                }, false)
                1
            }
            .then(
                Commands.literal("help")
                    .executes { context ->
                        val source = context.source
                        source.sendSuccess({
                            Component.literal(
                                "§b--- Comandos NBP Cobble Plus ---\n" +
                                "§e/nbp §7- Status do mod\n" +
                                "§e/nbp modules §7- Lista os módulos e seus status\n" +
                                "§e/nbp heal §7- Cura sua equipe Pokémon (se ativado)\n" +
                                "§e/nbp combo §7- Mostra seu combo de capturas atual\n" +
                                "§e/nbp combo reset §7- Reseta seu combo de capturas\n" +
                                "§e/nbp reload §7- Recarrega as configurações (Admin)\n" +
                                "§e/nbp announce §7- Envia próximo anúncio automático (Admin)"
                            )
                        }, false)
                        1
                    }
            )
            .then(
                Commands.literal("modules")
                    .executes { context ->
                        val source = context.source
                        val features = FeatureManager.getFeatures()
                        val sb = StringBuilder("§b--- Módulos NBP Cobble Plus ---\n")
                        for (f in features) {
                            val status = if (f.isEnabled) "§a[ATIVO]" else "§c[DESATIVADO]"
                            sb.append("§7- ").append(f.name).append(": ").append(status).append("\n")
                        }
                        source.sendSuccess({ Component.literal(sb.toString().trim()) }, false)
                        1
                    }
            )
            .then(
                Commands.literal("heal")
                    .executes { context ->
                        val player = context.source.player
                        if (player != null) {
                            PartyHealFeature.executeHeal(player)
                        } else {
                            context.source.sendFailure(Component.literal("Apenas jogadores podem usar este comando."))
                        }
                        1
                    }
            )
            .then(
                Commands.literal("combo")
                    .executes { context ->
                        val player = context.source.player
                        if (player != null) {
                            CatchComboFeature.showStatus(player)
                        } else {
                            context.source.sendFailure(Component.literal("Apenas jogadores podem usar este comando."))
                        }
                        1
                    }
                    .then(
                        Commands.literal("reset").executes { context ->
                            val player = context.source.player
                            if (player != null) {
                                CatchComboFeature.resetCombo(player)
                            } else {
                                context.source.sendFailure(Component.literal("Apenas jogadores podem usar este comando."))
                            }
                            1
                        }
                    )
            )
            .then(
                Commands.literal("announce")
                    .requires { source -> source.hasPermission(2) }
                    .executes { context ->
                        AutoAnnouncerFeature.broadcastNextMessage()
                        context.source.sendSuccess({
                            Component.literal("§a[NBP] Anúncio enviado manualmente com sucesso.")
                        }, true)
                        1
                    }
            )
            .then(
                Commands.literal("reload")
                    .requires { source -> source.hasPermission(2) }
                    .executes { context ->
                        NbpConfig.load()
                        FeatureManager.reloadAll()
                        context.source.sendSuccess({
                            Component.literal("§a[NBP Cobble Plus] Configurações e módulos recarregados com sucesso!")
                        }, true)
                        1
                    }
            )

        dispatcher.register(root)

        // Registrar também o atalho /pokeheal se o módulo PartyHeal estiver ativado
        dispatcher.register(
            Commands.literal("pokeheal")
                .executes { context ->
                    val player = context.source.player
                    if (player != null) {
                        PartyHealFeature.executeHeal(player)
                    } else {
                        context.source.sendFailure(Component.literal("Apenas jogadores podem usar este comando."))
                    }
                    1
                }
        )
    }
}
