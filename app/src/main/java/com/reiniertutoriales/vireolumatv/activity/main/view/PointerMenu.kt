package com.reiniertutoriales.vireolumatv.activity.main.view

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.core.view.isVisible
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvControlColors
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvDialog
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvSymbol
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvSymbolIcon
import com.reiniertutoriales.vireolumatv.activity.main.compose.VireoTvTheme
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import com.reiniertutoriales.vireolumatv.utils.BackPresses
import com.reiniertutoriales.vireolumatv.webengine.PagePoint
import com.reiniertutoriales.vireolumatv.webengine.PageWindow
import com.reiniertutoriales.vireolumatv.widgets.cursor.PointerController

/**
 * The menu a long press opens at the pointer: grab mode, link actions, D-pad mode and zoom, as a
 * cross with grab in the centre; each arm leads back to the centre. It covers the page and places
 * its panel around the pointer, kept on screen. Closing it, by a command or by [close], returns
 * focus to the page and shows the pointer again.
 */
class PointerMenu @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {

    /** What the menu was opened on: the tab and the point of its page under the pointer. */
    private class Target(
        val tab: BrowserTab,
        val window: PageWindow,
        val cursor: PointerController,
        val point: PagePoint,
        val back: BackPresses,
    )

    private val centre = FocusRequester()
    private val panel = ComposeView(context).apply {
        layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContent { VireoTvTheme { Cross() } }
    }
    private var target: Target? = null

    init {
        addView(panel)
    }

    fun show(tab: BrowserTab, window: PageWindow, cursor: PointerController, point: PagePoint, back: BackPresses) {
        val shown = Target(tab, window, cursor, point, back)
        target = shown
        panel.animate().cancel()
        isVisible = true
        panel.post {
            if (target !== shown || !isAttachedToWindow) return@post
            panel.translationX = (point.x - panel.width / 2f).coerceIn(0f, (width - panel.width).coerceAtLeast(0).toFloat())
            panel.translationY = (point.y - panel.height / 2f).coerceIn(0f, (height - panel.height).coerceAtLeast(0).toFloat())
            runCatching { centre.requestFocus() }
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

    private fun movePage() {
        target?.cursor?.startDragging()
        close(grow = true)
    }

    private fun linkOptions() {
        val t = target ?: return
        hide()
        t.window.openLinkActions(t.point)
    }

    private fun directNavigation() {
        val t = target ?: return
        hide()
        if (AppContext.config.keyModeHintSeen) enterDirectNavigation(t) else explainDirectNavigation(t)
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
            closed.cursor.fadeIn()
        }
    }

    override fun onDetachedFromWindow() {
        hide(restoreFocus = false)
        super.onDetachedFromWindow()
    }

    private fun enterDirectNavigation(t: Target) {
        t.tab.engine.setPointerMode(false)
        t.back.holdBGoesBack = true
    }

    /** Explains D-pad mode until the user chooses not to see it again. */
    private fun explainDirectNavigation(t: Target) {
        TvDialog.Builder(context)
            .setTitle(R.string.dpad_mode_title)
            .setMessage(R.string.dpad_mode_message)
            .setNeutralButton(R.string.action_dont_show_again) { _, _ ->
                AppContext.config.keyModeHintSeen = true
                enterDirectNavigation(t)
            }
            .setPositiveButton(android.R.string.ok) { _, _ -> enterDirectNavigation(t) }
            .setOnDismissListener { t.tab.engine.view?.requestFocus() }
            .show()
    }

    @Composable
    private fun Cross() {
        val up = remember { FocusRequester() }
        val down = remember { FocusRequester() }
        val left = remember { FocusRequester() }
        val right = remember { FocusRequester() }
        Box(
            Modifier.semantics { testTagsAsResourceId = true }.size(176.dp)
                .background(colorResource(R.color.surface_variant), CircleShape).padding(8.dp),
        ) {
            MenuButton(TvSymbol.Move, R.string.pointer_move_page, "pointer_move_page", Alignment.Center,
                Modifier.focusRequester(centre).focusProperties {
                    this.up = up; this.down = down; this.left = left; this.right = right
                }, ::movePage)
            MenuButton(TvSymbol.Link, R.string.pointer_link_options, "pointer_link_options", Alignment.TopCenter,
                Modifier.focusRequester(up).focusProperties { this.down = centre }, ::linkOptions)
            MenuButton(TvSymbol.Dpad, R.string.dpad_mode_title, "pointer_dpad_mode", Alignment.BottomCenter,
                Modifier.focusRequester(down).focusProperties { this.up = centre }, ::directNavigation)
            MenuButton(TvSymbol.ZoomOut, R.string.pointer_zoom_out, "pointer_zoom_out", Alignment.CenterStart,
                Modifier.focusRequester(left).focusProperties { this.right = centre }) { target?.tab?.engine?.zoom(closer = false) }
            MenuButton(TvSymbol.ZoomIn, R.string.pointer_zoom_in, "pointer_zoom_in", Alignment.CenterEnd,
                Modifier.focusRequester(right).focusProperties { this.left = centre }) { target?.tab?.engine?.zoom(closer = true) }
        }
    }

    @Composable
    private fun BoxScope.MenuButton(symbol: TvSymbol, @StringRes label: Int, tag: String, alignment: Alignment,
                                    modifier: Modifier, onClick: () -> Unit) {
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        val hovered by interaction.collectIsHoveredAsState()
        val description = stringResource(label)
        Box(
            modifier.align(alignment).testTag(tag).size(48.dp)
                .background(TvControlColors.surface(focused, hovered), CircleShape)
                .border(if (focused) 2.dp else 0.dp, TvControlColors.border(focused, hovered), CircleShape)
                .semantics { contentDescription = description }
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { TvSymbolIcon(symbol, Modifier.size(26.dp)) }
    }

    private companion object {
        const val OPEN_MS = 180L
        const val CLOSE_MS = 140L
        const val OPEN_SCALE = 0.94f
        const val GROW_SCALE = 1.08f
    }
}
