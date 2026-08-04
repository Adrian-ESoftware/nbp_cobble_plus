package com.nbp.cobbleplus.feature.impl

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.reactive.ObservableSubscription
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.nbp.cobbleplus.config.NbpConfig
import com.nbp.cobbleplus.feature.FeatureModule
import com.nbp.cobbleplus.i18n.PlayerLanguage
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.util.UUID

data class WagerChallenge(
    val challengerUuid: UUID,
    val challengerName: String,
    val targetUuid: UUID,
    val targetName: String,
    val amount: Long,
    val createdAtTick: Long
)

data class ActiveWagerSession(
    val player1Uuid: UUID,
    val player2Uuid: UUID,
    val player1Name: String,
    val player2Name: String,
    val betAmount: Long,
    val totalPool: Long,
    val acceptedAtTick: Long
)

object WagerBattleFeature : FeatureModule {
    override val name: String = "Wager Battle"
    override val isEnabled: Boolean get() = NbpConfig.data.wagerBattle.enabled

    private val logger = LoggerFactory.getLogger("NBP-WagerBattle")
    private var victorySub: ObservableSubscription<*>? = null

    // Mapeamento de Desafios Pendentes (Chave: UUID do Desafiado)
    private val pendingChallenges = mutableMapOf<UUID, WagerChallenge>()

    // Mapeamento de Sessões em Andamento
    private val activeWagers = mutableListOf<ActiveWagerSession>()

    override fun onEnable() {
        if (victorySub != null) return
        victorySub = CobblemonEvents.BATTLE_VICTORY.subscribe { event ->
            if (!isEnabled) return@subscribe

            val winners = event.winners.filterIsInstance<PlayerBattleActor>().mapNotNull { it.entity }
            val losers = event.losers.filterIsInstance<PlayerBattleActor>().mapNotNull { it.entity }

            if (winners.isEmpty() && losers.isEmpty()) return@subscribe

            val winnerUuids = winners.map { it.uuid }.toSet()
            val loserUuids = losers.map { it.uuid }.toSet()

            val sessionIterator = activeWagers.iterator()
            while (sessionIterator.hasNext()) {
                val session = sessionIterator.next()
                val isP1Participant = session.player1Uuid in winnerUuids || session.player1Uuid in loserUuids
                val isP2Participant = session.player2Uuid in winnerUuids || session.player2Uuid in loserUuids

                if (isP1Participant || isP2Participant) {
                    val winnerPlayer = winners.firstOrNull { it.uuid == session.player1Uuid || it.uuid == session.player2Uuid }
                    val loserPlayer = losers.firstOrNull { it.uuid == session.player1Uuid || it.uuid == session.player2Uuid }

                    if (winnerPlayer != null) {
                        // Vitória e pagamento do prêmio acumulado
                        val loserName = loserPlayer?.scoreboardName ?: (if (winnerPlayer.uuid == session.player1Uuid) session.player2Name else session.player1Name)
                        val winnerName = winnerPlayer.scoreboardName

                        CobbleDollarsBridge.earn(winnerPlayer, session.totalPool, false)

                        winnerPlayer.sendSystemMessage(PlayerLanguage.text(winnerPlayer, "wager.victory_winner", "loser" to loserName, "amount" to session.totalPool))

                        if (loserPlayer != null) {
                            loserPlayer.sendSystemMessage(PlayerLanguage.text(loserPlayer, "wager.defeat_loser", "winner" to winnerName, "amount" to session.betAmount))
                        }

                        val config = NbpConfig.data.wagerBattle
                        if (config.announceVictories) {
                            val server = winnerPlayer.server
                            server?.playerList?.players?.forEach { p ->
                                val broadcastMsg = PlayerLanguage.template(p, "wager.broadcast", config.announceVictories.toString(), "winner" to winnerName, "loser" to loserName, "amount" to session.totalPool)
                                p.sendSystemMessage(Component.literal(broadcastMsg))
                            }
                        }
                        logger.info("Wager Battle encerrou: $winnerName venceu $loserName e recebeu ${session.totalPool} CobbleDollars.")
                    } else {
                        // Empate ou cancelamento -> Reembolso de ambos
                        val server = winnerPlayer?.server ?: loserPlayer?.server
                        if (server != null) {
                            val p1 = server.playerList.getPlayer(session.player1Uuid)
                            val p2 = server.playerList.getPlayer(session.player2Uuid)
                            if (p1 != null) {
                                CobbleDollarsBridge.earn(p1, session.betAmount, false)
                                p1.sendSystemMessage(PlayerLanguage.text(p1, "wager.refund_draw", "amount" to session.betAmount))
                            }
                            if (p2 != null) {
                                CobbleDollarsBridge.earn(p2, session.betAmount, false)
                                p2.sendSystemMessage(PlayerLanguage.text(p2, "wager.refund_draw", "amount" to session.betAmount))
                            }
                        }
                    }
                    sessionIterator.remove()
                }
            }
        }
    }

