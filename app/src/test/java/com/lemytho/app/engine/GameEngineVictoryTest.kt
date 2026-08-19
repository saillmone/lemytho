package com.lemytho.app.engine

import com.lemytho.app.data.model.Player
import com.lemytho.app.data.model.PlayerStatus
import com.lemytho.app.data.model.Role
import org.junit.Assert.assertEquals
import org.junit.Test

class GameEngineVictoryTest {

    private val engine = GameEngine()

    private fun player(id: Int, role: Role, status: PlayerStatus = PlayerStatus.ACTIVE) =
        Player(id = id, pseudo = "P$id", role = role, assignedWord = "", status = status)

    // --- determineWinner ---

    @Test
    fun `victoire des citoyens quand tous les imposteurs et inconnu sont elimines`() {
        val players = listOf(
            player(1, Role.CITIZEN),
            player(2, Role.CITIZEN),
            player(3, Role.IMPOSTOR, PlayerStatus.ELIMINATED),
            player(4, Role.UNKNOWN, PlayerStatus.ELIMINATED)
        )
        assertEquals(Victory.Citizen, engine.determineWinner(players))
    }

    @Test
    fun `victoire des citoyens meme avec un seul citoyen si aucun imposteur ne reste`() {
        val players = listOf(
            player(1, Role.CITIZEN),
            player(2, Role.IMPOSTOR, PlayerStatus.ELIMINATED),
            player(3, Role.UNKNOWN, PlayerStatus.ELIMINATED)
        )
        assertEquals(Victory.Citizen, engine.determineWinner(players))
    }

    @Test
    fun `victoire imposteur en duel final contre un citoyen`() {
        val players = listOf(
            player(1, Role.CITIZEN),
            player(2, Role.IMPOSTOR)
        )
        assertEquals(Victory.Impostor, engine.determineWinner(players))
    }

    @Test
    fun `victoire imposteur quand les citoyens sont tous elimines`() {
        val players = listOf(
            player(1, Role.CITIZEN, PlayerStatus.ELIMINATED),
            player(2, Role.IMPOSTOR),
            player(3, Role.IMPOSTOR)
        )
        assertEquals(Victory.Impostor, engine.determineWinner(players))
    }

    @Test
    fun `victoire inconnu en duel final contre un citoyen`() {
        val players = listOf(
            player(1, Role.CITIZEN),
            player(2, Role.UNKNOWN)
        )
        assertEquals(Victory.Unknown(setOf(2), byGuess = false), engine.determineWinner(players))
    }

    @Test
    fun `victoire inconnu quand les citoyens sont tous elimines`() {
        val players = listOf(
            player(1, Role.CITIZEN, PlayerStatus.ELIMINATED),
            player(2, Role.UNKNOWN),
            player(3, Role.UNKNOWN)
        )
        assertEquals(Victory.Unknown(setOf(2, 3), byGuess = false), engine.determineWinner(players))
    }

    @Test
    fun `victoire partagee quand citoyens elimines et imposteurs et inconnu en jeu`() {
        val players = listOf(
            player(1, Role.CITIZEN, PlayerStatus.ELIMINATED),
            player(2, Role.IMPOSTOR),
            player(3, Role.UNKNOWN)
        )
        assertEquals(Victory.Combined, engine.determineWinner(players))
    }

    @Test
    fun `partie en cours avec plusieurs citoyens`() {
        val players = listOf(
            player(1, Role.CITIZEN),
            player(2, Role.CITIZEN),
            player(3, Role.IMPOSTOR)
        )
        assertEquals(Victory.Ongoing, engine.determineWinner(players))
    }

    @Test
    fun `partie en cours avec un citoyen un imposteur et un inconnu`() {
        val players = listOf(
            player(1, Role.CITIZEN),
            player(2, Role.IMPOSTOR),
            player(3, Role.UNKNOWN)
        )
        assertEquals(Victory.Ongoing, engine.determineWinner(players))
    }

    @Test
    fun `partie en cours avec un citoyen et deux imposteurs`() {
        val players = listOf(
            player(1, Role.CITIZEN),
            player(2, Role.IMPOSTOR),
            player(3, Role.IMPOSTOR)
        )
        assertEquals(Victory.Ongoing, engine.determineWinner(players))
    }

    // --- computeScores ---

    @Test
    fun `scoring de victoire des citoyens`() {
        val players = listOf(
            player(1, Role.CITIZEN),
            player(2, Role.CITIZEN),
            player(3, Role.CITIZEN, PlayerStatus.ELIMINATED),
            player(4, Role.IMPOSTOR, PlayerStatus.ELIMINATED)
        )
        val scores = engine.computeScores(players, Victory.Citizen)
        assertEquals(mapOf(1 to 2, 2 to 2), scores)
    }

    @Test
    fun `scoring de victoire imposteur`() {
        val players = listOf(
            player(1, Role.CITIZEN),
            player(2, Role.IMPOSTOR),
            player(3, Role.IMPOSTOR, PlayerStatus.ELIMINATED)
        )
        val scores = engine.computeScores(players, Victory.Impostor)
        assertEquals(mapOf(2 to 10), scores)
    }

    @Test
    fun `scoring de victoire inconnu par devinette`() {
        val players = listOf(
            player(1, Role.CITIZEN),
            player(2, Role.IMPOSTOR),
            player(4, Role.UNKNOWN, PlayerStatus.ELIMINATED)
        )
        val scores = engine.computeScores(players, Victory.Unknown(setOf(4), byGuess = true))
        assertEquals(mapOf(4 to 6), scores)
    }

    @Test
    fun `scoring de victoire inconnu par survie`() {
        val players = listOf(
            player(1, Role.CITIZEN, PlayerStatus.ELIMINATED),
            player(4, Role.UNKNOWN),
            player(5, Role.UNKNOWN)
        )
        val scores = engine.computeScores(players, Victory.Unknown(setOf(4, 5), byGuess = false))
        assertEquals(mapOf(4 to 6, 5 to 6), scores)
    }

    @Test
    fun `scoring de victoire partagee`() {
        val players = listOf(
            player(1, Role.CITIZEN, PlayerStatus.ELIMINATED),
            player(2, Role.IMPOSTOR),
            player(3, Role.IMPOSTOR, PlayerStatus.ELIMINATED),
            player(4, Role.UNKNOWN)
        )
        val scores = engine.computeScores(players, Victory.Combined)
        assertEquals(mapOf(2 to 10, 4 to 6), scores)
    }

    @Test
    fun `scoring vide pour partie en cours`() {
        val players = listOf(
            player(1, Role.CITIZEN),
            player(2, Role.IMPOSTOR)
        )
        assertEquals(emptyMap<Int, Int>(), engine.computeScores(players, Victory.Ongoing))
    }
}
