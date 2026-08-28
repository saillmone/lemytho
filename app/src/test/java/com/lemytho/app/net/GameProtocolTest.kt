package com.lemytho.app.net

import com.lemytho.app.data.model.Player
import com.lemytho.app.data.model.PlayerStatus
import com.lemytho.app.data.model.Role
import com.lemytho.app.engine.Victory
import com.lemytho.app.ui.VotePhase
import com.lemytho.app.ui.VoteReveal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Couvre la couche de projection public/privé du protocole multijoueur.
 *
 * Le point critique est la non-fuite des secrets : un plateau public ne doit
 * jamais contenir les rôles ni les mots, sauf au moment d'une élimination ou
 * du résultat final (où la révélation est voulue).
 */
class GameProtocolTest {

    private val players = listOf(
        Player(id = 1, pseudo = "Alice", role = Role.CITIZEN, assignedWord = "Pizza"),
        Player(id = 2, pseudo = "Bob", role = Role.IMPOSTOR, assignedWord = "Burger"),
        Player(id = 3, pseudo = "Carla", role = Role.UNKNOWN, assignedWord = ""),
    )

    // --- Projection : pas de fuite de secrets ---

    @Test
    fun `le plateau public n'expose pas le mot secret et masque le role des actifs`() {
        val payload = boardPayload()

        val playersArray = payload.getJSONArray("players")
        for (i in 0 until playersArray.length()) {
            val p = playersArray.getJSONObject(i)
            assertFalse("un plateau public ne doit pas exposer de mot", p.has("word"))
            assertFalse("un plateau public ne doit pas exposer assignedWord", p.has("assignedWord"))
            // Les joueurs actifs ne doivent pas voir leur rôle fuiter.
            assertTrue("le rôle d'un joueur actif doit être null", p.isNull("role"))
        }
        // Aucun mot de la paire ne doit transiter.
        assertFalse(payload.has("wordPair"))
        assertFalse(payload.has("word"))
    }

    @Test
    fun `le role d'un joueur elimine est expose publiquement`() {
        val eliminated = listOf(
            Player(
                id = 1, pseudo = "Alice", role = Role.CITIZEN, assignedWord = "Pizza",
                status = PlayerStatus.ELIMINATED
            )
        )
        val payload = GameProtocol.boardPayload(
            players = eliminated,
            clueOrder = listOf(1),
            roundNumber = 1,
            turnNumber = 1,
            category = null,
            votePhase = VotePhase.IDLE,
            currentVoterId = null,
            tiedCandidates = emptySet()
        )

        val board = GameProtocol.parseBoard(payload)
        assertEquals(Role.CITIZEN, board.players.single().role)
    }

    @Test
    fun `le payload prive expose role et mot uniquement a son destinataire`() {
        val payload = GameProtocol.privatePayload(Role.IMPOSTOR, "Burger")

        assertEquals("IMPOSTOR", payload.getString("role"))
        assertEquals("Burger", payload.getString("word"))
        // Le payload privé ne contient que ces deux champs : pas d'identifiant de joueur.
        assertEquals(2, payload.length())
    }

    // --- Aller-retour : plateau public ---

    @Test
    fun `aller-retour du plateau public`() {
        val payload = GameProtocol.boardPayload(
            players = players,
            clueOrder = listOf(1, 2, 3),
            roundNumber = 1,
            turnNumber = 2,
            category = "Nourriture",
            votePhase = VotePhase.VOTING,
            currentVoterId = 2,
            tiedCandidates = setOf(2, 3)
        )

        val board = GameProtocol.parseBoard(payload)

        assertEquals(listOf(1, 2, 3), board.clueOrder)
        assertEquals(1, board.roundNumber)
        assertEquals(2, board.turnNumber)
        assertEquals("Nourriture", board.category)
        assertEquals(VotePhase.VOTING, board.votePhase)
        assertEquals(2, board.currentVoterId)
        assertEquals(setOf(2, 3), board.tiedCandidates)
        assertEquals(listOf(1, 2, 3), board.players.map { it.playerId })
        assertEquals(
            listOf("Alice", "Bob", "Carla"),
            board.players.map { it.pseudo }
        )
    }

    @Test
    fun `le plateau public conserve les statuts sans exposer les roles`() {
        val payload = boardPayload()
        val board = GameProtocol.parseBoard(payload)

        assertEquals(
            listOf(PlayerStatus.ACTIVE, PlayerStatus.ACTIVE, PlayerStatus.ACTIVE),
            board.players.map { it.status }
        )
    }

