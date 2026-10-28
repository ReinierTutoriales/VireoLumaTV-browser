package com.reiniertutoriales.vireolumatv.activity.main.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.databinding.ViewCursorMenuBinding
import com.reiniertutoriales.vireolumatv.model.WebTabState
import com.reiniertutoriales.vireolumatv.utils.BackNavigationEventsAdapter
import com.reiniertutoriales.vireolumatv.utils.dip2px
import com.reiniertutoriales.vireolumatv.webengine.PagePoint
import com.reiniertutoriales.vireolumatv.webengine.PageWindow
import com.reiniertutoriales.vireolumatv.widgets.cursor.CursorDrawerDelegate

/**
 * The menu a long press opens at the pointer: grab mode, link actions, D-pad mode and zoom. It
 * covers the page and places its panel around the pointer, kept on screen. Closing it, by a
 * command or by [close], returns focus to the page and shows the pointer again.
 */
class CursorMenuView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {

    /** What the menu was opened on: the tab and the point of its page under the pointer. */
    private class Target(
        val tab: WebTabState,
        val window: PageWindow,
        val cursor: CursorDrawerDelegate,
        val point: PagePoint,
        val back: BackNavigationEventsAdapter,
    )

    private val vb = ViewCursorMenuBinding.inflate(LayoutInflater.from(context), this, true)
    private val panel get() = vb.root
    private var target: Target? = null

    init {
        vb.btnGrabMode.setOnClickListener {
            target?.cursor?.goToGrabMode()
            close(grow = true)
        }
        vb.btnContextMenu.setOnClickListener {
            val t = target ?: return@setOnClickListener
            hide()
            t.window.openLinkActions(t.point)
        }
        vb.btnDPADMode.setOnClickListener {
            val t = target ?: return@setOnClickListener
            hide()
            if (AppContext.provideConfig().directNavigationModeHintSuppress) enterDirectNavigation(t) else explainDirectNavigation(t)
        }
        vb.btnZoomIn.setOnClickListener { target?.tab?.engine?.zoom(closer = true) }
        vb.btnZoomOut.setOnClickListener { target?.tab?.engine?.zoom(closer = false) }
    }

    fun show(tab: WebTabState, window: PageWindow, cursor: CursorDrawerDelegate, point: PagePoint, back: BackNavigationEventsAdapter) {
        val shown = Target(tab, window, cursor, point, back)
        target = shown
        panel.animate().cancel()
        isVisible = true
        panel.post {
            if (target !== shown || !isAttachedToWindow) return@post
            panel.translationX = (point.x - panel.width / 2f).coerceIn(0f, (width - panel.width).coerceAtLeast(0).toFloat())
            panel.translationY = (point.y - panel.height / 2f).coerceIn(0f, (height - panel.height).coerceAtLeast(0).toFloat())
            vb.btnGrabMode.requestFocus()
            cursor.hideCursor()
        }
        panel.alpha = 0f
        panel.scaleX = OPEN_SCALE
        panel.scaleY = OPEN_SCALE
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(OPEN_MS).start()
    }

    /** Fades the menu out; [grow] makes it expand as it goes, for grab mode. */
    fun close(grow: Boolean = false) {
        if (target == null) return
        val scale = if (grow) GROW_SCALE else 1f
        panel.animate().cancel()
        panel.animate().alpha(0f).scaleX(scale).scaleY(scale).setDuration(CLOSE_MS).withEndAction { hide() }.start()
    }

    /** Hides at once, before a dialog opens, so a key meant for the dialog cannot reach the menu. */
    private fun hide(restoreFocus: Boolean = true) {
        val closed = target
        target = null
        panel.animate().cancel()
        isVisible = false
        panel.alpha = 1f
        panel.scaleX = 1f
        panel.scaleY = 1f
        if (restoreFocus && closed != null) {
            closed.tab.engine.view?.requestFocus()
            closed.cursor.animateAppearing()
        }
    }

    override fun onDetachedFromWindow() {
        hide(restoreFocus = false)
        super.onDetachedFromWindow()
    }

    private fun enterDirectNavigation(t: Target) {
        t.tab.engine.setPointerMode(false)
        t.back.gameControllersLongPressBForBackNavigation = true
    }

    /** Explains D-pad mode once, with an option to stop explaining it. */
    private fun explainDirectNavigation(t: Target) {
        val dontAskAgain = CheckBox(context).apply { setText(R.string.don_t_show_again) }
        val padding = 24.dip2px(context).toInt()
        val content = FrameLayout(context).apply {
            setPadding(padding, padding, padding, padding)
            addView(dontAskAgain)
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.direct_navigation_mode_title)
            .setMessage(R.string.direct_navigation_mode_message)
            .setView(content)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (dontAskAgain.isChecked) AppContext.provideConfig().directNavigationModeHintSuppress = true
                enterDirectNavigation(t)
            }
            .setOnDismissListener { t.tab.engine.view?.requestFocus() }
            .show()
    }

    private companion object {
        const val OPEN_MS = 180L
        const val CLOSE_MS = 140L
        const val OPEN_SCALE = 0.94f
        const val GROW_SCALE = 1.08f
    }
}
