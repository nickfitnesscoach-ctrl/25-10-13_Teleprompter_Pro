package com.teleprompter.app.ui.overlay

import android.annotation.SuppressLint
import android.Manifest
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.Html
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.teleprompter.app.R
import com.teleprompter.app.data.preferences.OverlayPreferences
import com.teleprompter.app.ui.main.MainActivity
import com.teleprompter.app.utils.Constants
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Foreground service for displaying teleprompter overlay
 * All overlay logic is contained within this service to ensure
 * it works independently of Activity lifecycle
 */
class TeleprompterOverlayService : LifecycleService() {

    // WindowManager for overlay
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    // Preferences for saving position
    private lateinit var overlayPreferences: OverlayPreferences

    // Drag state
    private var isDragging = false
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialX = 0
    private var initialY = 0

    // Resize state
    private var isResizing = false
    private var initialResizeTouchX = 0f
    private var initialResizeTouchY = 0f
    private var initialWidth = 0
    private var initialHeight = 0

    // Text size pinch-to-zoom
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var currentTextSize = 28f // Base text size in sp
    private val minTextSize = 12f // Lower minimum for more range
    private val maxTextSize = 72f
    private var lastSavedTextSize = 28f // For throttling saves
    private var baseTextSize = 28f // Text size at start of gesture
    private var isScaling = false // Track if currently scaling
    private var accumulatedScale = 1.0f // Accumulated scale for smoothing

    // Text alignment
    private var currentAlignment = 0 // 0 = start, 1 = center, 2 = end

    // Overlay opacity
    private var currentOpacity = 100 // 0-100, where 100 is fully opaque
    private var isOpacityPanelVisible = false
    private val opacityPanelHandler = Handler(Looper.getMainLooper())
    private var opacityPanelHideRunnable: Runnable? = null

    // Scroll animation
    private var scrollAnimator: ValueAnimator? = null
    private var isScrolling = false
    private var scrollSpeed = 50 // Speed level: 1 = slowest, 500 = fastest

    // Speed control with hold functionality
    private val speedChangeHandler = Handler(Looper.getMainLooper())
    private var speedChangeRunnable: Runnable? = null
    private var isHoldingButton = false

    // UI components
    private var scriptTextView: TextView? = null
    private var scrollView: ScrollView? = null
    private var speedIndicator: TextView? = null
    private var speedOverlay: TextView? = null

    // Button wrapping
    private var speedControlRow: View? = null
    private var mainControlRow: View? = null
    private val minWidthForSingleRow = 350 // dp - minimum width to show all buttons in one row

    // Speed overlay timer
    private val speedOverlayHandler = Handler(Looper.getMainLooper())
    private var speedOverlayRunnable: Runnable? = null

    // Track current script content
    private var currentScriptContent: String? = null

    // Orientation listener
    private var orientationEventListener: OrientationEventListener? = null
    private var lastOrientation = Configuration.ORIENTATION_UNDEFINED
    private var pendingOrientationChange: Runnable? = null
    private val orientationChangeHandler = Handler(Looper.getMainLooper())

    // PIP mode state
    private var isPipMode = false
    private var pipView: View? = null

    override fun onCreate() {
        super.onCreate()

        // Check notification permission for Android 13+
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(
                this,
                "Notification permission required for teleprompter overlay",
                Toast.LENGTH_LONG
            ).show()
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayPreferences = OverlayPreferences(this)

        // Initialize orientation listener
        setupOrientationListener()

        // Create notification channel
        createNotificationChannel()

        // Must call startForeground within 5 seconds of service start
        startForeground(Constants.FOREGROUND_SERVICE_ID, createNotification())
    }

    /**
     * Setup orientation listener to detect screen rotation
     */
    private fun setupOrientationListener() {
        lastOrientation = getCurrentOrientation()

        orientationEventListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(angle: Int) {
                if (angle == ORIENTATION_UNKNOWN) return

                // Get the actual display rotation
                val newOrientation = getCurrentOrientation()

                // Check if orientation actually changed
                if (newOrientation != lastOrientation && overlayView != null) {
                    // Cancel any pending orientation change
                    pendingOrientationChange?.let { orientationChangeHandler.removeCallbacks(it) }

                    // Schedule orientation change with debounce to avoid flickering
                    pendingOrientationChange = Runnable {
                        // Double check orientation hasn't changed back
                        val confirmedOrientation = getCurrentOrientation()

                        if (confirmedOrientation == newOrientation && confirmedOrientation != lastOrientation) {
                            lastOrientation = newOrientation
                            Log.d("TeleprompterService", "Orientation changed to: $newOrientation (angle: $angle, rotation: ${getDisplayRotation()})")

                            // Recreate overlay with new orientation
                            recreateOverlay()
                        }
                    }

                    // Wait 500ms to confirm orientation change (longer delay to reduce flickering)
                    orientationChangeHandler.postDelayed(pendingOrientationChange!!, 500)
                }
            }
        }

