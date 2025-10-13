package com.teleprompter.app.core

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import android.widget.ScrollView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.teleprompter.app.utils.Constants
import com.teleprompter.app.utils.clamp

/**
 * Controller for managing teleprompter scrolling behavior
 * Uses ValueAnimator for smooth, efficient scrolling
 * Lifecycle-aware to prevent memory leaks
 */
class ScrollController(
    private val scrollView: ScrollView,
    lifecycle: Lifecycle
) : DefaultLifecycleObserver {

    private var valueAnimator: ValueAnimator? = null
    private var isScrolling = false
    private var scrollSpeed = Constants.DEFAULT_SCROLL_SPEED // pixels per second

    // Callback for speed changes
    var onSpeedChanged: ((Int) -> Unit)? = null

    init {
        lifecycle.addObserver(this)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        cleanup()
        super.onDestroy(owner)
    }

    /**
     * Start auto-scrolling
     */
    fun start() {
        if (isScrolling) return
        isScrolling = true
        startScrolling()
    }

    /**
     * Pause auto-scrolling
     */
    fun pause() {
        isScrolling = false
        valueAnimator?.cancel()
    }

    /**
     * Toggle play/pause
     * @return true if now playing, false if paused
     */
    fun togglePlayPause(): Boolean {
        if (isScrolling) {
            pause()
        } else {
            start()
        }
        return isScrolling
    }

    /**
     * Increase scroll speed
     */
    fun increaseSpeed() {
        val newSpeed = (scrollSpeed + Constants.SCROLL_SPEED_STEP)
            .clamp(Constants.MIN_SCROLL_SPEED, Constants.MAX_SCROLL_SPEED)
        setSpeed(newSpeed)
    }

    /**
     * Decrease scroll speed
     */
    fun decreaseSpeed() {
        val newSpeed = (scrollSpeed - Constants.SCROLL_SPEED_STEP)
            .clamp(Constants.MIN_SCROLL_SPEED, Constants.MAX_SCROLL_SPEED)
        setSpeed(newSpeed)
    }

    /**
     * Set scroll speed directly
     * If currently scrolling, restart with new speed
     */
    fun setSpeed(speed: Int) {
        val wasScrolling = isScrolling
        if (wasScrolling) {
            pause()
        }

        scrollSpeed = speed.clamp(Constants.MIN_SCROLL_SPEED, Constants.MAX_SCROLL_SPEED)
        onSpeedChanged?.invoke(scrollSpeed)

        if (wasScrolling) {
            start()
        }
    }

    /**
     * Get current scroll speed
     */
    fun getSpeed(): Int = scrollSpeed

    /**
     * Check if currently scrolling
     */
    fun isPlaying(): Boolean = isScrolling

    /**
     * Get current scroll position
     */
    fun getCurrentPosition(): Int = scrollView.scrollY

    /**
     * Reset scroll position to top
     */
    fun resetPosition() {
        scrollView.smoothScrollTo(0, 0)
    }

    /**
     * Scroll to specific position
     */
    fun scrollToPosition(y: Int) {
        scrollView.scrollTo(0, y)
    }

    /**
     * Start scrolling using ValueAnimator for smooth animation
     */
    private fun startScrolling() {
        val currentY = scrollView.scrollY
        val maxScroll = scrollView.getChildAt(0)?.height?.minus(scrollView.height) ?: 0

        // If already at the end, don't start
        if (currentY >= maxScroll) {
            pause()
            return
        }

        val remainingDistance = maxScroll - currentY
        // Calculate duration based on speed: distance / speed * 1000 (to convert to ms)
        val duration = (remainingDistance.toFloat() / scrollSpeed * 1000).toLong()

        valueAnimator = ValueAnimator.ofInt(currentY, maxScroll).apply {
            this.duration = duration
            interpolator = LinearInterpolator()

            addUpdateListener { animator ->
                if (isScrolling) {
                    val value = animator.animatedValue as Int
                    scrollView.scrollTo(0, value)
                }
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isScrolling = false
                }

                override fun onAnimationCancel(animation: Animator) {
                    isScrolling = false
                }
            })

            start()
        }
    }

    /**
     * Clean up resources
     */
    private fun cleanup() {
        valueAnimator?.cancel()
        valueAnimator = null
        onSpeedChanged = null
        isScrolling = false
    }

    /**
     * Destroy and cleanup - called manually or by lifecycle
     */
    fun destroy() {
        cleanup()
    }
}