    @Test
    fun `la categorie absente est parsee en null`() {
        val payload = GameProtocol.boardPayload(
            players = players,
            clueOrder = listOf(1, 2, 3),
            roundNumber = 1,
            turnNumber = 1,
            category = null,
            votePhase = VotePhase.IDLE,
            currentVoterId = null,
            tiedCandidates = emptySet()
        )

        val board = GameProtocol.parseBoard(payload)
        assertNull(board.category)
        assertNull(board.currentVoterId)
    }

    // --- Aller-retour : révélation privée ---

    @Test
    fun `aller-retour de la revelation privee`() {
        val payload = GameProtocol.privatePayload(Role.IMPOSTOR, "Burger")
        val (role, word) = GameProtocol.parsePrivate(payload)!!

        assertEquals(Role.IMPOSTOR, role)
        assertEquals("Burger", word)
    }

    @Test
    fun `l'Inconnu recoit un mot vide`() {
        val payload = GameProtocol.privatePayload(Role.UNKNOWN, "")
        val (role, word) = GameProtocol.parsePrivate(payload)!!

        assertEquals(Role.UNKNOWN, role)
        assertEquals("", word)
    }

    @Test
    fun `un payload prive invalide est rejete`() {
        val payload = org.json.JSONObject().put("role", "ROLE_INEXISTANT")
        assertNull(GameProtocol.parsePrivate(payload))
    }

    // --- Aller-retour : élimination ---

    @Test
    fun `aller-retour de l'elimination`() {
        val payload = GameProtocol.eliminationPayload(2, "Bob", Role.IMPOSTOR, 3)
        val snapshot = GameProtocol.parseElimination(payload)!!

        assertEquals(2, snapshot.playerId)
        assertEquals("Bob", snapshot.pseudo)
        assertEquals(Role.IMPOSTOR, snapshot.role)
        assertEquals(3, snapshot.turnNumber)
        assertFalse(snapshot.guessResolved)
        assertFalse(snapshot.guessCorrect)
        assertNull(snapshot.guessText)
        assertTrue(snapshot.votes.isEmpty())
    }

    @Test
    fun `aller-retour de l'elimination avec votes`() {
        val payload = GameProtocol.eliminationPayload(
            playerId = 2,
            pseudo = "Bob",
            role = Role.CITIZEN,
            turnNumber = 3,
            votes = listOf(
                VoteReveal("Alice", "Bob"),
                VoteReveal("Carla", "Bob")
            )
        )
        val snapshot = GameProtocol.parseElimination(payload)!!

        assertEquals(2, snapshot.votes.size)
        assertEquals("Alice", snapshot.votes[0].voterPseudo)
        assertEquals("Bob", snapshot.votes[0].targetPseudo)
        assertEquals("Carla", snapshot.votes[1].voterPseudo)
    }

    @Test
    fun `aller-retour de l'elimination avec devinette resolue`() {
        val payload = GameProtocol.eliminationPayload(
            2, "Bob", Role.UNKNOWN, 4,
            guessResolved = true,
            guessCorrect = true,
            guessText = "Tour Eiffel"
        )
        val snapshot = GameProtocol.parseElimination(payload)!!

        assertTrue(snapshot.guessResolved)
        assertTrue(snapshot.guessCorrect)
        assertEquals(Role.UNKNOWN, snapshot.role)
        assertEquals(4, snapshot.turnNumber)
        assertEquals("Tour Eiffel", snapshot.guessText)
    }

    // --- Aller-retour : résultat final ---

    @Test
    fun `aller-retour du resultat final avec scores`() {
        val payload = GameProtocol.resultPayload(
            players = players,
            victory = Victory.Unknown(setOf(3), byGuess = true),
            totalScores = mapOf(1 to 2, 2 to 0, 3 to 5)
        )

        val result = GameProtocol.parseResult(payload)

        assertTrue(result.victory is Victory.Unknown)
        val unknown = result.victory as Victory.Unknown
        assertEquals(setOf(3), unknown.winnerIds)
        assertTrue(unknown.byGuess)
        assertEquals(3, result.players.size)
        assertEquals(mapOf(1 to 2, 2 to 0, 3 to 5), result.totalScores)
    }

    @Test
    fun `aller-retour d'une victoire des citoyens`() {
        val payload = GameProtocol.resultPayload(
            players = players,
            victory = Victory.Citizen,
            totalScores = mapOf(1 to 3)
        )

        val result = GameProtocol.parseResult(payload)
        assertEquals(Victory.Citizen, result.victory)
        assertEquals(mapOf(1 to 3), result.totalScores)
    }

    private fun boardPayload() = GameProtocol.boardPayload(
        players = players,
        clueOrder = listOf(1, 2, 3),
        roundNumber = 1,
        turnNumber = 1,
        category = "Nourriture",
        votePhase = VotePhase.IDLE,
        currentVoterId = null,
        tiedCandidates = emptySet()
    )
}
