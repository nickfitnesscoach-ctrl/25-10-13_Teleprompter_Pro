package com.teleprompter.app.data.repository

import com.teleprompter.app.data.db.ScriptDao
import com.teleprompter.app.data.models.Script
import com.teleprompter.app.data.validation.ScriptValidator
import com.teleprompter.app.utils.DatabaseException
import com.teleprompter.app.utils.Result
import com.teleprompter.app.utils.ValidationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Implementation of ScriptRepository
 */
class ScriptRepositoryImpl(
    private val scriptDao: ScriptDao,
    private val validator: ScriptValidator,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ScriptRepository {

    override fun getAllScripts(): Flow<List<Script>> {
        return scriptDao.getAllScripts()
            .flowOn(ioDispatcher)
            .catch { e ->
                // Log error but emit empty list
                emit(emptyList())
            }
    }

    override suspend fun getScriptById(id: Long): Result<Script> = withContext(ioDispatcher) {
        try {
            val script = scriptDao.getScriptById(id)
            if (script != null) {
                Result.Success(script)
            } else {
                Result.Error(
                    DatabaseException("Script not found with id: $id"),
                    "Script not found"
                )
            }
        } catch (e: Exception) {
            Result.Error(
                DatabaseException("Failed to get script", e),
                "Failed to load script: ${e.message}"
            )
        }
    }

    override suspend fun insertScript(script: Script): Result<Long> = withContext(ioDispatcher) {
        try {
            // Validate script
            val validationResult = validator.validate(script)
            if (!validationResult.isValid) {
                return@withContext Result.Error(
                    ValidationException(validationResult.errorMessage ?: "Validation failed"),
                    validationResult.errorMessage
                )
            }

            // Insert script
            val id = scriptDao.insertScript(script)
            Result.Success(id)
        } catch (e: Exception) {
            Result.Error(
                DatabaseException("Failed to insert script", e),
                "Failed to save script: ${e.message}"
            )
        }
    }

    override suspend fun updateScript(script: Script): Result<Unit> = withContext(ioDispatcher) {
        try {
            // Validate script
            val validationResult = validator.validate(script)
            if (!validationResult.isValid) {
                return@withContext Result.Error(
                    ValidationException(validationResult.errorMessage ?: "Validation failed"),
                    validationResult.errorMessage
                )
            }

            // Update script
            scriptDao.updateScript(script)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(
                DatabaseException("Failed to update script", e),
                "Failed to update script: ${e.message}"
            )
        }
    }

    override suspend fun deleteScript(script: Script): Result<Unit> = withContext(ioDispatcher) {
        try {
            scriptDao.deleteScript(script)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(
                DatabaseException("Failed to delete script", e),
                "Failed to delete script: ${e.message}"
            )
        }
    }

    override fun searchScripts(query: String): Flow<List<Script>> {
        val searchQuery = "%$query%"
        return scriptDao.searchScripts(searchQuery)
            .flowOn(ioDispatcher)
            .catch { e ->
                // Log error but emit empty list
                emit(emptyList())
            }
    }
}
