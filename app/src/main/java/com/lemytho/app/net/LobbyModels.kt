package com.lemytho.app.net

import org.json.JSONArray

/** État de la connexion au serveur relais. */
sealed class ConnectionStatus {
    data object Disconnected : ConnectionStatus()
    data object Connecting : ConnectionStatus()
    data object Connected : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()
}

/** Membre d'un salon (représentation publique, sans information secrète). */
data class LobbyMember(
    val playerId: Int,
    val pseudo: String,
    val isHost: Boolean,
    val ready: Boolean = false
)

/** Événement reçu du serveur, exposé aux ViewModels via un Flow. */
sealed class ServerEvent {
    /** La liste des joueurs du salon a changé. */
    data class LobbyUpdate(val members: List<LobbyMember>) : ServerEvent()

    /** Le salon a été fermé (déconnexion de l'hôte). */
    data object LobbyClosed : ServerEvent()

    /** Événement de jeu relayé par le serveur (nom + payload JSON). */
    data class GameEvent(val name: String, val data: org.json.JSONObject) : ServerEvent()
}

/** Résultat d'une création/adhésion de salon. */
sealed class LobbyResult {
    data class Success(
        val code: String?,
        val playerId: Int,
        val members: List<LobbyMember>
    ) : LobbyResult()

    data class Failure(val error: String, val message: String) : LobbyResult()
}

/** Parse une liste de membres depuis un tableau JSON. */
fun parseMembers(array: JSONArray?): List<LobbyMember> {
    if (array == null) return emptyList()
    return buildList {
        for (i in 0 until array.length()) {
            val m = array.optJSONObject(i) ?: continue
            add(
                LobbyMember(
                    playerId = m.optInt("playerId"),
                    pseudo = m.optString("pseudo"),
                    isHost = m.optBoolean("isHost"),
                    ready = m.optBoolean("ready")
                )
            )
        }
    }
}
