package com.opencover.app.data.local

import android.content.Context
import com.opencover.app.data.model.WordPair
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

/**
 * Implémentation Room de [WordRepository].
 * [seedIfNeeded] compare la version maximale du seed embarqué à celle en base :
 * si la base est en retard, elle est vidée puis rechargée.
 */
class WordRepositoryImpl(
    private val wordDao: WordDao,
    private val context: Context
) : WordRepository {

    override fun getWords(): Flow<List<WordPair>> = wordDao.getAll()

    override fun getByCategory(category: String): Flow<List<WordPair>> =
        wordDao.getByCategory(category)

    override suspend fun getRandomPair(category: String?): WordPair? =
        if (category == null) wordDao.getRandomPair()
        else wordDao.getRandomPairByCategory(category)

    override fun getCategories(): Flow<List<String>> = wordDao.getCategories()

    override suspend fun seedIfNeeded() {
        val seed = readSeedFromAssets()
        val seedVersion = seed.maxOfOrNull { it.version } ?: return
        val dbVersion = wordDao.maxVersion() ?: 0
        if (dbVersion < seedVersion) {
            wordDao.deleteAll()
            wordDao.insertAll(seed)
        }
    }

    private fun readSeedFromAssets(): List<WordPair> {
        val json = context.assets.open(SEED_FILE).bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            WordPair(
                id = item.getInt("id"),
                category = item.getString("category"),
                civilWord = item.getString("civilWord"),
                undercoverWord = item.getString("undercoverWord"),
                version = item.getInt("version")
            )
        }
    }

    private companion object {
        const val SEED_FILE = "words_seed.json"
    }
}