        // Enable the listener if it can detect orientation
        if (orientationEventListener?.canDetectOrientation() == true) {
            orientationEventListener?.enable()
            Log.d("TeleprompterService", "Orientation listener enabled")
        } else {
            Log.w("TeleprompterService", "Cannot detect orientation changes")
        }
    }

    /**
     * Get current display rotation
     */
    @Suppress("DEPRECATION")
    private fun getDisplayRotation(): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val display = windowManager.defaultDisplay
                display?.rotation ?: Surface.ROTATION_0
            } else {
                windowManager.defaultDisplay.rotation
            }
        } catch (e: Exception) {
            Log.e("TeleprompterService", "Error getting display rotation", e)
            Surface.ROTATION_0
        }
    }

    /**
     * Get current orientation based on display rotation
     */
    private fun getCurrentOrientation(): Int {
        return when (getDisplayRotation()) {
            Surface.ROTATION_90, Surface.ROTATION_270 -> Configuration.ORIENTATION_LANDSCAPE
            else -> Configuration.ORIENTATION_PORTRAIT
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        try {
            // Get script content from intent
            val scriptContent = intent?.getStringExtra(Constants.EXTRA_SCRIPT_CONTENT)
                ?: getString(R.string.default_teleprompter_text)

            // Save current script content
            currentScriptContent = scriptContent

            // Create and show overlay
            createOverlay()

            // Set script text after overlay is created (convert markdown to HTML)
            val htmlContent = convertMarkdownToHtml(scriptContent)
            scriptTextView?.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Html.fromHtml(htmlContent, Html.FROM_HTML_MODE_COMPACT)
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(htmlContent)
            }

        } catch (e: Exception) {
            Toast.makeText(this, "Error showing overlay: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }

        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Recreate overlay with new orientation
        recreateOverlay()
    }

    /**
     * Recreate overlay when configuration changes (e.g., orientation)
     */
    private fun recreateOverlay() {
        // Save current state
        val wasScrolling = isScrolling
        val currentSpeed = scrollSpeed
        val scrollPosition = scrollView?.scrollY ?: 0

        // Remove old overlay
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
                // View already removed, ignore
            }
        }
        overlayView = null

        // Recreate overlay
        createOverlay()

        // Restore state
        scrollSpeed = currentSpeed
        val htmlContent = convertMarkdownToHtml(currentScriptContent ?: "")
        scriptTextView?.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(htmlContent, Html.FROM_HTML_MODE_COMPACT)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(htmlContent)
        }

        // Restore scroll position after layout
        scrollView?.post {
            scrollView?.scrollTo(0, scrollPosition)

            // Restore scrolling state
            if (wasScrolling) {
                startScrolling()
                overlayView?.findViewById<ImageButton>(R.id.btnPlayPause)
                    ?.setImageResource(R.drawable.ic_pause)
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }

    /**
     * Create overlay window and setup UI
     */
    @SuppressLint("InflateParams")
    private fun createOverlay() {
        if (overlayView != null && overlayView?.parent != null) return

        // Inflate layout without parent (null is correct for WindowManager overlays)
        val inflater = LayoutInflater.from(this)

        // Determine layout based on current display rotation
        val currentOrientation = getCurrentOrientation()
        lastOrientation = currentOrientation

        val layoutRes = if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            R.layout.overlay_landscape
        } else {
            R.layout.overlay_portrait
        }

        Log.d("TeleprompterService", "Creating overlay with orientation: $currentOrientation (rotation: ${getDisplayRotation()}), layout: $layoutRes")
        overlayView = inflater.inflate(layoutRes, null)

        // Create layout params - always use TYPE_APPLICATION_OVERLAY for Android O+
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        // Load saved position and size synchronously
        val (savedX, savedY) = runBlocking {
            overlayPreferences.getPosition()
        }
        val (savedWidth, savedHeight) = runBlocking {
            overlayPreferences.getSize()
        }

        // Get screen dimensions
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Calculate max height (leave space for status bar and navigation bar)
        val maxHeight = (screenHeight * 0.85).toInt() // Use 85% of screen height

        // Constrain saved dimensions
        val finalWidth = if (savedWidth == -1) WindowManager.LayoutParams.MATCH_PARENT else savedWidth.coerceAtMost(screenWidth)
        val finalHeight = savedHeight.coerceAtMost(maxHeight)

        layoutParams = WindowManager.LayoutParams(
            finalWidth,
            finalHeight,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY.coerceAtMost(screenHeight - finalHeight)
        }

        // Add view to window
        windowManager.addView(overlayView, layoutParams)

        // Setup UI components
        setupViews()
    }

    /**
     * Setup overlay views and button listeners
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupViews() {
        val view = overlayView ?: return

        // Find views
        scrollView = view.findViewById(R.id.scriptScrollView)
        scriptTextView = view.findViewById(R.id.scriptTextView)
        speedIndicator = view.findViewById(R.id.speedIndicator)
        speedOverlay = view.findViewById(R.id.speedOverlay)
        speedControlRow = view.findViewById(R.id.speedControlRow)
        mainControlRow = view.findViewById(R.id.mainControlRow)

        val btnPlayPause = view.findViewById<ImageButton>(R.id.btnPlayPause)
        val btnSlower = view.findViewById<ImageButton>(R.id.btnSlower)
        val btnFaster = view.findViewById<ImageButton>(R.id.btnFaster)
        val btnMinimize = view.findViewById<ImageButton>(R.id.btnMinimize)

        // Setup duplicate buttons in top row
        val btnSlowerTop = view.findViewById<ImageButton>(R.id.btnSlowerTop)
        val btnPlayPauseTop = view.findViewById<ImageButton>(R.id.btnPlayPauseTop)
        val btnFasterTop = view.findViewById<ImageButton>(R.id.btnFasterTop)

        // Setup button listeners for both main and top row buttons
        val playPauseClickListener = View.OnClickListener {
            if (isScrolling) {
                stopScrolling()
                btnPlayPause?.setImageResource(R.drawable.ic_play)
                btnPlayPauseTop?.setImageResource(R.drawable.ic_play)
            } else {
                startScrolling()
                btnPlayPause?.setImageResource(android.R.drawable.ic_media_pause)
                btnPlayPauseTop?.setImageResource(R.drawable.ic_pause)
            }
        }

        btnPlayPause?.setOnClickListener(playPauseClickListener)
        btnPlayPauseTop?.setOnClickListener(playPauseClickListener)

        // Setup slower button with hold functionality
        val slowerTouchListener = View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    decreaseSpeed()
                    startSpeedChangeRepeater(false) // false = slower
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopSpeedChangeRepeater()
                    true
                }
                else -> false
            }
        }

        btnSlower?.setOnTouchListener(slowerTouchListener)
        btnSlowerTop?.setOnTouchListener(slowerTouchListener)

        // Setup faster button with hold functionality
        val fasterTouchListener = View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    increaseSpeed()
                    startSpeedChangeRepeater(true) // true = faster
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopSpeedChangeRepeater()
                    true
                }
                else -> false
            }
        }

        btnFaster?.setOnTouchListener(fasterTouchListener)
        btnFasterTop?.setOnTouchListener(fasterTouchListener)

        // Setup drag button
        val btnDrag = view.findViewById<ImageButton>(R.id.btnDrag)
        btnDrag?.setOnTouchListener { _, event ->
            handleDragTouch(event)
        }

        // Setup resize button
        val btnResize = view.findViewById<ImageButton>(R.id.btnResize)
        btnResize?.setOnTouchListener { _, event ->
            handleResizeTouch(event)
        }

        btnMinimize?.setOnClickListener {
            // Return to main activity (script list) - just bring it to front
            val intent = Intent(this, com.teleprompter.app.ui.main.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }

            startActivity(intent)

            // Hide overlay and stop service
            overlayView?.let { view ->
                try {
                    windowManager.removeView(view)
                } catch (_: Exception) {
                    // View already removed
                }
            }
            overlayView = null
            stopSelf()
        }

        // Setup PIP button (Picture-in-Picture mode)
        val btnPip = view.findViewById<ImageButton>(R.id.btnPip)
        btnPip?.setOnClickListener {
            enterPipMode()
        }

        // Setup text alignment button
        val btnAlignText = view.findViewById<ImageButton>(R.id.btnAlignText)
        btnAlignText?.setOnClickListener {
            toggleTextAlignment()
        }

        // Setup opacity button and slider
        setupOpacityControls()

        // Setup pinch-to-zoom for text size
        setupTextSizeGesture()

        // Update speed indicator
        updateSpeedIndicator()

        // Setup layout change listener for button wrapping
        overlayView?.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
            val width = right - left
            updateButtonLayout(width)
        }
    }

    /**
     * Update button layout based on overlay width
     */
    private fun updateButtonLayout(widthPx: Int) {
        val view = overlayView ?: return
        val density = resources.displayMetrics.density
        val widthDp = widthPx / density

        val btnSlower = view.findViewById<ImageButton>(R.id.btnSlower)
        val playPauseFrame = view.findViewById<View>(R.id.playPauseFrame)
        val btnFaster = view.findViewById<ImageButton>(R.id.btnFaster)

        Log.d("TeleprompterService", "updateButtonLayout: widthDp=$widthDp, minWidth=$minWidthForSingleRow")

        if (widthDp < minWidthForSingleRow) {
            // Narrow: show speed controls in top row, hide from main row
            Log.d("TeleprompterService", "Narrow mode: showing top row")
            speedControlRow?.visibility = View.VISIBLE
            btnSlower?.visibility = View.GONE
            playPauseFrame?.visibility = View.GONE
            btnFaster?.visibility = View.GONE
        } else {
            // Wide: show all buttons in main row, hide top row
            Log.d("TeleprompterService", "Wide mode: showing main row only")
            speedControlRow?.visibility = View.GONE
            btnSlower?.visibility = View.VISIBLE
            playPauseFrame?.visibility = View.VISIBLE
            btnFaster?.visibility = View.VISIBLE
        }
    }

    /**
     * Setup pinch-to-zoom gesture for text size with smooth scaling
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupTextSizeGesture() {
        val textView = scriptTextView ?: return
        val scroll = scrollView ?: return

        // Load saved text size
        lifecycleScope.launch {
            currentTextSize = overlayPreferences.getTextSize()
            textView.textSize = currentTextSize
        }

        // Initialize ScaleGestureDetector with improved smoothness
        scaleGestureDetector = ScaleGestureDetector(this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    // Mark that scaling has started
                    isScaling = true
                    baseTextSize = currentTextSize
                    accumulatedScale = 1.0f
                    Log.d("TeleprompterService", "Scale begin - baseSize: $baseTextSize")
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    // Get scale factor from detector
                    val scaleFactor = detector.scaleFactor

                    // Filter out invalid scale factors
                    if (scaleFactor <= 0f || scaleFactor.isNaN() || scaleFactor.isInfinite()) {
                        return false
                    }

                    // Amplify the scale factor for faster response
                    // Different amplification for zoom in vs zoom out for better control
                    val amplifiedScale = if (scaleFactor > 1.0f) {
                        // Zooming in: 3.5x amplification
                        1.0f + (scaleFactor - 1.0f) * 3.5f
                    } else {
                        // Zooming out: 2.5x amplification (slower for better control)
                        1.0f - (1.0f - scaleFactor) * 2.5f
                    }

                    // Calculate new text size based on current size with amplification
                    val newTextSize = currentTextSize * amplifiedScale

                    // Apply min/max constraints to text size
                    val constrainedSize = newTextSize.coerceIn(minTextSize, maxTextSize)

                    // Check if size actually changed to avoid unnecessary updates
                    if (kotlin.math.abs(constrainedSize - currentTextSize) < 0.01f) {
                        // Size didn't change (hit boundary), but keep gesture alive
                        return true
                    }

                    // Update text size immediately for smooth response
                    currentTextSize = constrainedSize
                    textView.textSize = currentTextSize

                    // Throttled save - only if changed by at least 1sp
                    if (kotlin.math.abs(currentTextSize - lastSavedTextSize) > 1.0f) {
                        lastSavedTextSize = currentTextSize
                        lifecycleScope.launch {
                            overlayPreferences.saveTextSize(currentTextSize)
                        }
                    }

                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    isScaling = false
                    accumulatedScale = 1.0f

                    // Final save with exact value
                    lifecycleScope.launch {
                        overlayPreferences.saveTextSize(currentTextSize)
                    }
                    Log.d("TeleprompterService", "Scale end - final size: $currentTextSize")
                }
            })

        // Set touch listener on SCROLLVIEW instead of TextView
        // This ensures pinch gestures are captured before ScrollView intercepts them
        scroll.setOnTouchListener { _, event ->
            // Let ScaleGestureDetector handle the event first
            scaleGestureDetector.onTouchEvent(event)

            val pointerCount = event.pointerCount

            // Block manual scrolling when auto-scrolling is active
            // Allow pinch-to-zoom always (2+ fingers)
            // Allow manual scroll only when paused (1 finger + not scrolling)
            when {
                pointerCount >= 2 -> true // Always allow pinch-to-zoom
                isScrolling -> true // Block manual scroll during auto-scroll
                else -> false // Allow manual scroll when paused
            }
        }

        // Load saved alignment
        lifecycleScope.launch {
            currentAlignment = overlayPreferences.getTextAlignment()
            applyTextAlignment()
        }
    }

    /**
     * Toggle text alignment (start -> center -> end -> start)
     */
    private fun toggleTextAlignment() {
        currentAlignment = (currentAlignment + 1) % 3
        applyTextAlignment()

        // Save alignment
        lifecycleScope.launch {
            overlayPreferences.saveTextAlignment(currentAlignment)
        }
    }

    /**
     * Apply current text alignment to TextView
     */
    private fun applyTextAlignment() {
        val textView = scriptTextView ?: return
        val gravity = when (currentAlignment) {
            0 -> Gravity.START or Gravity.TOP
            1 -> Gravity.CENTER_HORIZONTAL or Gravity.TOP
            2 -> Gravity.END or Gravity.TOP
            else -> Gravity.START or Gravity.TOP
        }
        textView.gravity = gravity
    }

    /**
     * Setup opacity controls (button and slider)
     */
    private fun setupOpacityControls() {
        val view = overlayView ?: return
        val btnOpacity = view.findViewById<ImageButton>(R.id.btnOpacity)
        val opacitySliderPanel = view.findViewById<View>(R.id.opacitySliderPanel)
        val opacitySeekBar = view.findViewById<android.widget.SeekBar>(R.id.opacitySeekBar)
        val opacityValueText = view.findViewById<TextView>(R.id.opacityValueText)

        // Load saved opacity
        lifecycleScope.launch {
            currentOpacity = overlayPreferences.getOverlayOpacity()
            opacitySeekBar?.progress = currentOpacity
            opacityValueText?.text = currentOpacity.toString()
            applyOverlayOpacity()
        }

        // Toggle opacity slider panel visibility
        btnOpacity?.setOnClickListener {
            isOpacityPanelVisible = !isOpacityPanelVisible
            opacitySliderPanel?.visibility = if (isOpacityPanelVisible) View.VISIBLE else View.GONE
        }

        // Setup seekbar listener
        opacitySeekBar?.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentOpacity = progress
                    opacityValueText?.text = progress.toString()
                    applyOverlayOpacity()
                }
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {
                // Cancel auto-hide when user starts touching
                opacityPanelHideRunnable?.let { opacityPanelHandler.removeCallbacks(it) }
            }

            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                // Save opacity when user releases slider
                lifecycleScope.launch {
                    overlayPreferences.saveOverlayOpacity(currentOpacity)
                }

                // Schedule auto-hide after 1 second
                scheduleOpacityPanelHide()
            }
        })
    }

    /**
     * Schedule opacity panel to auto-hide after 1 second
     */
    private fun scheduleOpacityPanelHide() {
        // Cancel any existing scheduled hide
        opacityPanelHideRunnable?.let { opacityPanelHandler.removeCallbacks(it) }

        // Schedule new hide
        opacityPanelHideRunnable = Runnable {
            val opacitySliderPanel = overlayView?.findViewById<View>(R.id.opacitySliderPanel)
            opacitySliderPanel?.visibility = View.GONE
            isOpacityPanelVisible = false
        }

        opacityPanelHandler.postDelayed(opacityPanelHideRunnable!!, 1000)
    }

    /**
     * Apply current opacity to overlay with text being 30% less transparent
     * Example: Overlay 50% -> Text 80%, Overlay 40% -> Text 70%
     * Uses drawable with rounded corners to maintain design
     */
    private fun applyOverlayOpacity() {
        val view = overlayView ?: return
        val textView = scriptTextView ?: return

        // Overlay opacity: same as slider value (0-100 mapped to 0-255)
        val overlayAlphaInt = (currentOpacity * 2.55f).toInt()

        // Text opacity: 30% less transparent (add 30 to percentage, then convert to 0-255)
        val textOpacity = (currentOpacity + 30).coerceAtMost(100)
        val textAlphaInt = (textOpacity * 2.55f).toInt()

        // Create rounded rectangle drawable for text area (16dp corners)
        val textPanelDrawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(android.graphics.Color.argb(overlayAlphaInt, 0, 0, 0))
            cornerRadius = resources.getDimension(R.dimen.text_area_corner_radius)
        }

        // Create rounded rectangle drawable for control panel (20dp corners)
        val controlPanelDrawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(android.graphics.Color.argb(overlayAlphaInt, 0, 0, 0))
            cornerRadius = resources.getDimension(R.dimen.control_panel_corner_radius)
        }

        // Apply drawables with rounded corners
        view.findViewById<View>(R.id.scriptScrollView)?.background = textPanelDrawable
        view.findViewById<View>(R.id.controlButtons)?.background = controlPanelDrawable

        // Apply to text using textColor with alpha
        val textColor = android.graphics.Color.argb(textAlphaInt, 255, 255, 255) // White with alpha
        textView.setTextColor(textColor)
    }

    /**
     * Start auto-scrolling with ValueAnimator
     */
    private fun startScrolling() {
        val scroll = scrollView ?: return
        val textView = scriptTextView ?: return

        // Ensure TextView can wrap multiple lines
        textView.maxLines = Int.MAX_VALUE
        textView.ellipsize = null

        val scrollViewHeight = scroll.height

        // Add TOP padding so text starts at bottom of screen
        val currentPaddingBottom = textView.paddingBottom
        textView.setPadding(
            textView.paddingLeft,
            scrollViewHeight, // Add top padding equal to scroll view height
            textView.paddingRight,
            currentPaddingBottom
        )

        // Wait for layout to be measured
        textView.post {
            // Force measure
            textView.measure(
                View.MeasureSpec.makeMeasureSpec(scroll.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )

            // Get the content height (child of ScrollView)
            val contentHeight = textView.measuredHeight
            val maxScroll = contentHeight - scrollViewHeight

            Log.d("TeleprompterService", "contentHeight=$contentHeight, scrollViewHeight=$scrollViewHeight, maxScroll=$maxScroll")

            if (maxScroll <= 0) {
                Toast.makeText(this, "Text is too short to scroll", Toast.LENGTH_SHORT).show()
                return@post
            }

            val currentY = scroll.scrollY

            // Always start from position 0 (text at bottom of screen) and scroll to maxScroll (text moves up)
            if (currentY == 0) {
                Log.d("TeleprompterService", "Starting teleprompter - text will scroll from bottom to top")
            }

            // Scroll from current position to maxScroll (text moves upward)
            val remainingDistance = maxScroll - currentY

            if (remainingDistance <= 0) {
                Toast.makeText(this, "Already at the end", Toast.LENGTH_SHORT).show()
                return@post
            }

            val duration = (remainingDistance * 1000 / scrollSpeed).toLong()

            Log.d("TeleprompterService", "Scrolling from $currentY to $maxScroll, duration=$duration ms")

            scrollAnimator = ValueAnimator.ofInt(currentY, maxScroll).apply {
                this.duration = duration
                interpolator = LinearInterpolator()

                addUpdateListener { animator ->
                    if (isScrolling) {
                        val value = animator.animatedValue as Int
                        scroll.scrollTo(0, value)
                    }
                }

                start()
            }

            isScrolling = true
        }
    }

    /**
     * Stop auto-scrolling (pause)
     */
    private fun stopScrolling() {
        scrollAnimator?.cancel()
        scrollAnimator = null
        isScrolling = false
    }

    /**
     * Increase scroll speed (make it faster)
     */
    private fun increaseSpeed() {
        // Higher speed number = faster
        // Increase by 1 for very smooth increment
        scrollSpeed = (scrollSpeed + 1).coerceAtMost(500)
        updateSpeedIndicator()

        // Restart scrolling with new speed if currently scrolling
        if (isScrolling) {
            stopScrolling()
            startScrolling()
            overlayView?.findViewById<ImageButton>(R.id.btnPlayPause)
                ?.setImageResource(android.R.drawable.ic_media_pause)
        }
    }

    /**
     * Decrease scroll speed (make it slower)
     */
    private fun decreaseSpeed() {
        // Lower speed number = slower
        // Decrease by 1 for very smooth increment
        scrollSpeed = (scrollSpeed - 1).coerceAtLeast(1)
        updateSpeedIndicator()

        // Restart scrolling with new speed if currently scrolling
        if (isScrolling) {
            stopScrolling()
            startScrolling()
            overlayView?.findViewById<ImageButton>(R.id.btnPlayPause)
                ?.setImageResource(android.R.drawable.ic_media_pause)
        }
    }

    /**
     * Update speed indicator text
     */
    private fun updateSpeedIndicator() {
        // Display speed level (1-500)
        speedIndicator?.text = getString(R.string.speed_format, scrollSpeed)

        // Show speed on play button temporarily
        showSpeedOverlay()
    }

    /**
     * Show speed value on play button temporarily
     */
    private fun showSpeedOverlay() {
        // Cancel any existing hide timer
        speedOverlayRunnable?.let { speedOverlayHandler.removeCallbacks(it) }

        // Hide play button and show speed overlay
        overlayView?.findViewById<ImageButton>(R.id.btnPlayPause)?.visibility = View.GONE
        speedOverlay?.apply {
            text = scrollSpeed.toString()
            visibility = View.VISIBLE
        }

        // Hide after 0.5 seconds and show play button again
        speedOverlayRunnable = Runnable {
            speedOverlay?.visibility = View.GONE
            overlayView?.findViewById<ImageButton>(R.id.btnPlayPause)?.visibility = View.VISIBLE
        }
        speedOverlayHandler.postDelayed(speedOverlayRunnable!!, 500)
    }

    /**
     * Start repeating speed changes when button is held
     */
    private fun startSpeedChangeRepeater(isIncreasing: Boolean) {
        isHoldingButton = true

        speedChangeRunnable = object : Runnable {
            override fun run() {
                if (isHoldingButton) {
                    if (isIncreasing) {
                        increaseSpeed()
                    } else {
                        decreaseSpeed()
                    }
                    // Repeat every 50ms for smooth continuous change
                    speedChangeHandler.postDelayed(this, 50)
                }
            }
        }

        // Start repeating after 300ms delay (so single tap works normally)
        speedChangeHandler.postDelayed(speedChangeRunnable!!, 300)
    }

    /**
     * Stop repeating speed changes when button is released
     */
    private fun stopSpeedChangeRepeater() {
        isHoldingButton = false
        speedChangeRunnable?.let {
            speedChangeHandler.removeCallbacks(it)
        }
        speedChangeRunnable = null
    }

    /**
     * Handle drag touch events
     */
    private fun handleDragTouch(event: MotionEvent): Boolean {
        val params = layoutParams ?: return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()

                    val newX = initialX + deltaX
                    val newY = initialY + deltaY

                    // Get screen dimensions
                    val displayMetrics = resources.displayMetrics
                    val screenWidth = displayMetrics.widthPixels
                    val screenHeight = displayMetrics.heightPixels

                    // Get overlay dimensions
                    val overlayWidth = overlayView?.width ?: 0
                    val overlayHeight = overlayView?.height ?: 0

                    // Constrain to screen bounds
                    params.x = newX.coerceIn(-overlayWidth / 2, screenWidth - overlayWidth / 2)
                    params.y = newY.coerceIn(0, screenHeight - overlayHeight)

                    // Update view layout
                    overlayView?.let { windowManager.updateViewLayout(it, params) }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false

                    // Save position to preferences
                    lifecycleScope.launch {
                        overlayPreferences.saveOverlayPosition(params.x, params.y)
                        Log.d("TeleprompterService", "Saved overlay position: x=${params.x}, y=${params.y}")
                    }
                }
                return true
            }

            else -> return false
        }
    }

    /**
     * Handle touch events for resizing overlay (diagonal resize like window corner)
     */
    private fun handleResizeTouch(event: MotionEvent): Boolean {
        val params = layoutParams ?: return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isResizing = true
                initialResizeTouchX = event.rawX
                initialResizeTouchY = event.rawY
                initialWidth = params.width
                initialHeight = params.height
                Log.d("TeleprompterService", "Started resizing from width: $initialWidth, height: $initialHeight")
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isResizing) {
                    val deltaX = (event.rawX - initialResizeTouchX).toInt()
                    val deltaY = (event.rawY - initialResizeTouchY).toInt()

                    val newWidth = initialWidth + deltaX
                    val newHeight = initialHeight + deltaY

                    // Get screen dimensions
                    val displayMetrics = resources.displayMetrics
                    val screenWidth = displayMetrics.widthPixels
                    val screenHeight = displayMetrics.heightPixels

                    // Set min/max constraints
                    val minWidth = (250 * displayMetrics.density).toInt()
                    val minHeight = (150 * displayMetrics.density).toInt()

                    // Constrain width and height
                    params.width = newWidth.coerceIn(minWidth, screenWidth)
                    params.height = newHeight.coerceIn(minHeight, screenHeight)

                    // Update view layout
                    overlayView?.let { windowManager.updateViewLayout(it, params) }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isResizing) {
                    isResizing = false

                    // Save width and height to preferences
                    lifecycleScope.launch {
                        overlayPreferences.saveOverlaySize(params.width, params.height)
                        Log.d("TeleprompterService", "Saved overlay size: width=${params.width}, height=${params.height}")
                    }
                }
                return true
            }

            else -> return false
        }
    }

    /**
     * Create notification channel for foreground service
     */
    private fun createNotificationChannel() {
        NotificationChannel(
            "teleprompter_overlay",
            "Teleprompter Overlay",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps teleprompter overlay active"
            setShowBadge(false)

            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(this)
        }
    }

    /**
     * Create notification for foreground service
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, "teleprompter_overlay")
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    /**
     * Enter Picture-in-Picture mode - minimize to small circular icon
     */
    private fun enterPipMode() {
        if (isPipMode) return

        isPipMode = true

        // Stop scrolling when entering PIP
        if (isScrolling) {
            stopScrolling()
        }

        // Remove full overlay
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
                // View already removed, ignore
            }
        }

        // Create PIP view
        val inflater = LayoutInflater.from(this)
        @SuppressLint("InflateParams")
        val view = inflater.inflate(R.layout.overlay_pip, null)
        pipView = view

        // Setup PIP layout params - circular icon (slightly larger than app icons)
        val pipSize = (64 * resources.displayMetrics.density).toInt()
        val pipParams = WindowManager.LayoutParams(
            pipSize,
            pipSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 20 // Margin from right
            y = 100 // Margin from top
        }

        // Add PIP view
        windowManager.addView(pipView, pipParams)

        // Setup PIP interactions
        setupPipInteractions(pipParams)

        Log.d("TeleprompterService", "Entered PIP mode")
    }

    /**
     * Setup interactions for PIP mode
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupPipInteractions(params: WindowManager.LayoutParams) {
        val pipContainer = pipView?.findViewById<View>(R.id.pipContainer) ?: return
        val pipPlayPauseIndicator = pipView?.findViewById<ImageView>(R.id.pipPlayPauseIndicator)

        // Update play/pause indicator
        val iconRes = if (isScrolling) R.drawable.ic_pause else R.drawable.ic_play
        pipPlayPauseIndicator?.setImageResource(iconRes)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var hasMoved = false

        pipContainer.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = true
                    hasMoved = false
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        val deltaX = (initialTouchX - event.rawX).toInt()
                        val deltaY = (event.rawY - initialTouchY).toInt()

                        // Check if actually moved (more than 10px)
                        if (kotlin.math.abs(deltaX) > 10 || kotlin.math.abs(deltaY) > 10) {
                            hasMoved = true
                        }

                        params.x = initialX + deltaX
                        params.y = initialY + deltaY

                        // Update view layout
                        pipView?.let { windowManager.updateViewLayout(it, params) }
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging && !hasMoved) {
                        // Tap detected - exit PIP mode
                        exitPipMode()
                    }
                    isDragging = false
                    true
                }

                else -> false
            }
        }
    }

    /**
     * Exit Picture-in-Picture mode - restore full overlay
     */
    private fun exitPipMode() {
        if (!isPipMode) return

        isPipMode = false

        // Remove PIP view
        pipView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
                // View already removed, ignore
            }
        }
        pipView = null

        // Recreate full overlay
        createOverlay()

        // Restore script content (convert markdown to HTML)
        val htmlContent = convertMarkdownToHtml(currentScriptContent ?: "")
        scriptTextView?.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(htmlContent, Html.FROM_HTML_MODE_COMPACT)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(htmlContent)
        }

        Log.d("TeleprompterService", "Exited PIP mode")
    }

    /**
     * Convert markdown-style formatting to HTML for display
     */
    private fun convertMarkdownToHtml(text: String): String {
        var html = text

        // Bold: **text** -> <b>text</b>
        html = html.replace(Regex("""\*\*([^*]+?)\*\*"""), "<b>$1</b>")

        // Underline: __text__ -> <u>text</u> (process before single _)
        html = html.replace(Regex("""__([^_]+?)__"""), "<u>$1</u>")

        // Italic: _text_ -> <i>text</i> (single _ only)
        html = html.replace(Regex("""_([^_]+?)_"""), "<i>$1</i>")

        return html
    }

    override fun onDestroy() {
        super.onDestroy()

        // Disable orientation listener
        orientationEventListener?.disable()
        orientationEventListener = null
        pendingOrientationChange?.let { orientationChangeHandler.removeCallbacks(it) }
        orientationChangeHandler.removeCallbacksAndMessages(null)

        // Stop scrolling
        stopScrolling()

        // Stop speed change repeater
        stopSpeedChangeRepeater()
        speedChangeHandler.removeCallbacksAndMessages(null)

        // Clear speed overlay timer
        speedOverlayRunnable?.let { speedOverlayHandler.removeCallbacks(it) }
        speedOverlayHandler.removeCallbacksAndMessages(null)

        // Clear opacity panel timer
        opacityPanelHideRunnable?.let { opacityPanelHandler.removeCallbacks(it) }
        opacityPanelHandler.removeCallbacksAndMessages(null)

        // Remove overlay
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
                // View already removed, ignore
            }
        }
        overlayView = null

        // Remove PIP view if present
        pipView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
                // View already removed, ignore
            }
        }
        pipView = null

        // Stop foreground
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
}
