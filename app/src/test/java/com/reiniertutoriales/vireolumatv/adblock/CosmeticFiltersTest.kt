package com.reiniertutoriales.vireolumatv.adblock

import org.junit.Assert.*
import org.junit.Test

class CosmeticFiltersTest {
    private val filters = CosmeticFilters.parse(
        "+example.test\t.ad-box\n+example.test\t#top-ad\n-sub.example.test\t.ad-box\n+other.test\t.promo\n")

    @Test fun parentDomainsApplyAndExceptionsRemoveSelectors() {
        assertEquals(".ad-box{display:none!important}#top-ad{display:none!important}",
            filters.cssFor("www.example.test"))
        assertEquals("#top-ad{display:none!important}", filters.cssFor("SUB.example.test"))
        assertEquals("", filters.cssFor("unrelated.test"))
        assertEquals("", filters.cssFor(null))
        assertTrue(CosmeticFilters.parse("").isEmpty)
    }
}
