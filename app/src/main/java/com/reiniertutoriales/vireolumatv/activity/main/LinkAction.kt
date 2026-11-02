package com.reiniertutoriales.vireolumatv.activity.main

import com.reiniertutoriales.vireolumatv.R

internal enum class LinkAction(val label: Int) {
    RELOAD(R.string.nav_reload),
    OPEN_TAB(R.string.link_open_in_new_tab),
    OPEN_APP(R.string.link_open_in_app),
    COPY(R.string.link_copy),
    DOWNLOAD(R.string.link_download),
    SHARE(R.string.link_share);

    companion object {
        fun available(url: String?): List<LinkAction> {
            val web = url?.substringBefore(':')?.lowercase() in listOf("http", "https")
            return entries.filter {
                when (it) {
                    RELOAD -> true
                    COPY, SHARE -> url != null
                    else -> web
                }
            }
        }
    }
}
