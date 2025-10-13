package com.teleprompter.app.data.repository

import com.teleprompter.app.data.models.Script
import com.teleprompter.app.utils.Result
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing Script data
 */
interface ScriptRepository {
    /**
     * Get all scripts as a Flow
     */
    fun getAllScripts(): Flow<List<Script>>

    /**
     * Get a script by its ID
     */
    suspend fun getScriptById(id: Long): Result<Script>

    /**
     * Insert a new script
     * @return Result with the ID of the inserted script
     */
    suspend fun insertScript(script: Script): Result<Long>

    /**
     * Update an existing script
     */
    suspend fun updateScript(script: Script): Result<Unit>

    /**
     * Delete a script
     */
    suspend fun deleteScript(script: Script): Result<Unit>

    /**
     * Search scripts by title or content
     */
    fun searchScripts(query: String): Flow<List<Script>>
}
