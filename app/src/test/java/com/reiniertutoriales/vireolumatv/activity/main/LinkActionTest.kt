package com.reiniertutoriales.vireolumatv.activity.main

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkActionTest {
    @Test fun noLinkOffersOnlyReloadAndNonWebLinksCannotDownloadOrOpenTabs() {
        assertEquals(listOf(LinkAction.RELOAD), LinkAction.available(null))
        assertEquals(listOf(LinkAction.RELOAD, LinkAction.COPY, LinkAction.SHARE),
            LinkAction.available("mailto:person@example.test"))
        assertEquals(listOf(LinkAction.RELOAD, LinkAction.COPY, LinkAction.SHARE),
            LinkAction.available("javascript:alert(1)"))
    }

    @Test fun webLinksOfferAllSixActionsInTheExistingOrder() {
        for (url in listOf("http://example.test", "https://example.test", "HTTPS://EXAMPLE.TEST")) {
            assertEquals(LinkAction.entries, LinkAction.available(url))
        }
    }
}