    override fun onDisable() {
        victorySub?.unsubscribe()
        victorySub = null
        pendingChallenges.clear()
        activeWagers.clear()
    }

    fun tick(server: MinecraftServer) {
        if (!isEnabled || pendingChallenges.isEmpty()) return
        val currentTick = server.tickCount.toLong()
        val timeoutTicks = NbpConfig.data.wagerBattle.challengeTimeoutSeconds * 20L

        val iterator = pendingChallenges.entries.iterator()
        while (iterator.hasNext()) {
            val (targetUuid, challenge) = iterator.next()
            if (currentTick - challenge.createdAtTick >= timeoutTicks) {
                iterator.remove()
                val targetPlayer = server.playerList.getPlayer(targetUuid)
                val challengerPlayer = server.playerList.getPlayer(challenge.challengerUuid)
                targetPlayer?.sendSystemMessage(PlayerLanguage.text(targetPlayer, "wager.expired"))
                challengerPlayer?.sendSystemMessage(PlayerLanguage.text(challengerPlayer, "wager.expired"))
            }
        }
    }

    fun challengePlayer(challenger: ServerPlayer, targetName: String, amount: Long) {
        if (!isEnabled) {
            challenger.sendSystemMessage(PlayerLanguage.text(challenger, "wager.disabled"))
            return
        }

        val config = NbpConfig.data.wagerBattle
        if (amount < config.minWagerAmount || amount > config.maxWagerAmount) {
            challenger.sendSystemMessage(PlayerLanguage.text(challenger, "wager.min_max", "min" to config.minWagerAmount, "max" to config.maxWagerAmount))
            return
        }

        val server = challenger.server ?: return
        val target = server.playerList.getPlayerByName(targetName)
        if (target == null) {
            challenger.sendSystemMessage(PlayerLanguage.text(challenger, "wager.target_not_found", "player" to targetName))
            return
        }

        if (target.uuid == challenger.uuid) {
            challenger.sendSystemMessage(PlayerLanguage.text(challenger, "wager.self_challenge"))
            return
        }

        // Condição de Proximidade: Jogadores na mesma dimensão e dentro da distância máxima
        val maxDist = config.maxChallengeDistance.toDouble()
        if (challenger.level() != target.level() || challenger.distanceToSqr(target) > maxDist * maxDist) {
            challenger.sendSystemMessage(PlayerLanguage.text(challenger, "wager.not_nearby", "player" to target.scoreboardName, "distance" to config.maxChallengeDistance))
            return
        }

        // Verifica o saldo do desafiante
        val challengerBalance = CobbleDollarsBridge.balance(challenger)
        if (challengerBalance < BigInteger.valueOf(amount)) {
            challenger.sendSystemMessage(PlayerLanguage.text(challenger, "wager.insufficient_sender", "amount" to amount))
            return
        }

        // Verifica o saldo do desafiado
        val targetBalance = CobbleDollarsBridge.balance(target)
        if (targetBalance < BigInteger.valueOf(amount)) {
            challenger.sendSystemMessage(PlayerLanguage.text(challenger, "wager.insufficient_target", "player" to target.scoreboardName, "amount" to amount))
            return
        }

        if (pendingChallenges.containsKey(target.uuid)) {
            challenger.sendSystemMessage(PlayerLanguage.text(challenger, "wager.pending_exists"))
            return
        }

        val challenge = WagerChallenge(
            challengerUuid = challenger.uuid,
            challengerName = challenger.scoreboardName,
            targetUuid = target.uuid,
            targetName = target.scoreboardName,
            amount = amount,
            createdAtTick = server.tickCount.toLong()
        )

        pendingChallenges[target.uuid] = challenge

        challenger.sendSystemMessage(PlayerLanguage.text(challenger, "wager.challenge_sent", "player" to target.scoreboardName, "amount" to amount))

        // Envia mensagem ao desafiado com botão clicável
        val receivedText = PlayerLanguage.string(target, "wager.challenge_received", "player" to challenger.scoreboardName, "amount" to amount)
        val clickText = PlayerLanguage.string(target, "wager.click_to_accept")

        target.sendSystemMessage(Component.literal(receivedText))

        val clickComponent = Component.literal(clickText).setStyle(
            Style.EMPTY
                .withColor(0x55FF55)
                .withBold(true)
                .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/duel accept"))
                .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§aClique para aceitar o desafio!")))
        )
        target.sendSystemMessage(clickComponent)
    }

