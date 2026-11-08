package com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentDialog
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.main.AdBlocker
import com.reiniertutoriales.vireolumatv.activity.main.BrowserSettings
import com.reiniertutoriales.vireolumatv.activity.main.MainActivity
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvControlColors
import com.reiniertutoriales.vireolumatv.activity.main.compose.VireoTvTheme
import com.reiniertutoriales.vireolumatv.utils.activemodel.ScreenModels

/** The settings pages, in tab order. */
internal enum class SettingsTab(@StringRes val label: Int, val tag: String) {
    GENERAL(R.string.settings_tab_general, "general_page_tab"),
    SHORTCUTS(R.string.settings_tab_shortcuts, "shortcuts_page_tab"),
    ABOUT(R.string.settings_tab_about, "about_page_tab"),
}

/**
 * Full-screen settings with General, Shortcuts and About pages. Only activating a tab switches the
 * page, never focus alone; the pages' state lives here while the screen is open, so unsaved text
 * survives a page switch. The General text values apply on close, and focus returns where it was.
 */
class SettingsScreen(context: Context, model: BrowserSettings) : ComponentDialog(context, R.style.SettingsScreen) {
    private val activity = context.activity()
    private val scope = (activity as LifecycleOwner).lifecycleScope
    internal val general = GeneralPage(context, model, ScreenModels.get(AdBlocker::class, activity), scope,
        onBlockerChanged = { (activity as? MainActivity)?.refreshAdblockSettings() })
    internal val about = AboutPage(context, scope, onDone = ::dismiss)
    private var previousFocus: View? = null

    init {
        setContentView(ComposeView(context).apply {
            setContent { VireoTvTheme { SettingsContent(general, about) } }
        })
        setOnDismissListener { general.save() }
    }

    override fun show() {
        previousFocus = activity.window?.decorView?.findFocus()
        super.show()
    }

    override fun dismiss() {
        super.dismiss()
        previousFocus?.takeIf { it.isAttachedToWindow && it.isFocusable && it.isShown }?.requestFocus()
        previousFocus = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
}

private fun Context.activity(): Activity {
    var current: Context = this
    while (current !is Activity) current = (current as ContextWrapper).baseContext
    return current
}

/** Tabs on top, the chosen page below with a short fade, inside the overscan-safe area. */
@Composable
internal fun SettingsContent(general: GeneralPage, about: AboutPage) {
    var page by remember { mutableStateOf(SettingsTab.GENERAL) }
    val context = LocalContext.current
    val requesters = remember { SettingsTab.entries.associateWith { FocusRequester() } }
    Column(
        // The window draws the black backdrop (SettingsScreen style); no second full-screen fill.
        Modifier.semantics { testTagsAsResourceId = true }.fillMaxSize()
            .padding(start = dimensionResource(R.dimen.safe_horizontal), end = dimensionResource(R.dimen.safe_horizontal),
                top = dimensionResource(R.dimen.safe_vertical)),
    ) {
        Row(Modifier.focusRestorer(requesters.getValue(page)).padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsTab.entries.forEach { tab ->
                SettingsTabChip(tab, tab == page, Modifier.focusRequester(requesters.getValue(tab))) { page = tab }
            }
        }
        Crossfade(page, Modifier.fillMaxWidth().weight(1f).padding(top = 16.dp), animationSpec = tween(180),
            label = "settings page") { shown ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                when (shown) {
                    SettingsTab.GENERAL -> general.Content()
                    SettingsTab.SHORTCUTS -> ShortcutsPage(context)
                    SettingsTab.ABOUT -> about.Content()
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { requesters.getValue(SettingsTab.GENERAL).requestFocus() } }
}

@Composable
private fun SettingsTabChip(tab: SettingsTab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier.testTag(tab.tag).semantics { this.selected = selected }
            .defaultMinSize(minWidth = 120.dp, minHeight = 48.dp)
            .background(if (selected && !focused) TvControlColors.Selected else TvControlColors.surface(focused, hovered), shape)
            .border(if (focused) 2.dp else if (selected) 1.dp else 0.dp, TvControlColors.border(focused, hovered || selected), shape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(tab.label), fontSize = 17.sp,
            color = if (selected || focused) MaterialTheme.colorScheme.onSurface else Color.Gray)
    }
}
