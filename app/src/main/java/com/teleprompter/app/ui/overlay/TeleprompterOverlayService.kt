package com.teleprompter.app.ui.overlay

import android.Manifest
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
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
    private var initialResizeTouchY = 0f
    private var initialHeight = 0

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

    override fun onCreate() {
        super.onCreate()

        // Check notification permission for Android 13+
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
                stopSelf()
                return
            }
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayPreferences = OverlayPreferences(this)

        // Create notification channel
        createNotificationChannel()

        // Must call startForeground within 5 seconds of service start
        startForeground(Constants.FOREGROUND_SERVICE_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        try {
            // Get script content from intent
            val scriptContent = intent?.getStringExtra(Constants.EXTRA_SCRIPT_CONTENT)
                ?: getString(R.string.default_teleprompter_text)

            // Create and show overlay
            createOverlay()

            // Set script text after overlay is created
            scriptTextView?.text = scriptContent

        } catch (e: Exception) {
            Toast.makeText(this, "Error showing overlay: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }

    /**
     * Create overlay window and setup UI
     */
    private fun createOverlay() {
        if (overlayView != null) return

        // Inflate layout
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_portrait, null)

        // Create layout params
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Load saved position and height synchronously
        val (savedX, savedY) = runBlocking {
            overlayPreferences.getPosition()
        }
        val savedHeight = runBlocking {
            overlayPreferences.getHeight()
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            savedHeight,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
        }

        // Add view to window
        windowManager.addView(overlayView, layoutParams)

        // Setup UI components
        setupViews()
    }

    /**
     * Setup overlay views and button listeners
     */
    private fun setupViews() {
        val view = overlayView ?: return

        // Find views
        scrollView = view.findViewById(R.id.scriptScrollView)
        scriptTextView = view.findViewById(R.id.scriptTextView)
        speedIndicator = view.findViewById(R.id.speedIndicator)
        val btnPlayPause = view.findViewById<ImageButton>(R.id.btnPlayPause)
        val btnSlower = view.findViewById<ImageButton>(R.id.btnSlower)
        val btnFaster = view.findViewById<ImageButton>(R.id.btnFaster)
        val btnMinimize = view.findViewById<ImageButton>(R.id.btnMinimize)

        // Setup button listeners
        btnPlayPause?.setOnClickListener {
            if (isScrolling) {
                stopScrolling()
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            } else {
                startScrolling()
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            }
        }

        // Setup slower button with hold functionality
        btnSlower?.setOnTouchListener { _, event ->
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

        // Setup faster button with hold functionality
        btnFaster?.setOnTouchListener { _, event ->
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
            stopSelf()
        }

        // Update speed indicator
        updateSpeedIndicator()
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

        // Wait for layout to be measured
        textView.post {
            // Force measure
            textView.measure(
                View.MeasureSpec.makeMeasureSpec(scroll.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )

            // Get the content height (child of ScrollView)
            val contentHeight = textView.measuredHeight
            val scrollViewHeight = scroll.height
            val fullHeight = contentHeight - scrollViewHeight

            Log.d("TeleprompterService", "contentHeight=$contentHeight, scrollViewHeight=$scrollViewHeight, fullHeight=$fullHeight")

            if (fullHeight <= 0) {
                Toast.makeText(this, "Text is too short to scroll", Toast.LENGTH_SHORT).show()
                return@post
            }

            val currentY = scroll.scrollY
            val remainingDistance = fullHeight - currentY

            if (remainingDistance <= 0) {
                Toast.makeText(this, "Already at the end", Toast.LENGTH_SHORT).show()
                return@post
            }

            // Calculate duration based on speed (higher speed = faster = less duration)
            // Formula: duration = distance * (1000 / speed)
            // Speed 1: very slow (1000ms per pixel)
            // Speed 500: very fast (2ms per pixel)
            val duration = (remainingDistance * 1000 / scrollSpeed).toLong()

            Log.d("TeleprompterService", "Starting scroll from $currentY to $fullHeight, duration=$duration ms")

            scrollAnimator = ValueAnimator.ofInt(currentY, fullHeight).apply {
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
            val wasScrolling = isScrolling
            stopScrolling()
            startScrolling()
            if (wasScrolling) {
                overlayView?.findViewById<ImageButton>(R.id.btnPlayPause)
                    ?.setImageResource(android.R.drawable.ic_media_pause)
            }
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
            val wasScrolling = isScrolling
            stopScrolling()
            startScrolling()
            if (wasScrolling) {
                overlayView?.findViewById<ImageButton>(R.id.btnPlayPause)
                    ?.setImageResource(android.R.drawable.ic_media_pause)
            }
        }
    }

    /**
     * Update speed indicator text
     */
    private fun updateSpeedIndicator() {
        // Display speed level (1-500)
        speedIndicator?.text = "Speed: $scrollSpeed"
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
     * Handle touch events for resizing overlay
     */
    private fun handleResizeTouch(event: MotionEvent): Boolean {
        val params = layoutParams ?: return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isResizing = true
                initialResizeTouchY = event.rawY
                initialHeight = params.height
                Log.d("TeleprompterService", "Started resizing from height: $initialHeight")
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isResizing) {
                    val deltaY = (event.rawY - initialResizeTouchY).toInt()
                    val newHeight = initialHeight + deltaY

                    // Get screen dimensions
                    val displayMetrics = resources.displayMetrics
                    val screenHeight = displayMetrics.heightPixels

                    // Set min/max constraints (100dp to screen height)
                    val minHeight = (100 * displayMetrics.density).toInt()
                    val maxHeight = screenHeight

                    // Constrain height
                    params.height = newHeight.coerceIn(minHeight, maxHeight)

                    // Update view layout
                    overlayView?.let { windowManager.updateViewLayout(it, params) }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isResizing) {
                    isResizing = false

                    // Save height to preferences
                    lifecycleScope.launch {
                        overlayPreferences.saveOverlayHeight(params.height)
                        Log.d("TeleprompterService", "Saved overlay height: ${params.height}")
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
        val channel = NotificationChannel(
            "teleprompter_overlay",
            "Teleprompter Overlay",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps teleprompter overlay active"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
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
            .setContentTitle("TelePrompt One Pro")
            .setContentText("Суфлёр активен поверх других приложений")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Stop scrolling
        stopScrolling()

        // Stop speed change repeater
        stopSpeedChangeRepeater()
        speedChangeHandler.removeCallbacksAndMessages(null)

        // Remove overlay
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                // View already removed, ignore
            }
        }
        overlayView = null

        // Stop foreground
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
}
