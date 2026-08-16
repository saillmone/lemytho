package com.opencover.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.opencover.app.data.model.WordPair
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(wordPairs: List<WordPair>)

    @Query("SELECT * FROM word_pairs ORDER BY id ASC")
    fun getAll(): Flow<List<WordPair>>

    @Query("SELECT * FROM word_pairs WHERE category = :category ORDER BY id ASC")
    fun getByCategory(category: String): Flow<List<WordPair>>

    @Query("SELECT * FROM word_pairs ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomPair(): WordPair?

    @Query("SELECT * FROM word_pairs WHERE category = :category ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomPairByCategory(category: String): WordPair?

    @Query("SELECT DISTINCT category FROM word_pairs ORDER BY category ASC")
    fun getCategories(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM word_pairs")
    suspend fun count(): Int

    @Query("SELECT MAX(version) FROM word_pairs")
    suspend fun maxVersion(): Int?

    @Query("DELETE FROM word_pairs")
    suspend fun deleteAll()
}
