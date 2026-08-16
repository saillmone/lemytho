package com.opencover.app.data.model

/** Statut d'un joueur au cours de la partie. */
enum class PlayerStatus {
    /** Toujours en jeu. */
    ACTIVE,

    /** Éliminé : son rôle exact a été révélé publiquement. */
    ELIMINATED
}
