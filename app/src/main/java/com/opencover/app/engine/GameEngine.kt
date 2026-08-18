package com.opencover.app.engine

import com.opencover.app.data.model.Player
import com.opencover.app.data.model.PlayerStatus
import com.opencover.app.data.model.Role
import com.opencover.app.data.model.WordPair
import kotlin.random.Random

/**
 * Résultat du dépouillement d'un tour de vote.
 *
 * - [Eliminated] : un unique joueur a le plus de votes, il est éliminé.
 * - [Tie] : plusieurs joueurs sont à égalité, un second tour est nécessaire.
 */
sealed class VoteOutcome {
    data class Eliminated(val targetId: Int) : VoteOutcome()
    data class Tie(val candidateIds: Set<Int>) : VoteOutcome()
}

/**
 * Résultat d'une élimination : le rôle exact est révélé publiquement (cahier des charges §5).
 */
data class EliminationResult(
    val playerId: Int,
    val pseudo: String,
    val revealedRole: Role
)

/**
 * Logique métier pure du jeu. Aucune dépendance Android, aucun état interne :
 * chaque méthode transforme des données en entrée et renvoie un résultat.
 * L'état de la partie vit dans le ViewModel (StateFlow), pas ici.
 *
 * L'aléatoire est injecté pour rendre les tests reproductibles (graine fixe).
 */
class GameEngine(private val random: Random = Random.Default) {

    /**
     * Répartit les rôles pour [playerCount] joueurs (3 à 20).
     * @throws IllegalArgumentException si le nombre est hors bornes.
     */
    fun computeRoleDistribution(
        playerCount: Int,
        threePlayerIsMrWhite: Boolean = true
    ): RoleDistribution {
        require(playerCount in MIN_PLAYERS..MAX_PLAYERS) {
            "Le nombre de joueurs doit être entre $MIN_PLAYERS et $MAX_PLAYERS (reçu : $playerCount)"
        }
        if (playerCount == 3) {
            return if (threePlayerIsMrWhite) {
                RoleDistribution(civilCount = 2, undercoverCount = 0, mrWhiteCount = 1)
            } else {
                RoleDistribution(civilCount = 2, undercoverCount = 1, mrWhiteCount = 0)
            }
        }
        val civilCount = (playerCount + 1) / 2 // arrondi supérieur de N/2
        val mrWhiteCount = when (playerCount) {
            in 3..10 -> 1
            in 11..16 -> 2
            else -> 3
        }
        val undercoverCount = playerCount - civilCount - mrWhiteCount
        return RoleDistribution(civilCount, undercoverCount, mrWhiteCount)
    }

    /** Génère la liste des rôles répartis puis mélangés aléatoirement. */
    fun assignRoles(playerCount: Int, threePlayerIsMrWhite: Boolean = true): List<Role> {
        val distribution = computeRoleDistribution(playerCount, threePlayerIsMrWhite)
        return buildList {
            repeat(distribution.civilCount) { add(Role.CIVIL) }
            repeat(distribution.undercoverCount) { add(Role.UNDERCOVER) }
            repeat(distribution.mrWhiteCount) { add(Role.MR_WHITE) }
        }.shuffled(random)
    }

    /**
     * Attribue à chaque joueur son mot secret selon son rôle.
     * Mr White ne reçoit aucun mot (chaîne vide).
     */
    fun assignWords(players: List<Player>, wordPair: WordPair): List<Player> =
        players.map { player ->
            val word = when (player.role) {
                Role.CIVIL -> wordPair.civilWord
                Role.UNDERCOVER -> wordPair.undercoverWord
                Role.MR_WHITE -> ""
            }
            player.copy(assignedWord = word)
        }

    /**
     * Élimine un joueur et révèle publiquement son rôle exact.
     * @throws IllegalArgumentException si l'id est inconnu.
     */
    fun eliminate(players: List<Player>, targetId: Int): EliminationResult {
        val target = players.firstOrNull { it.id == targetId }
            ?: throw IllegalArgumentException("Aucun joueur avec l'id $targetId")
        return EliminationResult(
            playerId = target.id,
            pseudo = target.pseudo,
            revealedRole = target.role
        )
    }

    /** Compte les votes reçus par chaque cible. */
    fun tallyVotes(votes: Map<Int, Int>): Map<Int, Int> =
        votes.values.groupingBy { it }.eachCount()

    /**
     * Dépouille un tour de vote restreint aux [candidates].
     *
     * Au second tour, seuls les ex æquo sont candidats : tout vote visant
     * un joueur hors de [candidates] est ignoré.
     *
     * @throws IllegalArgumentException si aucun vote valide n'est émis.
     */
    fun resolveVote(votes: Map<Int, Int>, candidates: Set<Int>): VoteOutcome {
        require(candidates.isNotEmpty()) { "Au moins un candidat est requis" }
        val validVotes = votes.values.filter { it in candidates }
        require(validVotes.isNotEmpty()) { "Aucun vote valide pour les candidats donnés" }
        val tally = validVotes.groupingBy { it }.eachCount()
        val maxCount = tally.values.maxOrNull() ?: 0
        val top = tally.filterValues { it == maxCount }.keys
        return if (top.size == 1) {
            VoteOutcome.Eliminated(top.first())
        } else {
            VoteOutcome.Tie(top.toSet())
        }
    }

