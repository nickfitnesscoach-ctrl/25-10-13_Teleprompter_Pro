package com.teleprompter.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Manages overlay position preferences using DataStore
 */
class OverlayPreferences(private val context: Context) {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "overlay_preferences")

    companion object {
        private val OVERLAY_X = intPreferencesKey("overlay_x")
        private val OVERLAY_Y = intPreferencesKey("overlay_y")

        // Default position (center-ish)
        const val DEFAULT_X = 0
        const val DEFAULT_Y = 100
    }

    /**
     * Get saved overlay X position
     */
    val overlayX: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[OVERLAY_X] ?: DEFAULT_X
    }

    /**
     * Get saved overlay Y position
     */
    val overlayY: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[OVERLAY_Y] ?: DEFAULT_Y
    }

    /**
     * Save overlay position
     */
    suspend fun saveOverlayPosition(x: Int, y: Int) {
        context.dataStore.edit { preferences ->
            preferences[OVERLAY_X] = x
            preferences[OVERLAY_Y] = y
        }
    }

    /**
     * Get position synchronously (for initial load)
     */
    suspend fun getPosition(): Pair<Int, Int> {
        val preferences = context.dataStore.data.first()
        val x = preferences[OVERLAY_X] ?: DEFAULT_X
        val y = preferences[OVERLAY_Y] ?: DEFAULT_Y
        return Pair(x, y)
    }
}
