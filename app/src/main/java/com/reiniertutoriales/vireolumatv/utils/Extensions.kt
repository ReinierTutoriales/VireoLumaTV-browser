package com.reiniertutoriales.vireolumatv.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.animation.Animation
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** The Activity hosting this view, or null when it is not inside one. */
val View.activity: Activity?
    get() {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

/**
 * Opens the keyboard for this focused field after an explicit user action (OK on the field).
 * WindowInsetsController is the current API; outside an Activity window the input method
 * service is asked directly, without the deprecated SHOW_IMPLICIT flag.
 */
fun View.openKeyboard() {
    val window = activity?.window
    if (window != null) WindowCompat.getInsetsController(window, this).show(WindowInsetsCompat.Type.ime())
    else context.getSystemService<InputMethodManager>()?.showSoftInput(this, 0)
}

fun Int.dip2px(context: Context): Float = this * context.resources.displayMetrics.density

/** Runs [action] when the animation ends; start and repeat are ignored. */
fun Animation.onAnimationEnd(action: () -> Unit) {
    setAnimationListener(object : Animation.AnimationListener {
        override fun onAnimationStart(animation: Animation) = Unit
        override fun onAnimationEnd(animation: Animation) = action()
        override fun onAnimationRepeat(animation: Animation) = Unit
    })
}
