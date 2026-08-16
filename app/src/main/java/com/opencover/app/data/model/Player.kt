package com.opencover.app.data.model

/**
 * Un joueur de la partie.
 *
 * Vit uniquement en mémoire (état de partie), jamais persisté en Room :
 * le mot assigné est secret et ne doit pas être écrit sur disque.
 *
 * @property id identifiant séquentiel attribué à la création de la partie.
 * @property pseudo nom d'affichage choisi par le joueur.
 * @property role rôle attribué par le GameEngine.
 * @property assignedWord mot secret ; chaîne vide ("") pour Mr White, qui n'a pas de mot.
 * @property status état actuel (actif ou éliminé).
 * @property score points accumulés au fil de la partie.
 */
data class Player(
    val id: Int,
    val pseudo: String,
    val role: Role,
    val assignedWord: String,
    val status: PlayerStatus = PlayerStatus.ACTIVE,
    val score: Int = 0
)
