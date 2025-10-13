package com.teleprompter.app.utils

/**
 * Application-wide constants
 */
object Constants {
    // Permissions
    const val OVERLAY_PERMISSION_REQUEST_CODE = 1001

    // Notification
    const val NOTIFICATION_CHANNEL_ID = "teleprompter_service"
    const val NOTIFICATION_CHANNEL_NAME = "Teleprompter Service"
    const val FOREGROUND_SERVICE_ID = 100

    // DataStore keys
    const val PREF_SCROLL_SPEED = "scroll_speed"
    const val PREF_FONT_SIZE = "font_size"
    const val PREF_TRANSPARENCY = "transparency"
    const val PREF_TEXT_COLOR = "text_color"
    const val PREF_BG_COLOR = "bg_color"
    const val PREF_POSITION_X = "position_x"
    const val PREF_POSITION_Y = "position_y"
    const val PREF_LAST_SCRIPT_ID = "last_script_id"

    // Default values
    const val DEFAULT_SCROLL_SPEED = 50 // px per second
    const val DEFAULT_FONT_SIZE = 28 // sp
    const val DEFAULT_TRANSPARENCY = 85 // 0-100%
    const val DEFAULT_TEXT_COLOR = 0xFFFFFFFF.toInt() // White
    const val DEFAULT_BG_COLOR = 0x80000000.toInt() // Semi-transparent black

    // Scroll control
    const val MIN_SCROLL_SPEED = 10
    const val MAX_SCROLL_SPEED = 200
    const val SCROLL_SPEED_STEP = 10

    // Font size control
    const val MIN_FONT_SIZE = 16
    const val MAX_FONT_SIZE = 48
    const val FONT_SIZE_STEP = 2

    // Transparency control
    const val MIN_TRANSPARENCY = 0
    const val MAX_TRANSPARENCY = 100

    // Intent extras
    const val EXTRA_SCRIPT_ID = "extra_script_id"
    const val EXTRA_SCRIPT_CONTENT = "extra_script_content"

    // Database
    const val DATABASE_NAME = "teleprompter_db"

    // UI Constants
    const val PREVIEW_LENGTH = 100
    const val DEFAULT_OVERLAY_Y = 100

    // Animation Constants
    const val FPS = 60
    const val FRAME_DELAY_MS = 16L  // 1000ms / 60fps ≈ 16ms

    // Validation Constants
    const val MAX_TITLE_LENGTH = 100
    const val MAX_CONTENT_LENGTH = 100000  // 100KB of text
}
