package com.reiniertutoriales.vireolumatv.adblock

import org.junit.Assert.*
import org.junit.Test

class FilterListPreprocessorTest {
    @Test fun popupRulesBecomeDocumentRulesForTheSeparateClient() {
        val result = FilterListPreprocessor.extract("""
            [Adblock Plus 2.0]
            ! comment ${'$'}popup
            ||popads.test^${'$'}popup
            ||pop.test^${'$'}popup,third-party
            &popunder=${'$'}popup
            @@||ok.test^${'$'}popup
            ||banner.test^${'$'}image
            ||plain.test^
            /regex${'$'}popup/
        """.trimIndent())
        assertEquals(listOf(
            "||popads.test^${'$'}document",
            "||pop.test^${'$'}document,third-party",
            "&popunder=${'$'}document",
            "@@||ok.test^${'$'}document"
        ), result.popupRules.lines().filter { it.isNotEmpty() })
    }

    @Test fun onlyPlainSiteSpecificElementHidingIsKept() {
        val result = FilterListPreprocessor.extract("""
            example.test,~sub.example.test##.ad-box
            ##.generic-ad
            news.test#@#.sponsor
            proc.test##div:has-text(Sponsored)
            ext.test#?#.ad:-abp-has(.x)
            js.test##+js(set-constant, x, 1)
            ent.*##.ad
            ||network.test/#anchor${'$'}popup
        """.trimIndent())
        assertEquals(listOf(
            "+example.test\t.ad-box",
            "-sub.example.test\t.ad-box",
            "-news.test\t.sponsor"
        ), result.cosmeticRules.lines().filter { it.isNotEmpty() })
        assertEquals("A '#' inside a network rule is not element hiding",
            "||network.test/#anchor${'$'}document", result.popupRules.trim())
    }
}
