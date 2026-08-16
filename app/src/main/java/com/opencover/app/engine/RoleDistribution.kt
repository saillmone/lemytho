package com.opencover.app.engine

/**
 * Résultat de la répartition des rôles pour un nombre de joueurs donné.
 *
 * Règle métier (cahier des charges §5) :
 * - civils = arrondi supérieur(N / 2)
 * - Mr White = 1 (3-10 joueurs), 2 (11-16), 3 (17-20)
 * - infiltrés = reste (jamais négatif)
 */
data class RoleDistribution(
    val civilCount: Int,
    val undercoverCount: Int,
    val mrWhiteCount: Int
) {
    val total: Int get() = civilCount + undercoverCount + mrWhiteCount
}
