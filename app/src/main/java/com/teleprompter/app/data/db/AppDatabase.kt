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
         *
         * Migration strategy for future versions:
         * - Always create migration paths to preserve user data
         * - Never use fallbackToDestructiveMigration() for production
         * - Test migrations thoroughly before release
         *
         * Example migration from version 1 to 2:
         * val MIGRATION_1_2 = object : Migration(1, 2) {
         *     override fun migrate(database: SupportSQLiteDatabase) {
         *         // Add new column with default value
         *         database.execSQL("ALTER TABLE scripts ADD COLUMN new_field TEXT DEFAULT ''")
         *     }
         * }
         *
         * Example migration from version 2 to 3:
         * val MIGRATION_2_3 = object : Migration(2, 3) {
         *     override fun migrate(database: SupportSQLiteDatabase) {
         *         // Create new table
         *         database.execSQL("CREATE TABLE IF NOT EXISTS new_table (id INTEGER PRIMARY KEY, data TEXT)")
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
                    // Migration strategy:
                    // Add all migration paths here when schema changes occur
                    // .addMigrations(MIGRATION_1_2, MIGRATION_2_3, ...)
                    //
                    // Current version is 1, no migrations needed yet
                    // When updating to version 2+, create appropriate Migration objects
                    // and add them via .addMigrations()
                    //
                    // IMPORTANT: Only use destructive migration on downgrade (rollback scenarios)
                    // NEVER on upgrade to preserve user data
                    .fallbackToDestructiveMigrationOnDowngrade() // Only destroy on downgrade, not upgrade
                    .setJournalMode(RoomDatabase.JournalMode.TRUNCATE) // Better for some devices
                    .build()
                INSTANCE = instance
                Log.d("AppDatabase", "Database instance created with migration strategy")
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
