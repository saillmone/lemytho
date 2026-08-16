package com.opencover.app.net

/**
 * Contrat des événements échangés entre le client et le serveur relais.
 *
 * Le serveur ne comprend pas la sémantique du jeu : il route simplement des
 * événements nommés. Cette liste centralise les noms pour éviter toute dérive
 * entre l'application Kotlin et le serveur Node.js.
 */
object Protocol {

    // --- Serveur -> client (relayés par le serveur) ---

    /** La partie démarre : chaque invité reçoit ensuite son rôle/mot privé. */
    const val EVENT_GAME_START = "game:start"

    /** Rôle + mot secret d'un joueur (envoyé uniquement à ce joueur). */
    const val EVENT_GAME_PRIVATE = "game:private"

    /** État public du plateau (joueurs, ordre, tour/manche, phase). */
    const val EVENT_GAME_BOARD = "game:board"

    /** Changement de phase de jeu. */
    const val EVENT_GAME_PHASE = "game:phase"

    /** Élimination d'un joueur (rôle révélé publiquement). */
    const val EVENT_GAME_ELIMINATION = "game:elimination"

    /** Résultat final + scores. */
    const val EVENT_GAME_RESULT = "game:result"

    // --- Client -> hôte (via relay:toHost) ---

    /** L'invité a terminé sa révélation (acknowledgement). */
    const val EVENT_PLAYER_REVEAL = "player:reveal"

    /** L'invité a voté. */
    const val EVENT_PLAYER_VOTE = "player:vote"

    /** L'invité se déclare prêt à rejouer (manche suivante). */
    const val EVENT_PLAYER_READY = "player:ready"

    /** La devinette de Mr White (validée ou non par le groupe). */
    const val EVENT_PLAYER_GUESS = "player:guess"

    /** Tous les événements serveur -> client que le client doit écouter. */
    val SERVER_TO_CLIENT_EVENTS = listOf(
        EVENT_GAME_START,
        EVENT_GAME_PRIVATE,
        EVENT_GAME_BOARD,
        EVENT_GAME_PHASE,
        EVENT_GAME_ELIMINATION,
        EVENT_GAME_RESULT
    )

    /**
     * Événements que le client peut recevoir, quelle que soit sa posture :
     * - en tant qu'invité, il reçoit les événements `game:*` ;
     * - en tant qu'hôte, il reçoit les événements `player:*` relayés par le serveur.
     */
    val INCOMING_EVENTS = SERVER_TO_CLIENT_EVENTS + listOf(
        EVENT_PLAYER_REVEAL,
        EVENT_PLAYER_VOTE,
        EVENT_PLAYER_READY,
        EVENT_PLAYER_GUESS
    )

    // --- Constantes de phase (payload de game:phase) ---

    const val PHASE_REVEAL = "REVEAL"
    const val PHASE_CLUE = "CLUE"
    const val PHASE_VOTE = "VOTE"
    const val PHASE_RESULT = "RESULT"

    /** Identifiant du joueur hôte (toujours 1, attribué par le serveur). */
    const val HOST_PLAYER_ID = 1
}
