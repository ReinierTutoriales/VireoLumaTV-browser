package com.reiniertutoriales.vireolumatv.activity.main.compose

import androidx.annotation.DrawableRes
import androidx.tv.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.reiniertutoriales.vireolumatv.R

/**
 * Typed catalog for the local Material Symbols Rounded vector assets.
 * UI consumers never pass Android resource IDs or font ligature strings.
 * One local vector renderer supports all project API levels without font fallback.
 */
enum class TvSymbol(@DrawableRes internal val drawable: Int) {
    Add(R.drawable.ic_add),
    Back(R.drawable.ic_back),
    Block(R.drawable.ic_block),
    Bookmark(R.drawable.ic_bookmark),
    Delete(R.drawable.ic_delete),
    Done(R.drawable.ic_done),
    Download(R.drawable.ic_download),
    Dpad(R.drawable.ic_dpad),
    Edit(R.drawable.ic_edit),
    Exit(R.drawable.ic_exit),
    Forward(R.drawable.ic_forward),
    Globe(R.drawable.ic_globe),
    History(R.drawable.ic_history),
    Home(R.drawable.ic_home),
    Link(R.drawable.ic_link),
    MenuDownloads(R.drawable.ic_menu_downloads),
    More(R.drawable.ic_more),
    Move(R.drawable.ic_move),
    NewTab(R.drawable.ic_new_tab),
    Private(R.drawable.ic_private),
    Refresh(R.drawable.ic_refresh),
    Search(R.drawable.ic_search),
    Settings(R.drawable.ic_settings),
    ShieldOff(R.drawable.ic_shield_off),
    ShieldOn(R.drawable.ic_shield_on),
    StatDownload(R.drawable.ic_stat_download),
    TabClose(R.drawable.ic_tab_close),
    Video(R.drawable.ic_video),
    ZoomIn(R.drawable.ic_zoom_in),
    ZoomOut(R.drawable.ic_zoom_out);
}

@Composable
fun TvSymbolIcon(
    symbol: TvSymbol,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    contentDescription: String? = null,
) {
    Icon(
        painter = painterResource(symbol.drawable),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}
