package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DownloadTaskEntity::class,
        BrowserHistoryEntity::class,
        BookmarkEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MeowDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun browserDao(): BrowserDao

    companion object {
        @Volatile
        private var INSTANCE: MeowDatabase? = null

        fun getDatabase(context: Context): MeowDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MeowDatabase::class.java,
                    "meowsave.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
