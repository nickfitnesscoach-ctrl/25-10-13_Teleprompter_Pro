package com.teleprompter.app.data.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.teleprompter.app.data.models.Script
import com.teleprompter.app.utils.Constants

/**
 * Room database for the application
 */
@Database(
    entities = [Script::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scriptDao(): ScriptDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Define database migrations here
         * Example:
         * val MIGRATION_1_2 = object : Migration(1, 2) {
         *     override fun migrate(database: SupportSQLiteDatabase) {
         *         database.execSQL("ALTER TABLE scripts ADD COLUMN new_field TEXT")
         *     }
         * }
         */

        /**
         * Get database instance (singleton pattern)
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    Constants.DATABASE_NAME
                )
                    // Add migrations here when schema changes occur
                    // .addMigrations(MIGRATION_1_2, MIGRATION_2_3, etc.)
                    // For now, we're on version 1, so no migrations needed yet
                    .fallbackToDestructiveMigrationOnDowngrade() // Only destroy on downgrade, not upgrade
                    .setJournalMode(RoomDatabase.JournalMode.TRUNCATE) // Better for some devices
                    .build()
                INSTANCE = instance
                Log.d("AppDatabase", "Database instance created")
                instance
            }
        }

        /**
         * Clear database instance (for testing purposes)
         */
        fun clearInstance() {
            INSTANCE = null
        }
    }
}