    fun acceptChallenge(target: ServerPlayer) {
        if (!isEnabled) {
            target.sendSystemMessage(PlayerLanguage.text(target, "wager.disabled"))
            return
        }

        val challenge = pendingChallenges.remove(target.uuid) ?: run {
            target.sendSystemMessage(PlayerLanguage.text(target, "wager.no_pending"))
            return
        }

        val server = target.server ?: return
        val challenger = server.playerList.getPlayer(challenge.challengerUuid)

        if (challenger == null) {
            target.sendSystemMessage(PlayerLanguage.text(target, "wager.target_not_found", "player" to challenge.challengerName))
            return
        }

        val config = NbpConfig.data.wagerBattle
        val maxDist = config.maxChallengeDistance.toDouble()
        if (challenger.level() != target.level() || challenger.distanceToSqr(target) > maxDist * maxDist) {
            target.sendSystemMessage(PlayerLanguage.text(target, "wager.not_nearby", "player" to challenger.scoreboardName, "distance" to config.maxChallengeDistance))
            challenger.sendSystemMessage(PlayerLanguage.text(challenger, "wager.not_nearby", "player" to target.scoreboardName, "distance" to config.maxChallengeDistance))
            return
        }

        val amount = challenge.amount

        // Re-verifica saldos no momento de aceitar
        val challengerBalance = CobbleDollarsBridge.balance(challenger)
        if (challengerBalance < BigInteger.valueOf(amount)) {
            target.sendSystemMessage(PlayerLanguage.text(target, "wager.insufficient_target", "player" to challenger.scoreboardName, "amount" to amount))
            challenger.sendSystemMessage(PlayerLanguage.text(challenger, "wager.insufficient_sender", "amount" to amount))
            return
        }

        val targetBalance = CobbleDollarsBridge.balance(target)
        if (targetBalance < BigInteger.valueOf(amount)) {
            target.sendSystemMessage(PlayerLanguage.text(target, "wager.insufficient_sender", "amount" to amount))
            challenger.sendSystemMessage(PlayerLanguage.text(challenger, "wager.insufficient_target", "player" to target.scoreboardName, "amount" to amount))
            return
        }

        // Retém as apostas de ambos os jogadores (Escrow)
        val spent1 = CobbleDollarsBridge.spend(challenger, amount)
        val spent2 = CobbleDollarsBridge.spend(target, amount)

        if (!spent1 || !spent2) {
            if (spent1) CobbleDollarsBridge.earn(challenger, amount, false)
            if (spent2) CobbleDollarsBridge.earn(target, amount, false)
            target.sendSystemMessage(Component.literal("§c[Wager Battle] Erro ao processar o débito da aposta."))
            return
        }

        val totalPool = amount * 2L
        val session = ActiveWagerSession(
            player1Uuid = challenger.uuid,
            player2Uuid = target.uuid,
            player1Name = challenger.scoreboardName,
            player2Name = target.scoreboardName,
            betAmount = amount,
            totalPool = totalPool,
            acceptedAtTick = server.tickCount.toLong()
        )

        activeWagers.add(session)

        val startMsgP1 = PlayerLanguage.string(challenger, "wager.accepted_start", "player1" to challenger.scoreboardName, "player2" to target.scoreboardName, "pool" to totalPool, "amount" to amount)
        val startMsgP2 = PlayerLanguage.string(target, "wager.accepted_start", "player1" to challenger.scoreboardName, "player2" to target.scoreboardName, "pool" to totalPool, "amount" to amount)

        challenger.sendSystemMessage(Component.literal(startMsgP1))
        target.sendSystemMessage(Component.literal(startMsgP2))

        // Inicia a Batalha Cobblemon Automaticamente
        startPvPBattle(challenger, target)
    }

    fun denyChallenge(target: ServerPlayer) {
        if (!isEnabled) return
        val challenge = pendingChallenges.remove(target.uuid) ?: run {
            target.sendSystemMessage(PlayerLanguage.text(target, "wager.no_pending"))
            return
        }

        val server = target.server ?: return
        val challenger = server.playerList.getPlayer(challenge.challengerUuid)

        target.sendSystemMessage(PlayerLanguage.text(target, "wager.denied_sender", "player" to challenge.challengerName))
        challenger?.sendSystemMessage(PlayerLanguage.text(challenger, "wager.denied_target", "player" to target.scoreboardName))
    }

