package com.teleprompter.app.data.db

import androidx.room.*
import com.teleprompter.app.data.models.Script
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Script entity operations
 */
@Dao
interface ScriptDao {

    /**
     * Get all scripts ordered by most recently updated
     */
    @Query("SELECT * FROM scripts ORDER BY updatedAt DESC")
    fun getAllScripts(): Flow<List<Script>>

    /**
     * Get script by ID
     */
    @Query("SELECT * FROM scripts WHERE id = :scriptId")
    suspend fun getScriptById(scriptId: Long): Script?

    /**
     * Insert new script
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScript(script: Script): Long

    /**
     * Update existing script
     */
    @Update
    suspend fun updateScript(script: Script)

    /**
     * Delete script
     */
    @Delete
    suspend fun deleteScript(script: Script)

    /**
     * Delete script by ID
     */
    @Query("DELETE FROM scripts WHERE id = :scriptId")
    suspend fun deleteScriptById(scriptId: Long)

    /**
     * Search scripts by title or content
     * Note: Query should already include wildcards (e.g., "%search%")
     */
    @Query("SELECT * FROM scripts WHERE title LIKE :query OR content LIKE :query ORDER BY updatedAt DESC")
    fun searchScripts(query: String): Flow<List<Script>>

    /**
     * Get count of all scripts
     */
    @Query("SELECT COUNT(*) FROM scripts")
    suspend fun getScriptsCount(): Int
}
