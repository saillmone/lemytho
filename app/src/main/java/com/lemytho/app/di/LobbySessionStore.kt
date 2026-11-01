package com.lemytho.app.di

import android.content.Context
import android.content.SharedPreferences

/** Salon actif à reprendre après une coupure (écran, process encore vivant). */
data class LobbySession(
    val serverUrl: String,
    val code: String,
    val pseudo: String,
    val isHost: Boolean
)

class LobbySessionStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): LobbySession? {
        val code = prefs.getString(KEY_CODE, null)?.takeIf { it.isNotBlank() } ?: return null
        val serverUrl = prefs.getString(KEY_SERVER, null)?.takeIf { it.isNotBlank() } ?: return null
        val pseudo = prefs.getString(KEY_PSEUDO, null)?.takeIf { it.isNotBlank() } ?: return null
        return LobbySession(
            serverUrl = serverUrl,
            code = code,
            pseudo = pseudo,
            isHost = prefs.getBoolean(KEY_HOST, false)
        )
    }

    fun save(session: LobbySession) {
        prefs.edit()
            .putString(KEY_SERVER, session.serverUrl)
            .putString(KEY_CODE, session.code)
            .putString(KEY_PSEUDO, session.pseudo)
            .putBoolean(KEY_HOST, session.isHost)
            .apply()
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_SERVER)
            .remove(KEY_CODE)
            .remove(KEY_PSEUDO)
            .remove(KEY_HOST)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "lemytho_prefs"
        const val KEY_SERVER = "session_server_url"
        const val KEY_CODE = "session_lobby_code"
        const val KEY_PSEUDO = "session_pseudo"
        const val KEY_HOST = "session_is_host"
    }
}
