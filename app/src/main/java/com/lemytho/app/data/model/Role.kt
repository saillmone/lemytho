package com.lemytho.app.data.model

/** Rôles possibles dans une partie du Mytho. */
enum class Role {
    /** Reçoit le mot des Citoyens et doit identifier l'imposteur. */
    CITIZEN,

    /** L'imposteur : reçoit un mot proche mais différent. */
    IMPOSTOR,

    /** Ne reçoit aucun mot, doit deviner le mot des Citoyens. */
    UNKNOWN
}
