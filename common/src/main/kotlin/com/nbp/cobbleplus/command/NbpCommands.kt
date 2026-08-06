package com.nbp.cobbleplus.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.LongArgumentType
import com.nbp.cobbleplus.config.NbpConfig
import com.nbp.cobbleplus.feature.FeatureManager
import com.nbp.cobbleplus.feature.impl.AutoAnnouncerFeature
import com.nbp.cobbleplus.feature.impl.CatchComboFeature
import com.nbp.cobbleplus.feature.impl.PartyHealFeature
import com.nbp.cobbleplus.feature.impl.LegendarySpawnerFeature
import com.nbp.cobbleplus.feature.impl.CaptureCapFeature
import com.nbp.cobbleplus.feature.impl.EconomyFeature
import com.nbp.cobbleplus.feature.impl.PointsFeature
import com.nbp.cobbleplus.feature.impl.SafariZoneFeature
import com.nbp.cobbleplus.feature.impl.WagerBattleFeature
import com.nbp.cobbleplus.feature.impl.ServerEventsFeature
import com.nbp.cobbleplus.feature.impl.ServerEventType
import com.nbp.cobbleplus.feature.impl.MissionsFeature
import com.nbp.cobbleplus.feature.impl.GtsFeature
import com.nbp.cobbleplus.config.MissionsConfigFile
import com.nbp.cobbleplus.mission.MissionCycle
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
                                "§e/nbp mission §7- Abre a tela de missões diárias e semanais\n" +
                                "§e/nbp mission list §7- Lista suas missões no chat\n" +
                                "§e/nbp gts §7- Abre o Global Trade Station\n" +
                                "§e/nbp gts sell <slot> <preço> §7- Anuncia um Pokémon\n" +
                                "§e/nbp gts cancel <id> §7- Cancela um anúncio seu\n" +
                                "§e/nbp gts collect §7- Coleta o dinheiro das vendas\n" +
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
                Commands.literal("gts")
                    .executes { context ->
                        val player = context.source.player
                        if (player == null) context.source.sendFailure(Component.literal("Apenas jogadores podem abrir o GTS."))
                        else GtsFeature.open(player)
                        1
                    }
                    .then(
                        Commands.literal("sell")
                            .then(Commands.argument("slot", IntegerArgumentType.integer(1, 6))
                                .then(Commands.argument("price", LongArgumentType.longArg(1))
                                    .executes { context ->
                                        val player = context.source.player
                                        if (player == null) { context.source.sendFailure(Component.literal("Apenas jogadores podem vender Pokémon.")); 0 }
                                        else if (GtsFeature.sell(player, IntegerArgumentType.getInteger(context, "slot"), LongArgumentType.getLong(context, "price"))) 1 else 0
                                    }))
                    )
                    .then(
                        Commands.literal("buy")
                            .then(Commands.argument("id", LongArgumentType.longArg(1)).executes { context ->
                                val player = context.source.player
                                if (player == null) { context.source.sendFailure(Component.literal("Apenas jogadores podem comprar Pokémon.")); 0 }
                                else if (GtsFeature.purchase(player, LongArgumentType.getLong(context, "id"))) 1 else 0
                            })
                    )
                    .then(
                        Commands.literal("cancel")
                            .then(Commands.argument("id", LongArgumentType.longArg(1)).executes { context ->
                                val player = context.source.player
                                if (player == null) { context.source.sendFailure(Component.literal("Apenas jogadores podem cancelar anúncios.")); 0 }
                                else if (GtsFeature.cancel(player, LongArgumentType.getLong(context, "id"))) 1 else 0
                            })
                    )
                    .then(
                        Commands.literal("collect").executes { context ->
                            val player = context.source.player
                            if (player == null) {
                                context.source.sendFailure(Component.literal("Apenas jogadores podem coletar pagamentos.")); 0
                            } else {
                                GtsFeature.collect(player)
                                1
                            }
                        }
                    )
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
                Commands.literal("points")
                    .executes { context ->
                        val player = context.source.player
                        if (player == null) {
                            context.source.sendFailure(PlayerLanguage.text(null, "player.only")); 0
                        } else {
                            showPoints(context.source, player); 1
                        }
                    }
                    .then(Commands.literal("view").executes { context ->
                        val player = context.source.player
                        if (player == null) {
                            context.source.sendFailure(PlayerLanguage.text(null, "player.only")); 0
                        } else {
                            PointsFeature.openView(player); 1
                        }
                    })
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
                Commands.literal("mission")
                    .executes { context ->
                        val player = context.source.player
                        if (player != null) MissionsFeature.openView(player) else context.source.sendFailure(Component.literal("Apenas jogadores."))
                        1
                    }
                    .then(
                        Commands.literal("open").executes { context ->
                            val player = context.source.player
                            if (player != null) MissionsFeature.openView(player) else context.source.sendFailure(Component.literal("Apenas jogadores."))
                            1
                        }
                    )
                    .then(
                        Commands.literal("list").executes { context ->
                            val player = context.source.player
                            if (player == null) { context.source.sendFailure(PlayerLanguage.text(null, "player.only")); return@executes 0 }
                            MissionsFeature.listFor(player).forEach { player.sendSystemMessage(Component.literal(it)) }
                            1
                        }
                    )
                    .then(
                        Commands.literal("reset")
                            .requires { it.hasPermission(2) }
                            .then(
                                Commands.literal("daily").executes { context ->
                                    val player = context.source.player
                                    if (player == null) { context.source.sendFailure(PlayerLanguage.text(null, "player.only")); return@executes 0 }
                                    MissionsFeature.resetDaily(player, context.source.server)
                                    1
                                }
                            )
                            .then(
                                Commands.literal("weekly").executes { context ->
                                    MissionsFeature.resetWeekly(context.source.server)
                                    1
                                }
                            )
                    )
                    .then(
                        Commands.literal("complete")
                            .requires { it.hasPermission(2) }
                            .then(
                                Commands.argument("player", EntityArgument.player())
                                    .then(
                                        Commands.argument("cycle", StringArgumentType.word())
                                            .executes { context ->
                                                val target = EntityArgument.getPlayer(context, "player")
                                                val cycle = runCatching { MissionCycle.valueOf(StringArgumentType.getString(context, "cycle").uppercase()) }.getOrNull()
                                                if (cycle == null) {
                                                    context.source.sendFailure(Component.literal("§cCiclo inválido: daily | weekly"))
                                                    return@executes 0
                                                }
                                                val completed = MissionsFeature.forceCompleteCycle(target, cycle)
                                                context.source.sendSuccess({ Component.literal("§aCompletadas $completed missão(ões) (${cycle.name.lowercase()}).") }, true)
                                                1
                                            }
                                    )
                            )
                    )
                    .then(
                        Commands.literal("reward")
                            .requires { it.hasPermission(2) }
                            .then(
                                Commands.argument("player", EntityArgument.player())
                                    .executes { context ->
                                        val target = EntityArgument.getPlayer(context, "player")
                                        val bucketId = MissionsConfigFile.data.buckets.keys.firstOrNull()
                                        val rolls = if (bucketId != null) MissionsFeature.testReward(target, bucketId) else emptyList()
                                        context.source.sendSuccess({ Component.literal("§a$rolls recompensa(s) gerada(s) para §e${target.scoreboardName}§a (bucket: $bucketId).") }, true)
                                        1
                                    }
                            )
                    )
                    .then(
                        Commands.literal("reload")
                            .requires { it.hasPermission(2) }
                            .executes { context ->
                                MissionsConfigFile.load()
                                context.source.sendSuccess({ Component.literal("§amissions.json recarregado.") }, true)
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

        // Registrar atalho /duel para apostas em PvP
        dispatcher.register(
            Commands.literal("duel")
                .executes { context ->
                    val player = context.source.player
                    if (player != null) player.sendSystemMessage(PlayerLanguage.text(player, "wager.usage"))
                    1
                }
                .then(
                    Commands.literal("accept")
                        .executes { context ->
                            val player = context.source.player
                            if (player != null) WagerBattleFeature.acceptChallenge(player)
                            1
                        }
                )
                .then(
                    Commands.literal("deny")
                        .executes { context ->
                            val player = context.source.player
                            if (player != null) WagerBattleFeature.denyChallenge(player)
                            1
                        }
                )
                .then(
                    Commands.argument("target", StringArgumentType.word())
                        .then(
                            Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes { context ->
                                    val player = context.source.player
                                    if (player != null) {
                                        val targetName = StringArgumentType.getString(context, "target")
                                        val amount = LongArgumentType.getLong(context, "amount")
                                        WagerBattleFeature.challengePlayer(player, targetName, amount)
                                    }
                                    1
                                }
                        )
                )
        )

        // /event
        dispatcher.register(
            Commands.literal("event")
                // /event — show current event status
                .executes { context ->
                    val player = context.source.player
                    if (player == null) { context.source.sendFailure(PlayerLanguage.text(null, "player.only")); return@executes 0 }
                    player.sendSystemMessage(Component.literal(ServerEventsFeature.getStatusMessage(player)))
                    1
                }
                // /event info
                .then(Commands.literal("info")
                    .executes { context ->
                        val player = context.source.player
                        if (player == null) { context.source.sendFailure(PlayerLanguage.text(null, "player.only")); return@executes 0 }
                        player.sendSystemMessage(Component.literal(ServerEventsFeature.getStatusMessage(player)))
                        1
                    }
                )
                // /event claim — for bounty
                .then(Commands.literal("claim")
                    .executes { context ->
                        val player = context.source.player
                        if (player == null) { context.source.sendFailure(PlayerLanguage.text(null, "player.only")); return@executes 0 }
                        ServerEventsFeature.handleBountyClaim(player)
                        1
                    }
                )
                .then(Commands.literal("stop")
                    .requires { it.hasPermission(2) }
                    .executes { context ->
                        if (ServerEventsFeature.stopEvent(context.source.server)) {
                            context.source.sendSuccess({ Component.literal("§a[Event] Active event stopped.") }, true)
                            1
                        } else {
                            context.source.sendFailure(Component.literal("§c[Event] There is no active event."))
                            0
                        }
                    }
                )
                // /event trigger <eventType> — admin only
                .then(Commands.literal("trigger")
                    .requires { it.hasPermission(2) }
                    .then(
                        Commands.argument("eventType", StringArgumentType.word())
                            .executes { context ->
                                val sv = context.source.server
                                val typeName = StringArgumentType.getString(context, "eventType").uppercase()
                                val eventType = runCatching { ServerEventType.valueOf(typeName) }.getOrNull()
                                if (eventType == null) {
                                    context.source.sendFailure(Component.literal("§cUnknown event type: $typeName. Valid: ${ServerEventType.values().joinToString { it.name.lowercase() }}"))
                                    return@executes 0
                                }
                                ServerEventsFeature.triggerEvent(sv, eventType)
                                context.source.sendSuccess({ Component.literal("§a[Event] Triggered event: ${eventType.key}") }, true)
                                1
                            }
                    )
                )
        )

        dispatcher.register(
            Commands.literal("shiny").executes { context ->
                val player = context.source.player
                if (player == null) {
                    context.source.sendFailure(PlayerLanguage.text(null, "player.only"))
                    0
                } else {
                    ServerEventsFeature.getShinyStatusMessages(player).forEach(player::sendSystemMessage)
                    1
                }
            }
        )
    }

    private fun showPoints(source: CommandSourceStack, target: ServerPlayer) {
        val viewer = source.player
        val header = if (viewer != null && viewer.uuid == target.uuid) {
            PlayerLanguage.string(viewer, "points.status_header")
        } else {
            PlayerLanguage.string(viewer, "points.status_header_other", "player" to target.scoreboardName)
        }
        val body = PointsFeature.getAll(target).entries.joinToString("\n") { (type, amount) ->
            "§7${type.displayName(viewer)}: §a$amount"
        }
        source.sendSuccess({ Component.literal("$header\n$body") }, false)
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
