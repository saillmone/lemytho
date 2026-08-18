package com.opencover.app.di

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistance légère du pseudo multijoueur (SharedPreferences).
 * Permet de pré-remplir le champ au prochain lancement, sans dépendance lourde.
 */
class PseudoStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): String = prefs.getString(KEY_PSEUDO, "") ?: ""

    fun save(pseudo: String) {
        if (pseudo.isBlank()) return
        prefs.edit().putString(KEY_PSEUDO, pseudo).apply()
    }

    private companion object {
        const val PREFS_NAME = "opencover_prefs"
        const val KEY_PSEUDO = "last_pseudo"
    }
}
