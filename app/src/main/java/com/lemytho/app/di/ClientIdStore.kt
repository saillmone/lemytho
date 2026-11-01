package com.lemytho.app.di

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * Identifiant stable de l'appareil : la chaise, pas le fil Socket.IO.
 * Persisté pour reprendre le même siège après un écran éteint.
 */
class ClientIdStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getOrCreate(): String {
        val existing = prefs.getString(KEY_CLIENT_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_CLIENT_ID, created).apply()
        return created
    }

    private companion object {
        const val PREFS_NAME = "lemytho_prefs"
        const val KEY_CLIENT_ID = "client_id"
    }
}
