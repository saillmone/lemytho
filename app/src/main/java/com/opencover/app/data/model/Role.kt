package com.opencover.app.data.model

/** Rôles possibles dans une partie d'OpenCover. */
enum class Role {
    /** Reçoit le mot civil et doit identifier l'intrus. */
    CIVIL,

    /** L'intrus : reçoit un mot proche mais différent. */
    UNDERCOVER,

    /** Ne reçoit aucun mot, doit deviner le mot civil. */
    MR_WHITE
}
