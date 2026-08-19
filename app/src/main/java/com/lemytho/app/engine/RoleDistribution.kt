package com.lemytho.app.engine

/**
 * Résultat de la répartition des rôles pour un nombre de joueurs donné.
 *
 * Règle métier (cahier des charges §5) :
 * - citoyens = arrondi supérieur(N / 2)
 * - Inconnu = 1 (3-10 joueurs), 2 (11-16), 3 (17-20)
 * - imposteurs = reste (jamais négatif)
 */
data class RoleDistribution(
    val citizenCount: Int,
    val impostorCount: Int,
    val unknownCount: Int
) {
    val total: Int get() = citizenCount + impostorCount + unknownCount
}
