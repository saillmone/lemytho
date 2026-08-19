package com.lemytho.app.di

import android.content.Context
import androidx.room.Room
import com.lemytho.app.data.local.AppDatabase
import com.lemytho.app.data.local.WordRepository
import com.lemytho.app.data.local.WordRepositoryImpl
import com.lemytho.app.engine.GameEngine
import com.lemytho.app.net.ConnectionManager

/**
 * Conteneur d'injection de dépendances manuel.
 * Pas de Hilt : minimalisme + conformité F-Droid (aucune dépendance propriétaire).
 */
class AppContainer(context: Context) {

    private val database: AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "lemytho.db"
    )
        // Les paires de mots sont re-seedées depuis les assets à chaque lancement :
        // une migration destructive est acceptable tant que l'app n'est pas publiée.
        .fallbackToDestructiveMigration()
        .build()

    val wordRepository: WordRepository = WordRepositoryImpl(
        wordDao = database.wordDao(),
        context = context.applicationContext
    )

    val gameEngine: GameEngine = GameEngine()

    val connectionManager: ConnectionManager = ConnectionManager()

    val pseudoStore: PseudoStore = PseudoStore(context)

    val serverStore: ServerStore = ServerStore(context)
}
