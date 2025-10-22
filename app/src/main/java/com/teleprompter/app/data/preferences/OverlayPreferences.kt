package com.teleprompter.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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
        private val OVERLAY_WIDTH = intPreferencesKey("overlay_width")
        private val OVERLAY_HEIGHT = intPreferencesKey("overlay_height")
        private val TEXT_SIZE = floatPreferencesKey("text_size")

        // Default position (center-ish)
        const val DEFAULT_X = 0
        const val DEFAULT_Y = 100
        const val DEFAULT_WIDTH = -1 // -1 means MATCH_PARENT
        const val DEFAULT_HEIGHT = 800 // Default height in pixels (approximately 400dp on most devices)
        const val DEFAULT_TEXT_SIZE = 28f // Default text size in sp
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
     * Get saved overlay width
     */
    val overlayWidth: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[OVERLAY_WIDTH] ?: DEFAULT_WIDTH
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
     * Save overlay width
     */
    suspend fun saveOverlayWidth(width: Int) {
        context.dataStore.edit { preferences ->
            preferences[OVERLAY_WIDTH] = width
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
     * Save overlay size (width and height)
     */
    suspend fun saveOverlaySize(width: Int, height: Int) {
        context.dataStore.edit { preferences ->
            preferences[OVERLAY_WIDTH] = width
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
     * Get width synchronously (for initial load)
     */
    suspend fun getWidth(): Int {
        var width = DEFAULT_WIDTH

        context.dataStore.edit { preferences ->
            width = preferences[OVERLAY_WIDTH] ?: DEFAULT_WIDTH
        }

        return width
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
     * Get size synchronously (for initial load)
     */
    suspend fun getSize(): Pair<Int, Int> {
        var width = DEFAULT_WIDTH
        var height = DEFAULT_HEIGHT

        context.dataStore.edit { preferences ->
            width = preferences[OVERLAY_WIDTH] ?: DEFAULT_WIDTH
            height = preferences[OVERLAY_HEIGHT] ?: DEFAULT_HEIGHT
        }

        return Pair(width, height)
    }

    /**
     * Reset overlay height to default
     */
    suspend fun resetHeight() {
        context.dataStore.edit { preferences ->
            preferences.remove(OVERLAY_HEIGHT)
        }
    }

    /**
     * Save text size
     */
    suspend fun saveTextSize(textSize: Float) {
        context.dataStore.edit { preferences ->
            preferences[TEXT_SIZE] = textSize
        }
    }

    /**
     * Get text size synchronously (for initial load)
     */
    suspend fun getTextSize(): Float {
        var textSize = DEFAULT_TEXT_SIZE

        context.dataStore.edit { preferences ->
            textSize = preferences[TEXT_SIZE] ?: DEFAULT_TEXT_SIZE
        }

        return textSize
    }
}
