package com.reiniertutoriales.vireolumatv.utils

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.metrics.performance.JankStats
import java.text.NumberFormat
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Counts slow frames while any screen of this process is in front, with AndroidX JankStats (a frame
 * is slow when it takes more than twice the display's frame time). The totals are shown in
 * Settings, so smoothness on a real TV is measured rather than guessed. Nothing leaves the device.
 */
object FrameStats : Application.ActivityLifecycleCallbacks {
    private val frames = AtomicLong()
    private val slow = AtomicLong()
    private val trackers = WeakHashMap<Activity, JankStats>()

    fun install(app: Application) = app.registerActivityLifecycleCallbacks(this)

    /** "1.8 % · 34/1892", or null before the first frame is measured. */
    fun summary(locale: Locale): String? {
        val total = frames.get()
        if (total == 0L) return null
        val late = slow.get()
        val percent = NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 1 }
            .format(late.toDouble() / total)
        return "$percent · $late/$total"
    }

    internal fun record(isJank: Boolean) {
        frames.incrementAndGet()
        if (isJank) slow.incrementAndGet()
    }

    // Tracking starts once a screen is shown, when its window has its views, and stops while paused.
    override fun onActivityResumed(activity: Activity) {
        trackers.getOrPut(activity) { JankStats.createAndTrack(activity.window) { record(it.isJank) } }
            .isTrackingEnabled = true
    }

    override fun onActivityPaused(activity: Activity) {
        trackers[activity]?.isTrackingEnabled = false
    }

    override fun onActivityDestroyed(activity: Activity) {
        trackers.remove(activity)?.isTrackingEnabled = false
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
