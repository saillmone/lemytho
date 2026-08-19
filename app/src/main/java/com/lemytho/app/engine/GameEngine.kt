package com.lemytho.app.engine

import com.lemytho.app.data.model.Player
import com.lemytho.app.data.model.PlayerStatus
import com.lemytho.app.data.model.Role
import com.lemytho.app.data.model.WordPair
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
        threePlayerIsUnknown: Boolean = true
    ): RoleDistribution {
        require(playerCount in MIN_PLAYERS..MAX_PLAYERS) {
            "Le nombre de joueurs doit être entre $MIN_PLAYERS et $MAX_PLAYERS (reçu : $playerCount)"
        }
        if (playerCount == 3) {
            return if (threePlayerIsUnknown) {
                RoleDistribution(citizenCount = 2, impostorCount = 0, unknownCount = 1)
            } else {
                RoleDistribution(citizenCount = 2, impostorCount = 1, unknownCount = 0)
            }
        }
        val citizenCount = (playerCount + 1) / 2 // arrondi supérieur de N/2
        val unknownCount = when (playerCount) {
            in 3..10 -> 1
            in 11..16 -> 2
            else -> 3
        }
        val impostorCount = playerCount - citizenCount - unknownCount
        return RoleDistribution(citizenCount, impostorCount, unknownCount)
    }

    /** Génère la liste des rôles répartis puis mélangés aléatoirement. */
    fun assignRoles(playerCount: Int, threePlayerIsUnknown: Boolean = true): List<Role> {
        val distribution = computeRoleDistribution(playerCount, threePlayerIsUnknown)
        return buildList {
            repeat(distribution.citizenCount) { add(Role.CITIZEN) }
            repeat(distribution.impostorCount) { add(Role.IMPOSTOR) }
            repeat(distribution.unknownCount) { add(Role.UNKNOWN) }
        }.shuffled(random)
    }

    /**
     * Attribue à chaque joueur son mot secret selon son rôle.
     * L'Inconnu ne reçoit aucun mot (chaîne vide).
     */
    fun assignWords(players: List<Player>, wordPair: WordPair): List<Player> =
        players.map { player ->
            val word = when (player.role) {
                Role.CITIZEN -> wordPair.citizenWord
                Role.IMPOSTOR -> wordPair.impostorWord
                Role.UNKNOWN -> ""
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
     * Modèle consolidé :
     * - 0 Imposteur et 0 Inconnu → victoire des Citoyens.
     * - 0 Citoyen + Imposteurs + Inconnus → victoire partagée.
     * - 0 Citoyen + uniquement des Imposteurs → victoire des Imposteurs.
     * - 0 Citoyen + uniquement des Inconnus → victoire de l'Inconnu (survie).
     * - 1 Citoyen + 1 Imposteur (duel) → victoire des Imposteurs.
     * - 1 Citoyen + 1 Inconnu (duel) → victoire de l'Inconnu (survie).
     * - Sinon → partie en cours.
     */
    fun determineWinner(players: List<Player>): Victory {
        val living = players.filter { it.status == PlayerStatus.ACTIVE }
        val citizens = living.filter { it.role == Role.CITIZEN }
        val impostors = living.filter { it.role == Role.IMPOSTOR }
        val unknowns = living.filter { it.role == Role.UNKNOWN }
        return when {
            impostors.isEmpty() && unknowns.isEmpty() -> Victory.Citizen
            citizens.isEmpty() && impostors.isNotEmpty() && unknowns.isNotEmpty() -> Victory.Combined
            citizens.isEmpty() && impostors.isNotEmpty() -> Victory.Impostor
            citizens.isEmpty() && unknowns.isNotEmpty() ->
                Victory.Unknown(unknowns.map { it.id }.toSet(), byGuess = false)
            citizens.size == 1 && impostors.size == 1 && unknowns.isEmpty() -> Victory.Impostor
            citizens.size == 1 && impostors.isEmpty() && unknowns.size == 1 ->
                Victory.Unknown(setOf(unknowns.first().id), byGuess = false)
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
            Victory.Citizen -> living
                .filter { it.role == Role.CITIZEN }
                .associate { it.id to CITIZEN_WIN_POINTS }
            Victory.Impostor -> living
                .filter { it.role == Role.IMPOSTOR }
                .associate { it.id to IMPOSTOR_WIN_POINTS }
            is Victory.Unknown -> victory.winnerIds.associateWith { UNKNOWN_WIN_POINTS }
            Victory.Combined -> living
                .filter { it.role == Role.IMPOSTOR || it.role == Role.UNKNOWN }
                .associate { it.id to
                    if (it.role == Role.IMPOSTOR) IMPOSTOR_WIN_POINTS else UNKNOWN_WIN_POINTS }
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
fun roleDistributionLabel(playerCount: Int, threePlayerIsUnknown: Boolean = true): String {
    val d = GameEngine().computeRoleDistribution(playerCount, threePlayerIsUnknown)
    return buildList {
        add(if (d.citizenCount <= 1) "${d.citizenCount} Citoyen" else "${d.citizenCount} Citoyens")
        if (d.impostorCount > 0) {
            add(if (d.impostorCount <= 1) "${d.impostorCount} Imposteur" else "${d.impostorCount} Imposteurs")
        }
        if (d.unknownCount > 0) {
            add(if (d.unknownCount <= 1) "${d.unknownCount} Inconnu" else "${d.unknownCount} Inconnus")
        }
    }.joinToString(" · ")
}
