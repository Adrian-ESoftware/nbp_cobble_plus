package com.nbp.cobbleplus.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.nbp.cobbleplus.config.NbpConfig
import com.nbp.cobbleplus.feature.FeatureManager
import com.nbp.cobbleplus.feature.impl.AutoAnnouncerFeature
import com.nbp.cobbleplus.feature.impl.CatchComboFeature
import com.nbp.cobbleplus.feature.impl.PartyHealFeature
import com.nbp.cobbleplus.feature.impl.LegendarySpawnerFeature
import com.nbp.cobbleplus.feature.impl.CaptureCapFeature
import com.nbp.cobbleplus.feature.impl.EconomyFeature
import com.nbp.cobbleplus.feature.impl.SafariZoneFeature
import com.nbp.cobbleplus.i18n.PlayerLanguage
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

object NbpCommands {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val root = Commands.literal("nbp")
            .executes { context ->
                val source = context.source
                source.sendSuccess({
                    PlayerLanguage.text(source.player, "command.status")
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
                                "§e/nbp combo hud §7- Mostra/esconde o HUD do combo na tela\n" +
                                "§e/nbp capturecap §7- Mostra seu limite de captura\n" +
                                "§e/nbp economy §7- Mostra seus ganhos e limite diário\n" +
                                "§e/nbp legendary test [espécie] §7- Testa um spawn lendário (Admin)\n" +
                                "§e/nbp legendary test-natural §7- Executa o sorteio natural completo (Admin)\n" +
                                "§e/nbp legendary chance §7- Mostra sua chance atual de anfitrião\n" +
                                "§e/nbp legendary available §7- Lista lendários disponíveis na área\n" +
                                "§e/nbp legendary history §7- Mostra o balanceamento (Admin)\n" +
                                "§e/nbp reload §7- Recarrega as configurações (Admin)\n" +
                                "§e/nbp announce §7- Envia próximo anúncio automático (Admin)"
                            )
                        }, false)
                        1
                    }
            )
            .then(
                Commands.literal("lang")
                    .executes { context ->
                        val player = context.source.player
                        if (player == null) {
                            context.source.sendFailure(PlayerLanguage.text(null, "player.only")); 0
                        } else {
                            context.source.sendSuccess({ PlayerLanguage.text(player, "lang.current", "lang" to PlayerLanguage.get(player)) }, false); 1
                        }
                    }
                    .then(
                        Commands.argument("language", StringArgumentType.word()).executes { context ->
                            val player = context.source.player
                            if (player == null) {
                                context.source.sendFailure(PlayerLanguage.text(null, "player.only")); 0
                            } else {
                                val language = StringArgumentType.getString(context, "language")
                                if (PlayerLanguage.set(player, language)) {
                                    context.source.sendSuccess({ PlayerLanguage.text(player, "lang.changed", "lang" to PlayerLanguage.get(player)) }, false); 1
                                } else {
                                    context.source.sendFailure(PlayerLanguage.text(player, "lang.invalid")); 0
                                }
                            }
                        }
                    )
            )
            .then(
                Commands.literal("legendary")
                    .requires { source -> source.hasPermission(2) }
                    .then(
                        Commands.literal("test")
                            .executes { context -> testLegendary(context.source, null) }
                            .then(
                                Commands.argument("species", StringArgumentType.word())
                                    .executes { context ->
                                        testLegendary(context.source, StringArgumentType.getString(context, "species"))
                                    }
                            )
                    )
                    .then(
                        Commands.literal("test-natural").executes { context ->
                            val spawned = LegendarySpawnerFeature.forceNaturalSpawn(context.source.server)
                            if (spawned) {
                                context.source.sendSuccess({ PlayerLanguage.text(context.source.player, "legend.natural.success") }, true)
                                1
                            } else {
                                context.source.sendFailure(PlayerLanguage.text(context.source.player, "legend.natural.failed"))
                                0
                            }
                        }
                    )
                    .then(
                        Commands.literal("chance").executes { context ->
                            val player = context.source.player
                            if (player == null) {
                                context.source.sendFailure(PlayerLanguage.text(null, "player.only"))
                                0
                            } else {
                                val chance = LegendarySpawnerFeature.playerChance(context.source.server, player)
                                context.source.sendSuccess({ PlayerLanguage.text(player, "legend.chance", "chance" to "%.2f".format(java.util.Locale.US, chance)) }, false)
                                1
                            }
                        }
                    )
                    .then(
                        Commands.literal("available").executes { context ->
                            val player = context.source.player
                            if (player == null) {
                                context.source.sendFailure(PlayerLanguage.text(null, "player.only"))
                                0
                            } else {
                                val available = LegendarySpawnerFeature.availableSpecies(player)
                                val key = if (available.isEmpty()) "legend.available.none" else "legend.available"
                                context.source.sendSuccess({ PlayerLanguage.text(player, key, "species" to available.joinToString(", ")) }, false)
                                1
                            }
                        }
                    )
                    .then(
                        Commands.literal("history").executes { context ->
                            context.source.sendSuccess({
                                Component.literal(LegendarySpawnerFeature.historySummary(context.source.server))
                            }, false)
                            1
                        }
                    )
                    .then(
                        Commands.literal("reset-history").executes { context ->
                            LegendarySpawnerFeature.resetHistory()
                            context.source.sendSuccess({ PlayerLanguage.text(context.source.player, "legend.history.reset") }, true)
                            1
                        }
                    )
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
                    .then(
                        Commands.literal("hud").executes { context ->
                            val player = context.source.player
                            if (player != null) {
                                CatchComboFeature.toggleHud(player)
                            } else {
                                context.source.sendFailure(Component.literal("Apenas jogadores podem usar este comando."))
                            }
                            1
                        }
                    )
            )
            .then(
                Commands.literal("capturecap")
                    .executes { context ->
                        val player = context.source.player
                        if (player == null) {
                            context.source.sendFailure(PlayerLanguage.text(null, "player.only")); 0
                        } else {
                            CaptureCapFeature.show(player); 1
                        }
                    }
                    .then(
                        Commands.literal("add")
                            .requires { source -> source.hasPermission(2) }
                            .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("levels", IntegerArgumentType.integer(1))
                                    .executes { context ->
                                        val target = EntityArgument.getPlayer(context, "player")
                                        val cap = CaptureCapFeature.addCap(target, IntegerArgumentType.getInteger(context, "levels"))
                                        captureCapAdminFeedback(context.source, target, cap)
                                    }))
                    )
                    .then(
                        Commands.literal("set")
                            .requires { source -> source.hasPermission(2) }
                            .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                    .executes { context ->
                                        val target = EntityArgument.getPlayer(context, "player")
                                        val cap = CaptureCapFeature.setCap(target, IntegerArgumentType.getInteger(context, "level"))
                                        captureCapAdminFeedback(context.source, target, cap)
                                    }))
                    )
                    .then(
                        Commands.literal("reset")
                            .requires { source -> source.hasPermission(2) }
                            .then(Commands.argument("player", EntityArgument.player())
                                .executes { context ->
                                    val target = EntityArgument.getPlayer(context, "player")
                                    val cap = CaptureCapFeature.resetCap(target)
                                    captureCapAdminFeedback(context.source, target, cap)
                                })
                    )
            )
            .then(
                Commands.literal("economy")
                    .requires { it.hasPermission(2) }
                    .executes { context ->
                        val player = context.source.player
                        if (player == null) {
                            context.source.sendFailure(PlayerLanguage.text(null, "player.only")); 0
                        } else {
                            val status = EconomyFeature.status(player)
                            context.source.sendSuccess({ PlayerLanguage.text(player, "economy.status",
                                "balance" to status.balance, "earned" to status.earnedToday, "cap" to status.dailyCap) }, false)
                            1
                        }
                    }
                    .then(
                        Commands.literal("reset")
                            .requires { it.hasPermission(2) }
                            .then(Commands.argument("player", EntityArgument.player()).executes { context ->
                                val target = EntityArgument.getPlayer(context, "player")
                                EconomyFeature.reset(target)
                                context.source.sendSuccess({ PlayerLanguage.text(context.source.player, "economy.reset", "player" to target.scoreboardName) }, true)
                                1
                            })
                    )
            )
            .then(
                Commands.literal("safari")
                    .executes { context ->
                        val player = context.source.player
                        if (player != null) SafariZoneFeature.openSafariGui(player) else context.source.sendFailure(Component.literal("Apenas jogadores."))
                        1
                    }
                    .then(
                        Commands.literal("enter").executes { context ->
                            val player = context.source.player
                            if (player != null) SafariZoneFeature.openSafariGui(player) else context.source.sendFailure(Component.literal("Apenas jogadores."))
                            1
                        }
                    )
                    .then(
                        Commands.literal("exit").executes { context ->
                            val player = context.source.player
                            if (player != null) SafariZoneFeature.exitSafari(player) else context.source.sendFailure(Component.literal("Apenas jogadores."))
                            1
                        }
                    )
                    .then(
                        Commands.literal("setspawn")
                            .requires { source -> source.hasPermission(2) }
                            .executes { context ->
                                val player = context.source.player
                                if (player != null) {
                                    player.sendSystemMessage(PlayerLanguage.text(player, "safari.setspawn_info"))
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
                            PlayerLanguage.text(context.source.player, "command.announce")
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
                            PlayerLanguage.text(context.source.player, "command.reloaded")
                        }, true)
                        1
                    }
            )

        dispatcher.register(root)

        // Atalhos diretos /safari, /safari enter e /safari exit
        dispatcher.register(
            Commands.literal("safari")
                .executes { context ->
                    val player = context.source.player
                    if (player != null) SafariZoneFeature.openSafariGui(player) else context.source.sendFailure(Component.literal("Apenas jogadores."))
                    1
                }
                .then(
                    Commands.literal("enter").executes { context ->
                        val player = context.source.player
                        if (player != null) SafariZoneFeature.openSafariGui(player) else context.source.sendFailure(Component.literal("Apenas jogadores."))
                        1
                    }
                )
                .then(
                    Commands.literal("exit").executes { context ->
                        val player = context.source.player
                        if (player != null) SafariZoneFeature.exitSafari(player) else context.source.sendFailure(Component.literal("Apenas jogadores."))
                        1
                    }
                )
        )

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

    private fun testLegendary(source: CommandSourceStack, species: String?): Int {
        val player = source.player
        if (player == null) {
            source.sendFailure(PlayerLanguage.text(null, "player.only"))
            return 0
        }
        val spawned = LegendarySpawnerFeature.forceSpawn(player, species)
        if (spawned) {
            source.sendSuccess({ PlayerLanguage.text(player, "legend.test.success") }, true)
            return 1
        }
        source.sendFailure(PlayerLanguage.text(player, "legend.test.failed"))
        return 0
    }

    private fun captureCapAdminFeedback(source: CommandSourceStack, target: ServerPlayer, cap: Int): Int {
        source.sendSuccess({
            PlayerLanguage.text(source.player, "capture_cap.admin_set", "player" to target.scoreboardName, "cap" to cap)
        }, true)
        target.displayClientMessage(PlayerLanguage.text(target, "capture_cap.current", "cap" to cap), true)
        return 1
    }
}
