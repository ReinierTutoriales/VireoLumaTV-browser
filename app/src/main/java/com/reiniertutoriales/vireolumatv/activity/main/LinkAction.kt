package com.reiniertutoriales.vireolumatv.activity.main

import com.reiniertutoriales.vireolumatv.R

internal enum class LinkAction(val label: Int) {
    RELOAD(R.string.refresh_page),
    OPEN_TAB(R.string.open_in_new_tab),
    OPEN_APP(R.string.open_in_external_application),
    COPY(R.string.copy_to_clipboard),
    DOWNLOAD(R.string.download),
    SHARE(R.string.share_url);

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
