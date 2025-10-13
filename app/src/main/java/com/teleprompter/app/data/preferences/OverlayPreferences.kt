package com.teleprompter.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Manages overlay position preferences using DataStore
 */
class OverlayPreferences(private val context: Context) {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "overlay_preferences")

    companion object {
        private val OVERLAY_X = intPreferencesKey("overlay_x")
        private val OVERLAY_Y = intPreferencesKey("overlay_y")
        private val OVERLAY_HEIGHT = intPreferencesKey("overlay_height")

        // Default position (center-ish)
        const val DEFAULT_X = 0
        const val DEFAULT_Y = 100
        const val DEFAULT_HEIGHT = 800 // Default height in pixels (approximately 400dp on most devices)
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
     * Get saved overlay height
     */
    val overlayHeight: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[OVERLAY_HEIGHT] ?: DEFAULT_HEIGHT
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
     * Save overlay height
     */
    suspend fun saveOverlayHeight(height: Int) {
        context.dataStore.edit { preferences ->
            preferences[OVERLAY_HEIGHT] = height
        }
    }

    /**
     * Get position synchronously (for initial load)
     */
    suspend fun getPosition(): Pair<Int, Int> {
        var x = DEFAULT_X
        var y = DEFAULT_Y

        context.dataStore.edit { preferences ->
            x = preferences[OVERLAY_X] ?: DEFAULT_X
            y = preferences[OVERLAY_Y] ?: DEFAULT_Y
        }

        return Pair(x, y)
    }

    /**
     * Get height synchronously (for initial load)
     */
    suspend fun getHeight(): Int {
        var height = DEFAULT_HEIGHT

        context.dataStore.edit { preferences ->
            height = preferences[OVERLAY_HEIGHT] ?: DEFAULT_HEIGHT
        }

        return height
    }

    /**
     * Reset overlay height to default
     */
    suspend fun resetHeight() {
        context.dataStore.edit { preferences ->
            preferences.remove(OVERLAY_HEIGHT)
        }
    }
}
