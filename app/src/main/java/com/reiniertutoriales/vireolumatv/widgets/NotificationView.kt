package com.reiniertutoriales.vireolumatv.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.annotation.DrawableRes
import com.reiniertutoriales.vireolumatv.databinding.ViewNotificationBinding
import java.lang.ref.WeakReference

open class NotificationView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    private var vb: ViewNotificationBinding
    private val dismissRunnable = Runnable { animateDisappearing() }

    companion object {
        const val DISAPPEARING_DELAY = 3000L
        const val APPEARING_DURATION = 180L
        const val DISAPPEARING_DURATION = 180L

        private var lastView: WeakReference<NotificationView>? = null

        fun showBottomRight(parent: RelativeLayout, @DrawableRes icon: Int, message: String): NotificationView {
            val view = NotificationView(parent.context)
            view.setIcon(icon)
            view.setMessage(message)
            val lp = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            parent.addView(view, lp)
            lastView?.get()?.let { previous ->
                previous.removeCallbacks(previous.dismissRunnable)
                previous.animate().cancel()
                (previous.parent as? ViewGroup)?.removeView(previous)
            }
            lastView = WeakReference(view)
            view.animateAppearing()
            return view
        }
    }

    init {
        vb = ViewNotificationBinding.inflate(LayoutInflater.from(context), this, true)
    }

    private fun animateDisappearing() {
        animate().cancel()
        animate().alpha(0f).translationY(height.toFloat()).setDuration(DISAPPEARING_DURATION).withEndAction {
            val parent = parent
            if (parent != null && parent is ViewGroup) {
                parent.removeView(this)
            }
            if (lastView?.get() === this) lastView = null
        }.also { it.start() }
    }

    fun setMessage(text: String) {
        vb.tvMessage.text = text
    }

    fun setIcon(@DrawableRes icon: Int) {
        vb.ivIcon.setImageResource(icon)
    }

    fun animateAppearing() {
        alpha = 0f
        animate().cancel()
        animate().alpha(1.0f).setDuration(APPEARING_DURATION).withEndAction {
            alpha = 1f
            translationY = 0f
            postDelayed(dismissRunnable, DISAPPEARING_DELAY)
        }.also { it.start() }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(dismissRunnable)
        animate().cancel()
        if (lastView?.get() === this) lastView = null
        super.onDetachedFromWindow()
    }
}
