package com.reiniertutoriales.vireolumatv.activity.main.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.reiniertutoriales.vireolumatv.R

/** TV Material 3 dark scheme on the app's AMOLED colors (res/values/colors.xml), with [ChromeMetrics]. */
@Composable
fun VireoTvTheme(content: @Composable () -> Unit) {
    val black = colorResource(R.color.background)
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = black,
            surface = black,
            onSurface = colorResource(R.color.on_surface),
            surfaceVariant = colorResource(R.color.surface_variant),
        ),
    ) { ProvideChromeMetrics(content) }
}
