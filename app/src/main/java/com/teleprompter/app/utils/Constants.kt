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

    // DataStore keys (only used ones)
    const val PREF_SCROLL_SPEED = "scroll_speed"
    const val PREF_LAST_SCRIPT_ID = "last_script_id"

    // Scroll control
    const val MIN_SCROLL_SPEED = 10
    const val MAX_SCROLL_SPEED = 200
    const val SCROLL_SPEED_STEP = 10

    // Intent extras
    const val EXTRA_SCRIPT_ID = "extra_script_id"
    const val EXTRA_SCRIPT_CONTENT = "extra_script_content"

    // Database
    const val DATABASE_NAME = "teleprompter_db"
}
