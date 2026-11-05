package com.reiniertutoriales.vireolumatv.activity.main

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import com.reiniertutoriales.vireolumatv.R

/** Actions offered by the application menu, in display order. */
internal enum class AppMenuEntry(@StringRes val label: Int, @DrawableRes val icon: Int) {
    HISTORY(R.string.menu_history, R.drawable.ic_history),
    FAVORITES(R.string.menu_favorites, R.drawable.ic_bookmark),
    DOWNLOADS(R.string.menu_downloads, R.drawable.ic_download),
    INCOGNITO(R.string.menu_private, R.drawable.ic_private),
    SETTINGS(R.string.menu_settings, R.drawable.ic_settings),
    CLOSE_TAB(R.string.menu_close_tab, R.drawable.ic_tab_close),
    CLOSE_APPLICATION(R.string.menu_quit, R.drawable.ic_exit),
}

/** Every entry: Favorites is where saved favorites are opened, edited and removed. */
internal fun appMenuEntries(sdk: Int = android.os.Build.VERSION.SDK_INT): List<AppMenuEntry> =
    AppMenuEntry.entries.filter { it != AppMenuEntry.INCOGNITO || sdk >= 28 }


internal class AppMenuPanel(context: Context, entries: List<AppMenuEntry>, onPick: (AppMenuEntry) -> Unit) :
    AppCompatDialog(context, R.style.SidePanel) {
    init {
        val content = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                AppMenuContent(entries) { entry ->
                    if (isShowing) {
                        dismiss()
                        onPick(entry)
                    }
                }
            }
        }
        setContentView(content)
        window?.apply {
            setGravity(Gravity.END)
            setLayout(context.resources.getDimensionPixelSize(R.dimen.side_panel_width), ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }
}

@Composable
internal fun AppMenuContent(entries: List<AppMenuEntry>, onPick: (AppMenuEntry) -> Unit) {
    val first = remember { FocusRequester() }
    MaterialTheme(colorScheme = darkColorScheme(
        background = Color.Black,
        surface = Color.Black,
        onSurface = Color(0xFFF2F2F2),
        primary = Color(0xFF7BD88F),
    )) {
        Column(
            Modifier.fillMaxSize().background(Color.Black)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, top = 27.dp, end = 48.dp, bottom = 27.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp))
            entries.forEachIndexed { index, entry ->
                Button(
                    onClick = { onPick(entry) },
                    modifier = Modifier.fillMaxWidth().then(
                        if (index == 0) Modifier.focusRequester(first) else Modifier
                    ),
                ) {
                    Icon(painterResource(entry.icon), contentDescription = null, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(18.dp))
                    Text(stringResource(entry.label), maxLines = 1)
                }
            }
        }
        LaunchedEffect(entries) {
            if (entries.isNotEmpty()) first.requestFocus()
        }
    }
}
