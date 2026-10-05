package com.reiniertutoriales.vireolumatv.activity.main.view

import android.app.AlertDialog
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.FrameLayout
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.databinding.ViewCursorMenuBinding
import com.reiniertutoriales.vireolumatv.utils.dip2px
import com.reiniertutoriales.vireolumatv.model.WebTabState
import com.reiniertutoriales.vireolumatv.webengine.WebEngineWindowProviderCallback
import com.reiniertutoriales.vireolumatv.widgets.cursor.CursorDrawerDelegate
import com.reiniertutoriales.vireolumatv.utils.BackNavigationEventsAdapter

class CursorMenuView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
): FrameLayout(context, attrs, defStyleAttr) {

    private var vb: ViewCursorMenuBinding =
        ViewCursorMenuBinding.inflate(LayoutInflater.from(context), this, true)
    private var menuContext: MenuContext? = null

    init {
        vb.btnGrabMode.setOnClickListener {
            menuContext?.cursorDrawerDelegate?.goToGrabMode()
            close(CloseAnimation.EXPLODE_OUT)
        }
        vb.btnContextMenu.setOnClickListener {
            val mc = menuContext ?: return@setOnClickListener
            closeWithoutAnimation()
            mc.windowProvider.suggestActionsForLink(mc.baseUri, mc.linkUri, mc.srcUri,
                mc.title, mc.altText, mc.textContent, mc.x, mc.y)
        }
        vb.btnDPADMode.setOnClickListener {
            val mc = menuContext ?: return@setOnClickListener
            closeWithoutAnimation()
            if (AppContext.provideConfig().directNavigationModeHintSuppress) {
                enterDirectNavigationMode(mc)
            } else {
                showDirectNavigationModeDialog(mc)
            }
        }
        vb.btnZoomIn.setOnClickListener { menuContext?.tab?.webEngine?.zoomIn() }
        vb.btnZoomOut.setOnClickListener { menuContext?.tab?.webEngine?.zoomOut() }
    }

    fun show(
        tab: WebTabState,
        windowProvider: WebEngineWindowProviderCallback,
        cursorDrawerDelegate: CursorDrawerDelegate,
        baseUri: String?,
        linkUri: String?,
        srcUri: String?,
        title: String?,
        altText: String?,
        textContent: String?,
        x: Int,
        y: Int,
        backNavigationEventsAdapter: BackNavigationEventsAdapter
    ) {
        val mc = MenuContext(tab, windowProvider, cursorDrawerDelegate,
            baseUri, linkUri, srcUri, title, altText, textContent, x, y, backNavigationEventsAdapter)
        menuContext = mc
        vb.root.animate().cancel()
        visibility = VISIBLE
        // Move the small menu within the screen, not the full-screen overlay.
        vb.root.post {
            if (menuContext !== mc || !isAttachedToWindow) return@post
            vb.root.translationX = (x - vb.root.width / 2f)
                .coerceIn(0f, (width - vb.root.width).coerceAtLeast(0).toFloat())
            vb.root.translationY = (y - vb.root.height / 2f)
                .coerceIn(0f, (height - vb.root.height).coerceAtLeast(0).toFloat())
            vb.btnGrabMode.requestFocus()
            cursorDrawerDelegate.hideCursor()
        }
        vb.root.alpha = 0f
        vb.root.scaleX = 0.94f
        vb.root.scaleY = 0.94f
        vb.root.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start()
    }

    /** Hides the menu immediately (no animation). Used before showing a dialog so focus does not re-trigger menu actions. */
    private fun closeWithoutAnimation() {
        val previous = menuContext
        menuContext = null
        vb.root.animate().cancel()
        visibility = GONE
        previous?.tab?.webEngine?.getView()?.requestFocus()
        previous?.cursorDrawerDelegate?.animateAppearing()
        vb.root.alpha = 1f
        vb.root.scaleX = 1f
        vb.root.scaleY = 1f
    }

    fun close(animation: CloseAnimation = CloseAnimation.FADE_OUT) {
        if (menuContext == null) return
        vb.root.animate().cancel()
        val scale = if (animation == CloseAnimation.EXPLODE_OUT) 1.08f else 1f
        vb.root.animate().alpha(0f).scaleX(scale).scaleY(scale).setDuration(140)
            .withEndAction { closeWithoutAnimation() }.start()
    }

    override fun onDetachedFromWindow() {
        closeWithoutAnimation()
        super.onDetachedFromWindow()
    }

    enum class CloseAnimation {
        FADE_OUT,
        ROTATE_OUT,
        EXPLODE_OUT
    }

    private fun enterDirectNavigationMode(mc: MenuContext) {
        mc.tab.webEngine?.setVirtualCursorMode(false)
        mc.backNavigationEventsAdapter?.gameControllersLongPressBForBackNavigation = true
    }

    private fun showDirectNavigationModeDialog(mc: MenuContext) {
        val pad = 24.dip2px(context).toInt()
        val checkBox = CheckBox(context).apply {
            text = context.getString(R.string.don_t_show_again)
        }
        val container = FrameLayout(context).apply {
            setPadding(pad, pad, pad, pad)
            addView(checkBox)
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.direct_navigation_mode_title)
            .setMessage(R.string.direct_navigation_mode_message)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (checkBox.isChecked) {
                    AppContext.provideConfig().directNavigationModeHintSuppress = true
                }
                enterDirectNavigationMode(mc)
            }
            .setOnDismissListener { mc.tab.webEngine.getView()?.requestFocus() }
            .show()
    }

    private data class MenuContext (
        val tab: WebTabState,
        val windowProvider: WebEngineWindowProviderCallback,
        val cursorDrawerDelegate: CursorDrawerDelegate,
        val baseUri: String?,
        val linkUri: String?,
        val srcUri: String?,
        val title: String?,
        val altText: String?,
        val textContent: String?,
        val x: Int,
        val y: Int,
        val backNavigationEventsAdapter: BackNavigationEventsAdapter
    )
}