    /**
     * Tire un candidat au hasard parmi [candidates].
     * Utilisé quand l'égalité persiste après le second tour.
     */
    fun randomElimination(candidates: Set<Int>): Int {
        require(candidates.isNotEmpty()) { "Au moins un candidat est requis" }
        return candidates.elementAt(random.nextInt(candidates.size))
    }

    /**
     * Construit l'ordre des tours d'indice pour [playerIds].
     * Le premier joueur est tiré au hasard, puis on suit l'ordre croissant des ids
     * (ordre d'inscription) en bouclant au début une fois la fin atteinte.
     *
     * Exemple : ids [1, 2, 3, 4, 5], départ aléatoire sur 3 → [3, 4, 5, 1, 2].
     */
    fun clueOrder(playerIds: List<Int>): List<Int> {
        require(playerIds.isNotEmpty()) { "Au moins un joueur est requis" }
        val sorted = playerIds.sorted()
        val startIndex = random.nextInt(sorted.size)
        return sorted.drop(startIndex) + sorted.take(startIndex)
    }

    /**
     * Détermine l'issue de la partie à partir de l'état actuel des joueurs.
     * Modèle consolidé (jeu de cartes Undercover) :
     * - 0 Infiltré et 0 Mr White → victoire des Civils.
     * - 0 Civil + Infiltrés + Mr White → victoire partagée.
     * - 0 Civil + uniquement des Infiltrés → victoire des Infiltrés.
     * - 0 Civil + uniquement des Mr White → victoire de Mr White (survie).
     * - 1 Civil + 1 Infiltré (duel) → victoire des Infiltrés.
     * - 1 Civil + 1 Mr White (duel) → victoire de Mr White (survie).
     * - Sinon → partie en cours.
     */
    fun determineWinner(players: List<Player>): Victory {
        val living = players.filter { it.status == PlayerStatus.ACTIVE }
        val civils = living.filter { it.role == Role.CIVIL }
        val undercovers = living.filter { it.role == Role.UNDERCOVER }
        val mrWhites = living.filter { it.role == Role.MR_WHITE }
        return when {
            undercovers.isEmpty() && mrWhites.isEmpty() -> Victory.Civil
            civils.isEmpty() && undercovers.isNotEmpty() && mrWhites.isNotEmpty() -> Victory.Combined
            civils.isEmpty() && undercovers.isNotEmpty() -> Victory.Undercover
            civils.isEmpty() && mrWhites.isNotEmpty() ->
                Victory.MrWhite(mrWhites.map { it.id }.toSet(), byGuess = false)
            civils.size == 1 && undercovers.size == 1 && mrWhites.isEmpty() -> Victory.Undercover
            civils.size == 1 && undercovers.isEmpty() && mrWhites.size == 1 ->
                Victory.MrWhite(setOf(mrWhites.first().id), byGuess = false)
            else -> Victory.Ongoing
        }
    }

    /**
     * Calcule les points gagnés par chaque joueur selon la victoire.
     * @return map playerId -> points gagnés (les joueurs à 0 point sont absents).
     */
    fun computeScores(players: List<Player>, victory: Victory): Map<Int, Int> {
        val living = players.filter { it.status == PlayerStatus.ACTIVE }
        return when (victory) {
            Victory.Ongoing -> emptyMap()
            Victory.Civil -> living
                .filter { it.role == Role.CIVIL }
                .associate { it.id to CIVIL_WIN_POINTS }
            Victory.Undercover -> living
                .filter { it.role == Role.UNDERCOVER }
                .associate { it.id to UNDERCOVER_WIN_POINTS }
            is Victory.MrWhite -> victory.winnerIds.associateWith { MR_WHITE_WIN_POINTS }
            Victory.Combined -> living
                .filter { it.role == Role.UNDERCOVER || it.role == Role.MR_WHITE }
                .associate { it.id to
                    if (it.role == Role.UNDERCOVER) UNDERCOVER_WIN_POINTS else MR_WHITE_WIN_POINTS }
        }
    }

    private companion object {
        const val MIN_PLAYERS = 3
        const val MAX_PLAYERS = 20
    }
}

/**
 * Libellé lisible de la répartition des rôles pour l'affichage (setup et lobby).
 * Réutilise [GameEngine.computeRoleDistribution] : source de vérité unique,
 * aucun doublon de la formule métier.
 */
fun roleDistributionLabel(playerCount: Int, threePlayerIsMrWhite: Boolean = true): String {
    val d = GameEngine().computeRoleDistribution(playerCount, threePlayerIsMrWhite)
    return buildList {
        add(if (d.civilCount <= 1) "${d.civilCount} Civil" else "${d.civilCount} Civils")
        if (d.undercoverCount > 0) {
            add(if (d.undercoverCount <= 1) "${d.undercoverCount} Infiltré" else "${d.undercoverCount} Infiltrés")
        }
        if (d.mrWhiteCount > 0) add("${d.mrWhiteCount} Mr White")
    }.joinToString(" · ")
}
