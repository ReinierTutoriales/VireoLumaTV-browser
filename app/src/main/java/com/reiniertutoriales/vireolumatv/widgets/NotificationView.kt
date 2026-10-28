package com.reiniertutoriales.vireolumatv.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.annotation.DrawableRes
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.databinding.ViewNotificationBinding
import java.lang.ref.WeakReference

/**
 * A message that fades in at the bottom end of the screen and leaves after [VISIBLE_MS]. A new
 * one replaces the one on screen. It is announced to accessibility services but never focused.
 */
class NotificationView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {

    private val vb = ViewNotificationBinding.inflate(LayoutInflater.from(context), this)
    private val leave = Runnable { fadeOut() }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = context.getDrawable(R.drawable.bg_toast)
        val horizontal = resources.getDimensionPixelSize(R.dimen.notification_padding_horizontal)
        val vertical = resources.getDimensionPixelSize(R.dimen.notification_padding_vertical)
        setPadding(horizontal, vertical, horizontal, vertical)
        isFocusable = false
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }

    private fun show(@DrawableRes icon: Int, message: String) {
        vb.ivIcon.setImageResource(icon)
        vb.tvMessage.text = message
        alpha = 0f
        animate().alpha(1f).setDuration(FADE_MS).withEndAction { postDelayed(leave, VISIBLE_MS) }.start()
    }

    private fun fadeOut() {
        animate().cancel()
        animate().alpha(0f).setDuration(FADE_MS).withEndAction { remove() }.start()
    }

    private fun remove() {
        removeCallbacks(leave)
        animate().cancel()
        (parent as? ViewGroup)?.removeView(this)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(leave)
        animate().cancel()
        if (current?.get() === this) current = null
        super.onDetachedFromWindow()
    }

    companion object {
        const val VISIBLE_MS = 3000L
        private const val FADE_MS = 180L
        private var current: WeakReference<NotificationView>? = null

        /** Shows [message] in the bottom end corner of [parent], replacing any message on screen. */
        fun showBottomRight(parent: RelativeLayout, @DrawableRes icon: Int, message: String): NotificationView {
            current?.get()?.remove()
            val view = NotificationView(parent.context)
            val end = parent.resources.getDimensionPixelSize(R.dimen.notification_margin_end)
            val bottom = parent.resources.getDimensionPixelSize(R.dimen.notification_margin_bottom)
            val params = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.ALIGN_PARENT_END)
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                marginEnd = end
                bottomMargin = bottom
            }
            parent.addView(view, params)
            current = WeakReference(view)
            view.show(icon, message)
            return view
        }
    }
}
