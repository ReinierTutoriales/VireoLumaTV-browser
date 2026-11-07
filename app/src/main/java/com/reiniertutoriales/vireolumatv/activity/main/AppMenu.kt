package com.reiniertutoriales.vireolumatv.activity.main

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvControlColors
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvSymbol
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvSymbolIcon
import com.reiniertutoriales.vireolumatv.activity.main.compose.VireoTvTheme

/** Actions offered by the application menu, in display order. */
internal enum class AppMenuEntry(@StringRes val label: Int, val symbol: TvSymbol) {
    HISTORY(R.string.menu_history, TvSymbol.History),
    FAVORITES(R.string.menu_favorites, TvSymbol.Bookmark),
    DOWNLOADS(R.string.menu_downloads, TvSymbol.Download),
    INCOGNITO(R.string.menu_private, TvSymbol.Private),
    SETTINGS(R.string.menu_settings, TvSymbol.Settings),
    CLOSE_TAB(R.string.menu_close_tab, TvSymbol.TabClose),
    CLOSE_APPLICATION(R.string.menu_quit, TvSymbol.Exit),
}

/** Every entry: Favorites is where saved favorites are opened, edited and removed. */
internal fun appMenuEntries(sdk: Int = android.os.Build.VERSION.SDK_INT): List<AppMenuEntry> =
    AppMenuEntry.entries.filter { it != AppMenuEntry.INCOGNITO || sdk >= 28 }

/**
 * The application menu as a TV side panel: full height at the end of the screen, one row per
 * entry, focus on the first. Back closes it; choosing an entry closes it and runs [onPick].
 */
internal class AppMenuPanel(context: Context, entries: List<AppMenuEntry>, private val onPick: (AppMenuEntry) -> Unit) :
    AppCompatDialog(context, R.style.SidePanel) {
    init {
        setContentView(ComposeView(context).apply {
            setContent { VireoTvTheme { TvAppMenu(entries, ::pick) } }
        })
        window?.apply {
            setGravity(Gravity.END)
            setLayout(context.resources.getDimensionPixelSize(R.dimen.side_panel_width), ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    /** Closes the panel, then runs the entry once. */
    internal fun pick(entry: AppMenuEntry) {
        if (!isShowing) return
        dismiss()
        onPick(entry)
    }
}

/** The panel's content: the app name, then one row per entry; the first takes focus. */
@Composable
internal fun TvAppMenu(entries: List<AppMenuEntry>, onPick: (AppMenuEntry) -> Unit) {
    val first = remember { FocusRequester() }
    Column(
        Modifier.semantics { testTagsAsResourceId = true }.fillMaxSize().verticalScroll(rememberScrollState()).padding(
            start = 24.dp,
            top = dimensionResource(R.dimen.safe_vertical),
            end = dimensionResource(R.dimen.safe_horizontal),
            bottom = dimensionResource(R.dimen.safe_vertical),
        ),
    ) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp, bottom = 16.dp),
        )
        entries.forEachIndexed { index, entry ->
            MenuRow(
                entry = entry,
                onClick = { onPick(entry) },
                modifier = Modifier.padding(bottom = 4.dp).testTag("menu_${entry.name.lowercase()}")
                    .then(if (index == 0) Modifier.focusRequester(first) else Modifier),
            )
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/**
 * One menu row. Foundation's clickable handles D-pad OK, touch, mouse and pointer-remote clicks and
 * reports hover; TV Material's clickable surfaces (1.1.0) react only to D-pad and accessibility.
 */
@Composable
private fun MenuRow(entry: AppMenuEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)
            .background(TvControlColors.surface(focused, hovered), shape)
            .border(if (focused) 2.dp else 0.dp, TvControlColors.border(focused, hovered), shape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        TvSymbolIcon(entry.symbol, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(entry.label), color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}
