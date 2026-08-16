package com.opencover.app.engine

import com.opencover.app.data.model.Player
import com.opencover.app.data.model.PlayerStatus
import com.opencover.app.data.model.Role
import org.junit.Assert.assertEquals
import org.junit.Test

class GameEngineVictoryTest {

    private val engine = GameEngine()

    private fun player(id: Int, role: Role, status: PlayerStatus = PlayerStatus.ACTIVE) =
        Player(id = id, pseudo = "P$id", role = role, assignedWord = "", status = status)

    // --- determineWinner ---

    @Test
    fun `victoire civile quand tous les infiltres et mr white sont elimines`() {
        val players = listOf(
            player(1, Role.CIVIL),
            player(2, Role.CIVIL),
            player(3, Role.UNDERCOVER, PlayerStatus.ELIMINATED),
            player(4, Role.MR_WHITE, PlayerStatus.ELIMINATED)
        )
        assertEquals(Victory.Civil, engine.determineWinner(players))
    }

    @Test
    fun `victoire civile meme avec un seul civil si aucun imposteur ne reste`() {
        val players = listOf(
            player(1, Role.CIVIL),
            player(2, Role.UNDERCOVER, PlayerStatus.ELIMINATED),
            player(3, Role.MR_WHITE, PlayerStatus.ELIMINATED)
        )
        assertEquals(Victory.Civil, engine.determineWinner(players))
    }

    @Test
    fun `victoire undercover en duel final contre un civil`() {
        val players = listOf(
            player(1, Role.CIVIL),
            player(2, Role.UNDERCOVER)
        )
        assertEquals(Victory.Undercover, engine.determineWinner(players))
    }

    @Test
    fun `victoire undercover quand les civils sont tous elimines`() {
        val players = listOf(
            player(1, Role.CIVIL, PlayerStatus.ELIMINATED),
            player(2, Role.UNDERCOVER),
            player(3, Role.UNDERCOVER)
        )
        assertEquals(Victory.Undercover, engine.determineWinner(players))
    }

    @Test
    fun `victoire mr white en duel final contre un civil`() {
        val players = listOf(
            player(1, Role.CIVIL),
            player(2, Role.MR_WHITE)
        )
        assertEquals(Victory.MrWhite(setOf(2), byGuess = false), engine.determineWinner(players))
    }

    @Test
    fun `victoire mr white quand les civils sont tous elimines`() {
        val players = listOf(
            player(1, Role.CIVIL, PlayerStatus.ELIMINATED),
            player(2, Role.MR_WHITE),
            player(3, Role.MR_WHITE)
        )
        assertEquals(Victory.MrWhite(setOf(2, 3), byGuess = false), engine.determineWinner(players))
    }

    @Test
    fun `victoire partagee quand civils elimines et infiltres et mr white en jeu`() {
        val players = listOf(
            player(1, Role.CIVIL, PlayerStatus.ELIMINATED),
            player(2, Role.UNDERCOVER),
            player(3, Role.MR_WHITE)
        )
        assertEquals(Victory.Combined, engine.determineWinner(players))
    }

    @Test
    fun `partie en cours avec plusieurs civils`() {
        val players = listOf(
            player(1, Role.CIVIL),
            player(2, Role.CIVIL),
            player(3, Role.UNDERCOVER)
        )
        assertEquals(Victory.Ongoing, engine.determineWinner(players))
    }

    @Test
    fun `partie en cours avec un civil un infiltre et un mr white`() {
        val players = listOf(
            player(1, Role.CIVIL),
            player(2, Role.UNDERCOVER),
            player(3, Role.MR_WHITE)
        )
        assertEquals(Victory.Ongoing, engine.determineWinner(players))
    }

    @Test
    fun `partie en cours avec un civil et deux infiltres`() {
        val players = listOf(
            player(1, Role.CIVIL),
            player(2, Role.UNDERCOVER),
            player(3, Role.UNDERCOVER)
        )
        assertEquals(Victory.Ongoing, engine.determineWinner(players))
    }

    // --- computeScores ---

    @Test
    fun `scoring de victoire civile`() {
        val players = listOf(
            player(1, Role.CIVIL),
            player(2, Role.CIVIL),
            player(3, Role.CIVIL, PlayerStatus.ELIMINATED),
            player(4, Role.UNDERCOVER, PlayerStatus.ELIMINATED)
        )
        val scores = engine.computeScores(players, Victory.Civil)
        assertEquals(mapOf(1 to 2, 2 to 2), scores)
    }

    @Test
    fun `scoring de victoire undercover`() {
        val players = listOf(
            player(1, Role.CIVIL),
            player(2, Role.UNDERCOVER),
            player(3, Role.UNDERCOVER, PlayerStatus.ELIMINATED)
        )
        val scores = engine.computeScores(players, Victory.Undercover)
        assertEquals(mapOf(2 to 10), scores)
    }

    @Test
    fun `scoring de victoire mr white par devinette`() {
        val players = listOf(
            player(1, Role.CIVIL),
            player(2, Role.UNDERCOVER),
            player(4, Role.MR_WHITE, PlayerStatus.ELIMINATED)
        )
        val scores = engine.computeScores(players, Victory.MrWhite(setOf(4), byGuess = true))
        assertEquals(mapOf(4 to 6), scores)
    }

    @Test
    fun `scoring de victoire mr white par survie`() {
        val players = listOf(
            player(1, Role.CIVIL, PlayerStatus.ELIMINATED),
            player(4, Role.MR_WHITE),
            player(5, Role.MR_WHITE)
        )
        val scores = engine.computeScores(players, Victory.MrWhite(setOf(4, 5), byGuess = false))
        assertEquals(mapOf(4 to 6, 5 to 6), scores)
    }

    @Test
    fun `scoring de victoire partagee`() {
        val players = listOf(
            player(1, Role.CIVIL, PlayerStatus.ELIMINATED),
            player(2, Role.UNDERCOVER),
            player(3, Role.UNDERCOVER, PlayerStatus.ELIMINATED),
            player(4, Role.MR_WHITE)
        )
        val scores = engine.computeScores(players, Victory.Combined)
        assertEquals(mapOf(2 to 10, 4 to 6), scores)
    }

    @Test
    fun `scoring vide pour partie en cours`() {
        val players = listOf(
            player(1, Role.CIVIL),
            player(2, Role.UNDERCOVER)
        )
        assertEquals(emptyMap<Int, Int>(), engine.computeScores(players, Victory.Ongoing))
    }
}
