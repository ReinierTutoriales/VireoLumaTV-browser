package com.reiniertutoriales.vireolumatv.activity.main.view.tabs

import com.reiniertutoriales.vireolumatv.model.BrowserTab

/** What the tab strip asks the browser to do; positions are indexes in the open tab list. */
sealed interface TabCommand {
    /** The remote moved onto the tab at [index]; it is previewed, not yet shown. */
    data class Focus(val index: Int) : TabCommand
    /** OK on the tab at [index]: show it. */
    data class Show(val index: Int) : TabCommand
    /** The "+" control: a new tab with the home page at the end. */
    data object Add : TabCommand
    /** A new tab with the home page, inserted at [index]. */
    data class AddAt(val index: Int) : TabCommand
    data class Favorite(val tab: BrowserTab) : TabCommand
    data class Close(val tab: BrowserTab) : TabCommand
}
