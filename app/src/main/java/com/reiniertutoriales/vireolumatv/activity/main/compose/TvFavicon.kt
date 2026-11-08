package com.reiniertutoriales.vireolumatv.activity.main.compose

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.singleton.Favicons

/**
 * The site icon of [url], requested only while this item is composed (visible rows of a lazy list);
 * meanwhile, and when there is none, a globe tinted [tint]. The home page shows the app icon.
 */
@Composable
fun TvFavicon(url: String?, modifier: Modifier = Modifier, tint: Color = Color.White) {
    val home = url == Config.HOME_PAGE_URL || url == Config.HOME_URL_ALIAS
    var icon by remember(url) { mutableStateOf<Bitmap?>(null) }
    if (!home && url != null) LaunchedEffect(url) { icon = Favicons.get(url) }
    val loaded = icon
    when {
        home -> Image(painterResource(R.drawable.ic_launcher), contentDescription = null, modifier = modifier)
        loaded != null -> Image(loaded.asImageBitmap(), contentDescription = null, modifier = modifier)
        else -> TvSymbolIcon(TvSymbol.Globe, modifier, tint = tint)
    }
}
