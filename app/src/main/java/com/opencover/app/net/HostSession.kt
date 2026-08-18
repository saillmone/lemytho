package com.opencover.app.net

import com.opencover.app.data.model.Player
import com.opencover.app.data.model.Role
import com.opencover.app.engine.Victory
import com.opencover.app.ui.VotePhase

/**
 * Projection de l'état complet de l'hôte vers les invités.
 *
 * L'hôte possède la vérité (rôles + mots de tous). Cette classe ne diffuse
 * jamais l'état brut : elle émet des événements publics (plateau, élimination,
 * résultat) et un événement privé (rôle + mot) adressé au seul joueur concerné.
 */
class HostSession(private val connectionManager: ConnectionManager) {

    fun sendStart(category: String?) {
        connectionManager.broadcast(Protocol.EVENT_GAME_START, GameProtocol.startPayload(category))
    }

    fun sendPrivate(playerId: Int, role: Role, word: String) {
        connectionManager.sendToPlayer(
            playerId,
            Protocol.EVENT_GAME_PRIVATE,
            GameProtocol.privatePayload(role, word)
        )
    }

    fun sendPhase(phase: String) {
        connectionManager.broadcast(Protocol.EVENT_GAME_PHASE, GameProtocol.phasePayload(phase))
    }

    fun sendRevealAck(acked: Int, total: Int) {
        connectionManager.broadcast(Protocol.EVENT_GAME_REVEAL_ACK, GameProtocol.revealAckPayload(acked, total))
    }

    fun sendBoard(
        players: List<Player>,
        clueOrder: List<Int>,
        roundNumber: Int,
        turnNumber: Int,
        category: String?,
        votePhase: VotePhase,
        currentVoterId: Int?,
        tiedCandidates: Set<Int>
    ) {
        connectionManager.broadcast(
            Protocol.EVENT_GAME_BOARD,
            GameProtocol.boardPayload(
                players, clueOrder, roundNumber, turnNumber, category,
                votePhase, currentVoterId, tiedCandidates
            )
        )
    }

    fun sendElimination(
        playerId: Int,
        pseudo: String,
        role: Role,
        turnNumber: Int,
        guessResolved: Boolean = false
    ) {
        connectionManager.broadcast(
            Protocol.EVENT_GAME_ELIMINATION,
            GameProtocol.eliminationPayload(playerId, pseudo, role, turnNumber, guessResolved)
        )
    }

    fun sendResult(players: List<Player>, victory: Victory, totalScores: Map<Int, Int>) {
        connectionManager.broadcast(
            Protocol.EVENT_GAME_RESULT,
            GameProtocol.resultPayload(players, victory, totalScores)
        )
    }
}
