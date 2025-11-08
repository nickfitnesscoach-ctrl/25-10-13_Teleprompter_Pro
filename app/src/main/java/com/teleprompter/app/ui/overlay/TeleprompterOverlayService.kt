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
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.teleprompter.app.R
import com.teleprompter.app.data.preferences.OverlayPreferences
import com.teleprompter.app.ui.main.MainActivity
import com.teleprompter.app.utils.Constants
import kotlinx.coroutines.launch

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

    // Animation duration bounds (milliseconds)
    private val MIN_ANIMATION_DURATION = 1000L // 1 second minimum
    private val MAX_ANIMATION_DURATION = 300000L // 5 minutes maximum

    // Speed control with hold functionality
    private val speedChangeHandler = Handler(Looper.getMainLooper())
    private var speedChangeRunnable: Runnable? = null
    private var isHoldingButton = false
    private var isChangingSpeed = false // Prevent race conditions during speed changes

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
    private var savedScrollPosition: Int = 0 // Save scroll position when entering PIP mode

    override fun onCreate() {
        super.onCreate()

        // Check notification permission for Android 13+
        // CRITICAL: Must check before calling startForeground() to avoid crash
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
                // Stop service immediately without proceeding to startForeground()
                stopSelf()
                return
            }
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
    private fun getDisplayRotation(): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Use display from context for Android R+
                display?.rotation ?: Surface.ROTATION_0
            } else {
                // Use deprecated API for older Android versions
                @Suppress("DEPRECATION")
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
            Log.e("TeleprompterService", "Failed to start overlay service", e)
            // Show error notification with full context
            showErrorNotification("Failed to start teleprompter: ${e.localizedMessage ?: "Unknown error"}")
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
        // Don't recreate if overlay already exists
        if (overlayView != null) {
            Log.d("TeleprompterService", "createOverlay() skipped - overlay already exists")
            return
        }

        Log.d("TeleprompterService", "createOverlay() - creating new overlay")

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

        // Use default values initially, will be updated asynchronously after overlay is created
        var savedX = OverlayPreferences.DEFAULT_X
        var savedY = OverlayPreferences.DEFAULT_Y
        var savedWidth = OverlayPreferences.DEFAULT_WIDTH
        var savedHeight = OverlayPreferences.DEFAULT_HEIGHT

        // Load saved position and size asynchronously and update overlay
        lifecycleScope.launch {
            val (loadedX, loadedY) = overlayPreferences.getPosition()
            val (loadedWidth, loadedHeight) = overlayPreferences.getSize()

            // Update layout params with loaded values
            layoutParams?.let { params ->
                // Validate position against current screen dimensions to prevent off-screen overlay
                val screenWidth = resources.displayMetrics.widthPixels
                val screenHeight = resources.displayMetrics.heightPixels

                params.width = if (loadedWidth == -1) WindowManager.LayoutParams.MATCH_PARENT else loadedWidth
                params.height = loadedHeight

                // Clamp X and Y coordinates to ensure overlay stays on screen
                params.x = loadedX.coerceIn(0, (screenWidth - params.width).coerceAtLeast(0))
                params.y = loadedY.coerceIn(0, (screenHeight - params.height).coerceAtLeast(0))
                // Only update if view is attached to window manager
                overlayView?.let { view ->
                    if (view.isAttachedToWindow) {
                        try {
                            windowManager.updateViewLayout(view, params)
                        } catch (e: Exception) {
                            Log.e("TeleprompterService", "Error updating view layout", e)
                        }
                    }
                }
            }
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
            // Validate initial position to ensure overlay stays on screen
            x = savedX.coerceIn(0, (screenWidth - finalWidth).coerceAtLeast(0))
            y = savedY.coerceIn(0, (screenHeight - finalHeight).coerceAtLeast(0))
        }

        // Add view to window
        windowManager.addView(overlayView, layoutParams)

        // Setup UI components
        setupViews()
    }

    /**
     * Expand touch area of a view for better touch responsiveness
     * @param view The view to expand touch area for
     * @param extraDp Additional touch area in dp on all sides
     */
    private fun expandTouchArea(view: View, extraDp: Int) {
        val parent = view.parent as? View ?: return
        val extraPx = (extraDp * resources.displayMetrics.density).toInt()

        parent.post {
            val rect = android.graphics.Rect()
            view.getHitRect(rect)

            // Calculate safe expansion - limit to prevent overlap with other buttons
            // Check available space in parent to avoid expanding beyond parent bounds
            val parentWidth = parent.width
            val parentHeight = parent.height

            // Limit expansion to not exceed parent boundaries or overlap with adjacent views
            val safeExtraLeft = minOf(extraPx, rect.left)
            val safeExtraTop = minOf(extraPx, rect.top)
            val safeExtraRight = minOf(extraPx, parentWidth - rect.right)
            val safeExtraBottom = minOf(extraPx, parentHeight - rect.bottom)

            rect.top -= safeExtraTop
            rect.left -= safeExtraLeft
            rect.bottom += safeExtraBottom
            rect.right += safeExtraRight

            parent.touchDelegate = android.view.TouchDelegate(rect, view)
            Log.d("TeleprompterService", "Expanded touch area for ${view.id} by safe margins: L=$safeExtraLeft T=$safeExtraTop R=$safeExtraRight B=$safeExtraBottom")
        }
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
        // Use individual listeners to prevent double-execution during layout transitions
        btnPlayPause?.setOnClickListener {
            // Only execute if this button is visible
            if (btnPlayPause.visibility == View.VISIBLE) {
                if (isScrolling) {
                    stopScrolling()
                    btnPlayPause.setImageResource(R.drawable.ic_play)
                    btnPlayPauseTop?.setImageResource(R.drawable.ic_play)
                } else {
                    startScrolling()
                    btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                    btnPlayPauseTop?.setImageResource(R.drawable.ic_pause)
                }
            }
        }

        btnPlayPauseTop?.setOnClickListener {
            // Only execute if this button is visible
            if (btnPlayPauseTop.visibility == View.VISIBLE) {
                if (isScrolling) {
                    stopScrolling()
                    btnPlayPause?.setImageResource(R.drawable.ic_play)
                    btnPlayPauseTop.setImageResource(R.drawable.ic_play)
                } else {
                    startScrolling()
                    btnPlayPause?.setImageResource(android.R.drawable.ic_media_pause)
                    btnPlayPauseTop.setImageResource(R.drawable.ic_pause)
                }
            }
        }

        // Setup slower button with hold functionality
        btnSlower?.setOnTouchListener { _, event ->
            // Only execute if this button is visible
            if (btnSlower.visibility == View.VISIBLE) {
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
            } else {
                false
            }
        }

        btnSlowerTop?.setOnTouchListener { _, event ->
            // Only execute if this button is visible
            if (btnSlowerTop.visibility == View.VISIBLE) {
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
            } else {
                false
            }
        }

        // Setup faster button with hold functionality
        btnFaster?.setOnTouchListener { _, event ->
            // Only execute if this button is visible
            if (btnFaster.visibility == View.VISIBLE) {
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
            } else {
                false
            }
        }

        btnFasterTop?.setOnTouchListener { _, event ->
            // Only execute if this button is visible
            if (btnFasterTop.visibility == View.VISIBLE) {
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
            } else {
                false
            }
        }

        // Setup drag button with improved touch handling and expanded touch area
        val btnDrag = view.findViewById<ImageButton>(R.id.btnDrag)
        btnDrag?.let { button ->
            // Expand touch area by 12dp on all sides for better responsiveness
            expandTouchArea(button, 12)

            button.setOnTouchListener { v, event ->
                // Request parent not to intercept touch events
                v.parent.requestDisallowInterceptTouchEvent(true)
                handleDragTouch(event)
            }
        }

        // Setup resize button
        val btnResize = view.findViewById<ImageButton>(R.id.btnResize)
        btnResize?.setOnTouchListener { _, event ->
            handleResizeTouch(event)
        }

        btnMinimize?.setOnClickListener {
            Log.d("TeleprompterService", "Close button clicked - returning to MainActivity")

            // Return to main activity (script list)
            val intent = Intent(this, com.teleprompter.app.ui.main.MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }

            try {
                startActivity(intent)
                Log.d("TeleprompterService", "MainActivity started successfully")
            } catch (e: Exception) {
                Log.e("TeleprompterService", "Error starting MainActivity", e)
            }

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

        // Load saved text size and font family
        lifecycleScope.launch {
            currentTextSize = overlayPreferences.getTextSize()
            textView.textSize = currentTextSize

            // Apply saved font family
            val fontFamily = overlayPreferences.getFontFamily()
            applyFontFamily(fontFamily)
        }

        // Observe font family changes in real-time
        lifecycleScope.launch {
            overlayPreferences.fontFamilyFlow.collect { fontFamily ->
                applyFontFamily(fontFamily)
                Log.d("TeleprompterService", "Font family changed to: $fontFamily")
            }
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
     * Apply font family to TextView
     */
    private fun applyFontFamily(fontFamilyName: String) {
        val textView = scriptTextView ?: return
        val typeface = when (fontFamilyName.lowercase()) {
            "bebas_neue" -> {
                try {
                    resources.getFont(R.font.bebas_neue)
                } catch (e: Exception) {
                    Log.e("TeleprompterService", "Error loading Bebas Neue font: ${e.message}")
                    Typeface.DEFAULT
                }
            }
            "comfortaa_regular" -> {
                try {
                    resources.getFont(R.font.comfortaa_regular)
                } catch (e: Exception) {
                    Log.e("TeleprompterService", "Error loading Comfortaa font: ${e.message}")
                    Typeface.DEFAULT
                }
            }
            "drukcyr_bold" -> {
                try {
                    resources.getFont(R.font.drukcyr_bold)
                } catch (e: Exception) {
                    Log.e("TeleprompterService", "Error loading Druk Cyr Bold font: ${e.message}")
                    Typeface.DEFAULT
                }
            }
            "montserrat_regular" -> {
                try {
                    resources.getFont(R.font.montserrat_regular)
                } catch (e: Exception) {
                    Log.e("TeleprompterService", "Error loading Montserrat font: ${e.message}")
                    Typeface.DEFAULT
                }
            }
            "opensans_regular" -> {
                try {
                    resources.getFont(R.font.opensans_regular)
                } catch (e: Exception) {
                    Log.e("TeleprompterService", "Error loading Open Sans font: ${e.message}")
                    Typeface.DEFAULT
                }
            }
            "oswald_regular" -> {
                try {
                    resources.getFont(R.font.oswald_regular)
                } catch (e: Exception) {
                    Log.e("TeleprompterService", "Error loading Oswald font: ${e.message}")
                    Typeface.DEFAULT
                }
            }
            "ptsans_regular" -> {
                try {
                    resources.getFont(R.font.ptsans_regular)
                } catch (e: Exception) {
                    Log.e("TeleprompterService", "Error loading PT Sans font: ${e.message}")
                    Typeface.DEFAULT
                }
            }
            "raleway_regular" -> {
                try {
                    resources.getFont(R.font.raleway_regular)
                } catch (e: Exception) {
                    Log.e("TeleprompterService", "Error loading Raleway font: ${e.message}")
                    Typeface.DEFAULT
                }
            }
            "roboto_regular" -> {
                try {
                    resources.getFont(R.font.roboto_regular)
                } catch (e: Exception) {
                    Log.e("TeleprompterService", "Error loading Roboto font: ${e.message}")
                    Typeface.DEFAULT
                }
            }
            "ubuntu_regular" -> {
                try {
                    resources.getFont(R.font.ubuntu_regular)
                } catch (e: Exception) {
                    Log.e("TeleprompterService", "Error loading Ubuntu font: ${e.message}")
                    Typeface.DEFAULT
                }
            }
            else -> Typeface.DEFAULT
        }
        textView.typeface = typeface
        Log.d("TeleprompterService", "Applied font family: $fontFamilyName")
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

        // Add TOP and BOTTOM padding so text starts at bottom and can scroll all the way to top
        textView.setPadding(
            textView.paddingLeft,
            scrollViewHeight, // Add top padding equal to scroll view height (text starts at bottom)
            textView.paddingRight,
            scrollViewHeight  // Add bottom padding equal to scroll view height (text can reach top)
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

            // Check if there's enough content to scroll
            if (maxScroll <= 0) {
                Log.d("TeleprompterService", "Text is too short to scroll (maxScroll=$maxScroll)")
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
                // Restart from beginning instead of showing error message
                scroll.scrollTo(0, 0)
                startScrolling()
                // Update play button icon to show pause state since we're scrolling
                overlayView?.let { view ->
                    view.findViewById<ImageButton>(R.id.btnPlayPause)
                        ?.setImageResource(android.R.drawable.ic_media_pause)
                }
                return@post
            }

            val duration = (remainingDistance * 1000 / scrollSpeed).toLong()
                .coerceIn(MIN_ANIMATION_DURATION, MAX_ANIMATION_DURATION)

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
     * Helper function to start scrolling with specific distance
     */
    private fun startScrollingWithDistance(scroll: ScrollView, fromY: Int, toY: Int) {
        val duration = ((toY - fromY) * 1000 / scrollSpeed).toLong()
            .coerceIn(MIN_ANIMATION_DURATION, MAX_ANIMATION_DURATION)

        Log.d("TeleprompterService", "Scrolling from $fromY to $toY, duration=$duration ms")

        scrollAnimator = ValueAnimator.ofInt(fromY, toY).apply {
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
        // Prevent race conditions during speed changes
        if (isChangingSpeed) return
        isChangingSpeed = true

        try {
            // Higher speed number = faster
            // Increase by 1 for very smooth increment
            scrollSpeed = (scrollSpeed + 1).coerceAtMost(500)
            updateSpeedIndicator()

            // Restart scrolling with new speed if currently scrolling
            if (isScrolling) {
                stopScrolling()
                // Add small delay to ensure animator is fully cancelled before restarting
                Handler(Looper.getMainLooper()).postDelayed({
                    startScrolling()
                    // Update play/pause button icon only if overlayView is still available
                    overlayView?.let { view ->
                        view.findViewById<ImageButton>(R.id.btnPlayPause)
                            ?.setImageResource(android.R.drawable.ic_media_pause)
                    }
                }, 50) // 50ms delay to ensure smooth transition
            }
        } finally {
            // Delay clearing the flag to prevent rapid successive calls
            Handler(Looper.getMainLooper()).postDelayed({
                isChangingSpeed = false
            }, 100)
        }
    }

    /**
     * Decrease scroll speed (make it slower)
     */
    private fun decreaseSpeed() {
        // Prevent race conditions during speed changes
        if (isChangingSpeed) return
        isChangingSpeed = true

        try {
            // Lower speed number = slower
            // Decrease by 1 for very smooth increment
            scrollSpeed = (scrollSpeed - 1).coerceAtLeast(1)
            updateSpeedIndicator()

            // Restart scrolling with new speed if currently scrolling
            if (isScrolling) {
                stopScrolling()
                // Add small delay to ensure animator is fully cancelled before restarting
                Handler(Looper.getMainLooper()).postDelayed({
                    startScrolling()
                    // Update play/pause button icon only if overlayView is still available
                    overlayView?.let { view ->
                        view.findViewById<ImageButton>(R.id.btnPlayPause)
                            ?.setImageResource(android.R.drawable.ic_media_pause)
                    }
                }, 50) // 50ms delay to ensure smooth transition
            }
        } finally {
            // Delay clearing the flag to prevent rapid successive calls
            Handler(Looper.getMainLooper()).postDelayed({
                isChangingSpeed = false
            }, 100)
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
     * Handle drag touch events with improved sensitivity and response
     */
    private fun handleDragTouch(event: MotionEvent): Boolean {
        val params = layoutParams ?: return false

        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY

                Log.d("TeleprompterService", "Drag started at: rawX=${event.rawX}, rawY=${event.rawY}")
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    // Calculate movement delta with higher precision
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

                    // Constrain to screen bounds with more lenient boundaries
                    params.x = newX.coerceIn(-overlayWidth / 2, screenWidth - overlayWidth / 2)
                    params.y = newY.coerceIn(0, screenHeight - overlayHeight)

                    // Update view layout immediately for smooth tracking
                    overlayView?.let {
                        try {
                            windowManager.updateViewLayout(it, params)
                        } catch (e: Exception) {
                            Log.e("TeleprompterService", "Error updating view layout during drag", e)
                        }
                    }
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

            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                // Ignore multi-touch gestures for dragging
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
     * Show error notification to user
     */
    private fun showErrorNotification(message: String) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, "teleprompter_overlay")
            .setContentTitle("Teleprompter Error")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(Constants.FOREGROUND_SERVICE_ID + 1, notification)
    }

    /**
     * Enter Picture-in-Picture mode - minimize to small circular icon
     */
    private fun enterPipMode() {
        if (isPipMode) return

        Log.d("TeleprompterService", "enterPipMode() - entering PIP mode")

        isPipMode = true

        // Save current scroll position before removing overlay
        scrollView?.let { scroll ->
            savedScrollPosition = scroll.scrollY
            Log.d("TeleprompterService", "Saved scroll position: $savedScrollPosition")
        }

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
        overlayView = null
        Log.d("TeleprompterService", "enterPipMode() - overlayView set to null")

        // Create PIP view
        val inflater = LayoutInflater.from(this)
        @SuppressLint("InflateParams")
        val view = inflater.inflate(R.layout.overlay_pip, null)
        pipView = view

        // Setup PIP layout params - standard app icon size
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
     * Setup interactions for PIP mode with improved touch handling
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

        pipContainer.setOnTouchListener { v, event ->
            // Request parent not to intercept touch events
            v.parent?.requestDisallowInterceptTouchEvent(true)

            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = true
                    hasMoved = false
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    Log.d("TeleprompterService", "PIP drag started at: rawX=${event.rawX}, rawY=${event.rawY}")
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        val deltaX = (initialTouchX - event.rawX).toInt()
                        val deltaY = (event.rawY - initialTouchY).toInt()

                        // Check if actually moved (reduced threshold for better responsiveness)
                        if (kotlin.math.abs(deltaX) > 5 || kotlin.math.abs(deltaY) > 5) {
                            hasMoved = true
                        }

                        params.x = initialX + deltaX
                        params.y = initialY + deltaY

                        // Update view layout immediately for smooth tracking
                        pipView?.let {
                            try {
                                windowManager.updateViewLayout(it, params)
                            } catch (e: Exception) {
                                Log.e("TeleprompterService", "Error updating PIP view layout during drag", e)
                            }
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging && !hasMoved) {
                        // Tap detected - exit PIP mode
                        Log.d("TeleprompterService", "PIP tap detected, exiting PIP mode")
                        exitPipMode()
                    }
                    isDragging = false
                    true
                }

                MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                    // Ignore multi-touch gestures for PIP
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

        Log.d("TeleprompterService", "exitPipMode() - exiting PIP mode, overlayView is null: ${overlayView == null}")

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

        // Restore scroll position after content is set
        scrollView?.post {
            scrollView?.scrollTo(0, savedScrollPosition)
            Log.d("TeleprompterService", "Restored scroll position: $savedScrollPosition")
        }

        Log.d("TeleprompterService", "Exited PIP mode")
    }

    /**
     * Convert markdown-style formatting to HTML for display
     */
    private fun convertMarkdownToHtml(text: String): String {
        var html = text

        // First, replace escaped characters with placeholders
        val escapeMap = mutableMapOf<String, String>()
        var escapeCounter = 0

        // Handle escaped markdown characters: \** -> **, \__ -> __, \_ -> _
        html = html.replace(Regex("""\\(\*\*|__|_)""")) { matchResult ->
            val placeholder = "___ESCAPE_${escapeCounter++}___"
            escapeMap[placeholder] = matchResult.groupValues[1]
            placeholder
        }

        // Bold: **text** -> <b>text</b>
        html = html.replace(Regex("""\*\*([^*]+?)\*\*"""), "<b>$1</b>")

        // Underline: __text__ -> <u>text</u> (process before single _)
        html = html.replace(Regex("""__([^_]+?)__"""), "<u>$1</u>")

        // Italic: _text_ -> <i>text</i> (single _ only)
        html = html.replace(Regex("""_([^_]+?)_"""), "<i>$1</i>")

        // Restore escaped characters
        escapeMap.forEach { (placeholder, original) ->
            html = html.replace(placeholder, original)
        }

        return html
    }

    override fun onDestroy() {
        super.onDestroy()

        // Disable orientation listener FIRST to prevent further callbacks
        orientationEventListener?.let { listener ->
            try {
                listener.disable()
            } catch (e: Exception) {
                Log.e("TeleprompterService", "Error disabling orientation listener", e)
            }
        }

        // Remove all pending orientation callbacks before nulling the listener
        pendingOrientationChange?.let { orientationChangeHandler.removeCallbacks(it) }
        pendingOrientationChange = null
        orientationChangeHandler.removeCallbacksAndMessages(null)

        // Now safe to null the listener after all callbacks are removed
        orientationEventListener = null

        // Stop scrolling
        stopScrolling()

        // Stop speed change repeater and clear all callbacks
        stopSpeedChangeRepeater()
        speedChangeHandler.removeCallbacksAndMessages(null)

        // Clear speed overlay timer and callbacks
        speedOverlayRunnable?.let { speedOverlayHandler.removeCallbacks(it) }
        speedOverlayRunnable = null
        speedOverlayHandler.removeCallbacksAndMessages(null)

        // Clear opacity panel timer and callbacks
        opacityPanelHideRunnable?.let { opacityPanelHandler.removeCallbacks(it) }
        opacityPanelHideRunnable = null
        opacityPanelHandler.removeCallbacksAndMessages(null)

        // Remove overlay view
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
