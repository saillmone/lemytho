package com.lemytho.app.engine

/**
 * Issue de la partie, déterminée après chaque élimination ou tentative de l'Inconnu.
 */
sealed class Victory {
    /** Partie en cours, personne n'a gagné. */
    data object Ongoing : Victory()

    /** Victoire des Citoyens : tous les Imposteurs et Inconnus sont éliminés. */
    data object Citizen : Victory()

    /** Victoire des Imposteurs : au moins l'un d'entre eux survit jusqu'à la fin. */
    data object Impostor : Victory()

    /**
     * Victoire de l'Inconnu.
     * @param winnerIds identifiants des Inconnus gagnants (+6 chacun).
     * @param byGuess true si gagnée en devinant le mot lors de son élimination,
     *                false si gagnée par survie (duel final ou dernière présence).
     */
    data class Unknown(val winnerIds: Set<Int>, val byGuess: Boolean) : Victory()

    /** Victoire partagée : les Citoyens sont éliminés, Imposteurs et Inconnus restent en jeu. */
    data object Combined : Victory()
}

// Constantes de scoring (cahier des charges, règles de victoire).
const val CITIZEN_WIN_POINTS = 2
const val IMPOSTOR_WIN_POINTS = 10
const val UNKNOWN_WIN_POINTS = 6
