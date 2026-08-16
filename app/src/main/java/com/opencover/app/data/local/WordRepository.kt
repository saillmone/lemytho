package com.opencover.app.data.local

import com.opencover.app.data.model.WordPair
import kotlinx.coroutines.flow.Flow

/** Accès aux paires de mots stockées localement. */
interface WordRepository {

    /** Toutes les paires, triées par id. */
    fun getWords(): Flow<List<WordPair>>

    /** Les paires d'une catégorie donnée. */
    fun getByCategory(category: String): Flow<List<WordPair>>

    /** Une paire aléatoire, éventuellement restreinte à [category]. */
    suspend fun getRandomPair(category: String? = null): WordPair?

    /** Les catégories distinctes présentes en base. */
    fun getCategories(): Flow<List<String>>

    /** Re-seed la base si la version du seed embarqué est plus récente que la base. */
    suspend fun seedIfNeeded()
}
