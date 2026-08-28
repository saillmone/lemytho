package com.lemytho.app.net

import com.lemytho.app.data.model.Player
import com.lemytho.app.data.model.Role
import com.lemytho.app.engine.Victory
import com.lemytho.app.ui.VotePhase
import com.lemytho.app.ui.VoteReveal

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
        guessResolved: Boolean = false,
        guessCorrect: Boolean = false,
        guessText: String? = null,
        votes: List<VoteReveal> = emptyList()
    ) {
        connectionManager.broadcast(
            Protocol.EVENT_GAME_ELIMINATION,
            GameProtocol.eliminationPayload(
                playerId, pseudo, role, turnNumber, guessResolved, guessCorrect, guessText, votes
            )
        )
    }

    fun sendResult(players: List<Player>, victory: Victory, totalScores: Map<Int, Int>) {
        connectionManager.broadcast(
            Protocol.EVENT_GAME_RESULT,
            GameProtocol.resultPayload(players, victory, totalScores)
        )
    }

    /** Annule la partie et renvoie les invités au salon (plus assez de joueurs). */
    fun sendCancelled() {
        connectionManager.broadcast(Protocol.EVENT_GAME_CANCELLED, org.json.JSONObject())
    }
}
