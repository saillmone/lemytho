package com.lemytho.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lemytho.app.data.model.WordPair

@Database(
    entities = [WordPair::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
}
