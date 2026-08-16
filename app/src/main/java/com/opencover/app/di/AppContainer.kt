package com.opencover.app.di

import android.content.Context
import androidx.room.Room
import com.opencover.app.data.local.AppDatabase
import com.opencover.app.data.local.WordRepository
import com.opencover.app.data.local.WordRepositoryImpl
import com.opencover.app.engine.GameEngine
import com.opencover.app.net.ConnectionManager

/**
 * Conteneur d'injection de dépendances manuel.
 * Pas de Hilt : minimalisme + conformité F-Droid (aucune dépendance propriétaire).
 */
class AppContainer(context: Context) {

    private val database: AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "opencover.db"
    ).build()

    val wordRepository: WordRepository = WordRepositoryImpl(
        wordDao = database.wordDao(),
        context = context.applicationContext
    )

    val gameEngine: GameEngine = GameEngine()

    val connectionManager: ConnectionManager = ConnectionManager()
}
