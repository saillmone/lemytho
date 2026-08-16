package com.opencover.app.engine

import com.opencover.app.data.model.Player
import com.opencover.app.data.model.Role
import com.opencover.app.data.model.WordPair
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {

    private val engine = GameEngine(random = Random(42))

    // --- computeRoleDistribution : la formule de répartition ---

    @Test
    fun `le total des roles egale le nombre de joueurs pour toutes les tailles valides`() {
        for (n in 3..20) {
            val distribution = engine.computeRoleDistribution(n)
            assertEquals("total incorrect pour n=$n", n, distribution.total)
        }
    }

    @Test
    fun `les civils valent toujours l arrondi superieur de la moitie`() {
        for (n in 3..20) {
            val distribution = engine.computeRoleDistribution(n)
            assertEquals("civils incorrects pour n=$n", (n + 1) / 2, distribution.civilCount)
        }
    }

    @Test
    fun `le nombre de mr white depend de la tranche de joueurs`() {
        for (n in 3..10) assertEquals(1, engine.computeRoleDistribution(n).mrWhiteCount)
        for (n in 11..16) assertEquals(2, engine.computeRoleDistribution(n).mrWhiteCount)
        for (n in 17..20) assertEquals(3, engine.computeRoleDistribution(n).mrWhiteCount)
    }

    @Test
    fun `le nombre d infiltres n est jamais negatif`() {
        for (n in 3..20) {
            assertTrue(engine.computeRoleDistribution(n).undercoverCount >= 0)
        }
    }

    @Test
    fun `la repartition rejette les tailles hors bornes`() {
        assertThrows(IllegalArgumentException::class.java) { engine.computeRoleDistribution(2) }
        assertThrows(IllegalArgumentException::class.java) { engine.computeRoleDistribution(21) }
    }

    @Test
    fun `trois joueurs forcent un mr white par defaut`() {
        val distribution = engine.computeRoleDistribution(3)
        assertEquals(RoleDistribution(civilCount = 2, undercoverCount = 0, mrWhiteCount = 1), distribution)
    }

    @Test
    fun `trois joueurs peuvent forcer un infiltre a la place de mr white`() {
        val distribution = engine.computeRoleDistribution(3, threePlayerIsMrWhite = false)
        assertEquals(RoleDistribution(civilCount = 2, undercoverCount = 1, mrWhiteCount = 0), distribution)
    }

    @Test
    fun `assignRoles a trois joueurs respecte le choix du troisieme joueur`() {
        val rolesMrWhite = engine.assignRoles(3, threePlayerIsMrWhite = true)
        assertEquals(1, rolesMrWhite.count { it == Role.MR_WHITE })
        assertEquals(0, rolesMrWhite.count { it == Role.UNDERCOVER })

        val rolesUndercover = engine.assignRoles(3, threePlayerIsMrWhite = false)
        assertEquals(0, rolesUndercover.count { it == Role.MR_WHITE })
        assertEquals(1, rolesUndercover.count { it == Role.UNDERCOVER })
    }

    @Test
    fun `repartitions de reference connues`() {
        assertEquals(RoleDistribution(5, 4, 1), engine.computeRoleDistribution(10))
        assertEquals(RoleDistribution(6, 3, 2), engine.computeRoleDistribution(11))
        assertEquals(RoleDistribution(9, 5, 3), engine.computeRoleDistribution(17))
        assertEquals(RoleDistribution(10, 7, 3), engine.computeRoleDistribution(20))
    }

    // --- assignRoles : mélange des rôles ---

    @Test
    fun `la repartition des roles correspond a la distribution pour chaque taille`() {
        for (n in 3..20) {
            val distribution = engine.computeRoleDistribution(n)
            val roles = engine.assignRoles(n)
            assertEquals(n, roles.size)
            assertEquals(distribution.civilCount, roles.count { it == Role.CIVIL })
            assertEquals(distribution.undercoverCount, roles.count { it == Role.UNDERCOVER })
            assertEquals(distribution.mrWhiteCount, roles.count { it == Role.MR_WHITE })
        }
    }

    // --- assignWords : attribution des mots ---

    @Test
    fun `chaque role recoit le bon mot`() {
        val wordPair = WordPair(id = 1, category = "Animaux", civilWord = "Chat", undercoverWord = "Chien", version = 1)
        val players = listOf(
            Player(id = 1, pseudo = "A", role = Role.CIVIL, assignedWord = ""),
            Player(id = 2, pseudo = "B", role = Role.UNDERCOVER, assignedWord = ""),
            Player(id = 3, pseudo = "C", role = Role.MR_WHITE, assignedWord = "")
        )
        val result = engine.assignWords(players, wordPair)
        assertEquals("Chat", result.first { it.id == 1 }.assignedWord)
        assertEquals("Chien", result.first { it.id == 2 }.assignedWord)
        assertEquals("", result.first { it.id == 3 }.assignedWord)
    }

    // --- eliminate : révélation du rôle ---

    @Test
    fun `l elimination revele le role exact du joueur`() {
        val players = listOf(
            Player(id = 1, pseudo = "A", role = Role.CIVIL, assignedWord = "Chat"),
            Player(id = 2, pseudo = "B", role = Role.UNDERCOVER, assignedWord = "Chien")
        )
        val result = engine.eliminate(players, targetId = 2)
        assertEquals(2, result.playerId)
        assertEquals(Role.UNDERCOVER, result.revealedRole)
    }

    @Test
    fun `eliminer un id inconnu leve une exception`() {
        val players = listOf(Player(id = 1, pseudo = "A", role = Role.CIVIL, assignedWord = "Chat"))
        assertThrows(IllegalArgumentException::class.java) { engine.eliminate(players, 99) }
    }

    // --- tallyVotes / resolveVote : dépouillement et égalités ---

    @Test
    fun `le depouillement compte les votes par cible`() {
        val votes = mapOf(1 to 2, 2 to 3, 3 to 2, 4 to 3)
        val tally = engine.tallyVotes(votes)
        assertEquals(2, tally[2])
        assertEquals(2, tally[3])
    }

    @Test
    fun `une majorite unique elimine le gagnant`() {
        val votes = mapOf(1 to 2, 2 to 3, 3 to 2, 4 to 2, 5 to 3)
        val outcome = engine.resolveVote(votes, candidates = setOf(2, 3))
        assertEquals(VoteOutcome.Eliminated(2), outcome)
    }

    @Test
    fun `une egalite renvoie les ex aequo pour un second tour`() {
        val votes = mapOf(1 to 2, 2 to 3, 3 to 2, 4 to 3)
        val outcome = engine.resolveVote(votes, candidates = setOf(2, 3))
        assertEquals(VoteOutcome.Tie(setOf(2, 3)), outcome)
    }

    @Test
    fun `les votes hors candidats sont ignores au second tour`() {
        // Seuls 2 et 3 restent candidats : le vote vers 4 est ignoré.
        val votes = mapOf(1 to 4, 2 to 3, 3 to 2, 4 to 3)
        val outcome = engine.resolveVote(votes, candidates = setOf(2, 3))
        assertEquals(VoteOutcome.Eliminated(3), outcome)
    }

    @Test
    fun `aucun vote valide leve une exception`() {
        val votes = mapOf(1 to 4)
        assertThrows(IllegalArgumentException::class.java) {
            engine.resolveVote(votes, candidates = setOf(2, 3))
        }
    }

    // --- randomElimination : tirage aléatoire en cas de persistance ---

    @Test
    fun `l elimination aleatoire renvoie toujours un candidat`() {
        val candidates = setOf(10, 20, 30)
        repeat(100) {
            assertTrue(engine.randomElimination(candidates) in candidates)
        }
    }

    @Test
    fun `l elimination aleatoire rejette un ensemble vide`() {
        assertThrows(IllegalArgumentException::class.java) {
            engine.randomElimination(emptySet())
        }
    }

    // --- clueOrder : ordre des tours d'indice ---

    @Test
    fun `l ordre des indices contient exactement tous les joueurs une fois`() {
        for (n in 3..20) {
            val order = engine.clueOrder((1..n).toList())
            assertEquals(n, order.size)
            assertEquals((1..n).toSet(), order.toSet())
        }
    }

    @Test
    fun `l ordre des indices suit l ordre d inscription en boucle apres le premier`() {
        // Graine fixe → départ déterministe. On vérifie la propriété structurale
        // indépendamment du point de départ : en partant de l'index du premier,
        // les suivants sont dans l'ordre croissant, en bouclant.
        val ids = listOf(1, 2, 3, 4, 5)
        repeat(20) {
            val order = engine.clueOrder(ids)
            val firstIndex = ids.indexOf(order.first())
            val expected = ids.drop(firstIndex) + ids.take(firstIndex)
            assertEquals("départ=${order.first()}", expected, order)
        }
    }

    @Test
    fun `l ordre des indices rejette une liste vide`() {
        assertThrows(IllegalArgumentException::class.java) {
            engine.clueOrder(emptyList())
        }
    }
}
