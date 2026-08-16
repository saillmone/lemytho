package com.opencover.app.engine

/**
 * Issue de la partie, déterminée après chaque élimination ou tentative de Mr White.
 */
sealed class Victory {
    /** Partie en cours, personne n'a gagné. */
    data object Ongoing : Victory()

    /** Victoire des Civils : tous les Infiltrés et Mr White sont éliminés. */
    data object Civil : Victory()

    /** Victoire des Infiltrés : au moins l'un d'entre eux survit jusqu'à la fin. */
    data object Undercover : Victory()

    /**
     * Victoire de Mr White.
     * @param winnerIds identifiants des Mr White gagnants (+6 chacun).
     * @param byGuess true si gagnée en devinant le mot lors de son élimination,
     *                false si gagnée par survie (duel final ou dernière présence).
     */
    data class MrWhite(val winnerIds: Set<Int>, val byGuess: Boolean) : Victory()

    /** Victoire partagée : les Civils sont éliminés, Infiltrés et Mr White restent en jeu. */
    data object Combined : Victory()
}

// Constantes de scoring (cahier des charges, règles de victoire).
const val CIVIL_WIN_POINTS = 2
const val UNDERCOVER_WIN_POINTS = 10
const val MR_WHITE_WIN_POINTS = 6
