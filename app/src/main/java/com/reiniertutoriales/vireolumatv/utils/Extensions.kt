package com.reiniertutoriales.vireolumatv.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.animation.Animation

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

fun Int.dip2px(context: Context): Float = this * context.resources.displayMetrics.density

/** Runs [action] when the animation ends; start and repeat are ignored. */
fun Animation.onAnimationEnd(action: () -> Unit) {
    setAnimationListener(object : Animation.AnimationListener {
        override fun onAnimationStart(animation: Animation) = Unit
        override fun onAnimationEnd(animation: Animation) = action()
        override fun onAnimationRepeat(animation: Animation) = Unit
    })
}
