package com.lemytho.app.di

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistance légère de l'URL du serveur multijoueur (SharedPreferences).
 * Permet de conserver la dernière adresse saisie par l'utilisateur, avec un
 * retour possible à la valeur par défaut (BuildConfig.SERVER_URL).
 */
class ServerStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Dernière URL mémorisée, ou null si jamais personnalisée. */
    fun load(): String? = prefs.getString(KEY_SERVER_URL, null)?.takeIf { it.isNotBlank() }

    fun save(url: String) {
        if (url.isBlank()) return
        prefs.edit().putString(KEY_SERVER_URL, url).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_SERVER_URL).apply()
    }

    private companion object {
        const val PREFS_NAME = "lemytho_prefs"
        const val KEY_SERVER_URL = "server_url"
    }
}
