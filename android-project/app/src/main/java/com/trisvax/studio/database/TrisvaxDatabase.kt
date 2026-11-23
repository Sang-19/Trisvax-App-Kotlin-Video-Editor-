package com.trisvax.studio.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProjectEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TrisvaxDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    companion object {
        @Volatile
        private var INSTANCE: TrisvaxDatabase? = null

        fun getInstance(context: Context): TrisvaxDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TrisvaxDatabase::class.java,
                    "trisvax_studio_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
