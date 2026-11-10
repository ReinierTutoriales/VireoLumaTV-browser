package com.reiniertutoriales.vireolumatv.activity.main.compose

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sizes of the app's controls for the current window, from the TV Material 3 tokens
 * (androidx.tv:tv-material 1.1.0: 40 dp buttons with 20 dp icons, 48 dp list items, 16/14 sp
 * body and label text) and the TV layout guide (48 dp side and 24 dp top safe margins on the
 * 960 x 540 dp canvas). A remote focuses but does not touch, so 40 dp targets apply on a TV;
 * other devices keep the 48 dp touch target. Narrow windows put the address above the commands.
 * Text is in sp, so the system text scale still applies; heights are minimums that grow with it.
 */
@Immutable
data class ChromeMetrics(
    val control: Dp,
    val icon: Dp,
    val row: Dp,
    val gap: Dp,
    val edge: Dp,
    val top: Dp,
    val body: TextUnit,
    val label: TextUnit,
    val tabMaxWidth: Dp,
    val stacked: Boolean,
) {
    companion object {
        fun forWindow(width: Dp, television: Boolean) = ChromeMetrics(
            control = if (television) 40.dp else 48.dp,
            icon = if (television) 20.dp else 24.dp,
            row = 48.dp,
            gap = 8.dp,
            edge = when {
                width >= EXPANDED -> 48.dp
                width >= MEDIUM -> 24.dp
                else -> 16.dp
            },
            top = if (width >= EXPANDED) 24.dp else 8.dp,
            body = 16.sp,
            label = 14.sp,
            tabMaxWidth = if (width >= EXPANDED) 188.dp else 180.dp,
            stacked = width < MEDIUM,
        )

        // Window size class width breakpoints.
        private val MEDIUM = 600.dp
        private val EXPANDED = 840.dp
    }
}

/** The metrics in effect; previews and tests without a provider get the TV ones. */
val LocalChromeMetrics = staticCompositionLocalOf { ChromeMetrics.forWindow(960.dp, television = true) }

/** Provides [ChromeMetrics] for this window, updated when its size or configuration changes. */
@Composable
fun ProvideChromeMetrics(content: @Composable () -> Unit) {
    // The window's current size (WindowMetrics), not the screen's: correct in multi-window and on resize.
    val width = LocalWindowInfo.current.containerDpSize.width
    val television = (LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
        Configuration.UI_MODE_TYPE_TELEVISION
    val metrics = remember(width, television) { ChromeMetrics.forWindow(width, television) }
    CompositionLocalProvider(LocalChromeMetrics provides metrics, content = content)
}
