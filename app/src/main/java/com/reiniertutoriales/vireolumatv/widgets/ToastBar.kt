package com.reiniertutoriales.vireolumatv.widgets

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvSymbol
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvSymbolIcon
import com.reiniertutoriales.vireolumatv.activity.main.compose.VireoTvTheme
import java.lang.ref.WeakReference

/**
 * A message that fades in at the bottom end of the screen and leaves after [VISIBLE_MS]. A new
 * one replaces the one on screen. It is announced to accessibility services but never focused.
 */
class ToastBar private constructor(context: Context, val symbol: TvSymbol, val message: String) : FrameLayout(context) {
    private val leave = Runnable { fadeOut() }

    init {
        isFocusable = false
        addView(ComposeView(context).apply {
            isFocusable = false
            setContent {
                VireoTvTheme {
                    val shape = RoundedCornerShape(dimensionResource(R.dimen.corner_large))
                    Row(
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            .background(colorResource(R.color.surface_high), shape)
                            .border(1.dp, colorResource(R.color.outline), shape)
                            .padding(horizontal = dimensionResource(R.dimen.notification_padding_horizontal),
                                vertical = dimensionResource(R.dimen.notification_padding_vertical)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TvSymbolIcon(symbol, Modifier.size(24.dp), tint = colorResource(R.color.brand))
                        Text(message, color = colorResource(R.color.on_surface), fontSize = 18.sp, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 360.dp))
                    }
                }
            }
        })
    }

    private fun show() {
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
        private var current: WeakReference<ToastBar>? = null

        /** Shows [message] in the bottom end corner of [parent], replacing any message on screen. */
        fun showBottomRight(parent: RelativeLayout, symbol: TvSymbol, message: String): ToastBar {
            current?.get()?.remove()
            val view = ToastBar(parent.context, symbol, message)
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
            view.show()
            return view
        }
    }
}
