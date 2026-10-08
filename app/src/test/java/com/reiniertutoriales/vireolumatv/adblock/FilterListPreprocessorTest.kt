package com.reiniertutoriales.vireolumatv.adblock

import org.junit.Assert.*
import org.junit.Test

class FilterListPreprocessorTest {
    @Test fun popupRulesBecomeDocumentRulesForTheSeparateEngine() {
        val result = FilterListPreprocessor.extract("""
            [Adblock Plus 2.0]
            ! comment ${'$'}popup
            ||popads.test^${'$'}popup
            ||pop.test^${'$'}popup,third-party
            &popunder=${'$'}popup
            @@||ok.test^${'$'}popup
            ||under.test^${'$'}popunder
            ||banner.test^${'$'}image
            example.test##.ad-box
            ||network.test/#anchor${'$'}popup
            /regex${'$'}popup/
        """.trimIndent())
        assertEquals(listOf(
            "||popads.test^${'$'}document",
            "||pop.test^${'$'}document,third-party",
            "&popunder=${'$'}document",
            "@@||ok.test^${'$'}document",
            "||under.test^${'$'}document",
            "||network.test/#anchor${'$'}document"
        ), result.popupRules.lines().filter { it.isNotEmpty() })
    }

    @Test fun uBlockDirectivesKeepOnlyBranchesForAnAndroidChromiumBrowser() {
        val text = """
            a
            !#if env_firefox
            firefox-only
            !#else
            not-firefox
            !#endif
            !#if env_mobile && !cap_html_filtering
            mobile
            !#if ext_ubol
            lite
            !#endif
            !#include filters-mobile.txt
            !#endif
            !#if (env_safari || env_chromium)
            chromium
            !#endif
            !#include ../escape.txt
            !#include https://evil.example/list.txt
            z
        """.trimIndent()
        val kept = FilterListPreprocessor.applyDirectives(text).lines().filter { it.isNotEmpty() }
        assertEquals(listOf("a", "not-firefox", "mobile", "!#include filters-mobile.txt", "chromium",
            "!#include ../escape.txt", "!#include https://evil.example/list.txt", "z"), kept)
        assertEquals(listOf("filters-mobile.txt"), FilterListPreprocessor.includes(kept.joinToString("\n")))
    }
}
