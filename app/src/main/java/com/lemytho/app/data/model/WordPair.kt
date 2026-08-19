package com.lemytho.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Une paire de mots pour une manche.
 * Persistée en base locale (Room), seedée depuis assets/words_seed.json.
 */
@Entity(tableName = "word_pairs")
data class WordPair(
    @PrimaryKey val id: Int,
    val category: String,
    val citizenWord: String,
    val impostorWord: String,
    val version: Int
)
