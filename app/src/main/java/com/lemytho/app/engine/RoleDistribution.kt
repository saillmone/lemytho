package com.lemytho.app.engine

/**
 * Résultat de la répartition des rôles pour un nombre de joueurs donné.
 *
 * Pour n ≥ 4 : intrus = (n+1)/3, inconnus = (intrus+1)/3,
 * imposteurs = intrus − inconnus, citoyens = n − intrus.
 * « Intrus » est un total de calcul (Imposteurs + Inconnus), pas un rôle.
 * À 3 joueurs, le toggle force 2 Citoyens + 1 Inconnu ou 1 Imposteur.
 */
data class RoleDistribution(
    val citizenCount: Int,
    val impostorCount: Int,
    val unknownCount: Int
) {
    val total: Int get() = citizenCount + impostorCount + unknownCount
}
