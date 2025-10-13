package com.teleprompter.app.core

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.teleprompter.app.R

/**
 * Controller for managing overlay window operations
 */
class OverlayController(private val context: Context) {

    private val windowManager = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var params: WindowManager.LayoutParams? = null

    // Drag state
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    /**
     * Show overlay window
     */
    fun showOverlay(layoutRes: Int, onViewCreated: (View) -> Unit) {
        if (overlayView != null) return

        // Inflate layout
        val inflater = LayoutInflater.from(context.applicationContext)
        overlayView = inflater.inflate(layoutRes, null)

        // Create layout params
        params = createLayoutParams()

        // Add to window manager
        windowManager.addView(overlayView, params)

        // Setup drag functionality
        setupDragListener()

        // Notify view created
        onViewCreated(overlayView!!)
    }

    /**
     * Remove overlay window
     */
    fun removeOverlay() {
        overlayView?.let { view ->
            // Check if view is actually attached before removing
            if (view.windowToken != null || view.parent != null) {
                try {
                    windowManager.removeView(view)
                } catch (e: IllegalArgumentException) {
                    // View was already removed, ignore
                } catch (e: IllegalStateException) {
                    // Window manager might be dead, ignore
                }
            }
            overlayView = null
            params = null
        }
    }

    /**
     * Check if overlay is currently shown
     */
    fun isShowing(): Boolean = overlayView != null

    /**
     * Update overlay transparency
     */
    fun setTransparency(alpha: Float) {
        overlayView?.alpha = alpha.coerceIn(0f, 1f)
    }

    /**
     * Create WindowManager layout parameters
     */
    private fun createLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100 // Default Y position from top
        }
    }

    /**
     * Setup drag-and-drop functionality for overlay
     */
    private fun setupDragListener() {
        val dragHandle = overlayView?.findViewById<View>(R.id.dragHandle) ?: return

        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params?.x = initialX + (event.rawX - initialTouchX).toInt()
                    params?.y = initialY + (event.rawY - initialTouchY).toInt()
                    overlayView?.let { windowManager.updateViewLayout(it, params) }
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Update overlay position
     */
    fun updatePosition(x: Int, y: Int) {
        params?.x = x
        params?.y = y
        overlayView?.let { windowManager.updateViewLayout(it, params) }
    }

    /**
     * Get current overlay view
     */
    fun getOverlayView(): View? = overlayView

    /**
     * Get current position X
     */
    fun getPositionX(): Int = params?.x ?: 0

    /**
     * Get current position Y
     */
    fun getPositionY(): Int = params?.y ?: 0
}