    private fun startPvPBattle(player1: ServerPlayer, player2: ServerPlayer) {
        runCatching {
            val registry = Cobblemon.battleRegistry
            logger.info("Cobblemon BattleRegistry Class: ${registry.javaClass.name}")
            
            val methods = registry.javaClass.methods
            for (m in methods) {
                if (m.name.contains("start", ignoreCase = true)) {
                    logger.info("Encontrado método em BattleRegistry: ${m.name}(${m.parameterTypes.joinToString { it.simpleName }})")
                }
            }

            // 1. Tenta método direto que receba ServerPlayer
            val directMethod = registry.javaClass.methods.firstOrNull { m ->
                m.parameterCount >= 2 && m.parameterTypes[0].isAssignableFrom(ServerPlayer::class.java) && m.parameterTypes[1].isAssignableFrom(ServerPlayer::class.java)
            }
            if (directMethod != null) {
                logger.info("Iniciando batalha via directMethod: ${directMethod.name}")
                directMethod.invoke(registry, player1, player2)
                return
            }

            // 2. Tenta método por PlayerBattleActor ou BattleSide
            val p1Party = Cobblemon.storage.getParty(player1)
            val p2Party = Cobblemon.storage.getParty(player2)

            val p1PokemonList = runCatching {
                val occupiedMethod = p1Party.javaClass.methods.firstOrNull { 
                    it.name == "toGettablePokemon" || it.name == "toPokemonList" || it.name == "getOccupied" || it.name == "occupied" || it.name == "toCollection" || it.name == "all" 
                }
                occupiedMethod?.invoke(p1Party) as? List<*> ?: emptyList<Any>()
            }.getOrDefault(emptyList())

            val p2PokemonList = runCatching {
                val occupiedMethod = p2Party.javaClass.methods.firstOrNull { 
                    it.name == "toGettablePokemon" || it.name == "toPokemonList" || it.name == "getOccupied" || it.name == "occupied" || it.name == "toCollection" || it.name == "all" 
                }
                occupiedMethod?.invoke(p2Party) as? List<*> ?: emptyList<Any>()
            }.getOrDefault(emptyList())

            val actorConstructors = PlayerBattleActor::class.java.constructors
            var actor1: Any? = null
            var actor2: Any? = null

            for (ctor in actorConstructors) {
                runCatching {
                    if (ctor.parameterCount == 2) {
                        val types = ctor.parameterTypes
                        if (types[0] == UUID::class.java) {
                            actor1 = ctor.newInstance(player1.uuid, p1PokemonList)
                            actor2 = ctor.newInstance(player2.uuid, p2PokemonList)
                        } else if (types[0].isAssignableFrom(ServerPlayer::class.java)) {
                            actor1 = ctor.newInstance(player1, p1PokemonList)
                            actor2 = ctor.newInstance(player2, p2PokemonList)
                        }
                    } else if (ctor.parameterCount == 1 && ctor.parameterTypes[0].isAssignableFrom(ServerPlayer::class.java)) {
                        actor1 = ctor.newInstance(player1)
                        actor2 = ctor.newInstance(player2)
                    }
                }
                if (actor1 != null && actor2 != null) break
            }

            if (actor1 != null && actor2 != null) {
                val startMethod = registry.javaClass.methods.firstOrNull { m ->
                    m.name.contains("start") && m.parameterCount in 2..3
                }
                if (startMethod != null) {
                    logger.info("Iniciando batalha via startMethod (${startMethod.name}) com actors!")
                    if (startMethod.parameterCount == 2) {
                        startMethod.invoke(registry, actor1, actor2)
                    } else if (startMethod.parameterCount == 3) {
                        val formatType = startMethod.parameterTypes[0]
                        val formatVal = formatType.enumConstants?.firstOrNull() 
                            ?: formatType.fields.firstOrNull { it.name.contains("SINGLES") }?.get(null)
                        startMethod.invoke(registry, formatVal, actor1, actor2)
                    }
                } else {
                    logger.warn("Nenhum método startBattle encontrado em BattleRegistry!")
                }
            } else {
                logger.warn("Não foi possível instanciar PlayerBattleActor para a batalha!")
            }
        }.onFailure { ex ->
            logger.error("Falha ao iniciar batalha automática entre ${player1.scoreboardName} e ${player2.scoreboardName}", ex)
        }
    }
}
