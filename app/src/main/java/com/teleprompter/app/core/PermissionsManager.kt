package com.teleprompter.app.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import com.teleprompter.app.utils.hasOverlayPermission

/**
 * Manager for handling overlay and notification permissions
 */
class PermissionsManager(private val context: Context) {

    /**
     * Check if app has overlay permission
     */
    fun hasOverlayPermission(): Boolean {
        return context.hasOverlayPermission()
    }

    /**
     * Check if notification permission is required (Android 13+)
     */
    fun isNotificationPermissionRequired(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    /**
     * Create intent to request overlay permission
     */
    fun createOverlayPermissionIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
    }

    /**
     * Check if device supports overlay permission
     */
    fun canRequestOverlayPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    }

    companion object {
        /**
         * Check overlay permission from any context
         */
        fun checkOverlayPermission(context: Context): Boolean {
            return context.hasOverlayPermission()
        }
    }
}
