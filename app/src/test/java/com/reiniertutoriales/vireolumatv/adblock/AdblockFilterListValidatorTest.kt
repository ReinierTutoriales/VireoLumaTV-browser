package com.reiniertutoriales.vireolumatv.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdblockFilterListValidatorTest {
    @Test
    fun defaultListRequiresAdblockHeaderAndMinimumLines() {
        val content = defaultListWithRules(99)

        assertTrue(AdblockFilterListValidator.isValid(content, requiresAdblockHeader = true))
    }

    @Test
    fun defaultListRejectsOneLineBelowMinimum() {
        val content = defaultListWithRules(98)

        assertFalse(AdblockFilterListValidator.isValid(content, requiresAdblockHeader = true))
    }

    @Test
    fun defaultListRejectsMissingAdblockHeader() {
        val content = buildString {
            appendLine("! no header")
            repeat(120) { appendLine("||example$it.com^") }
        }

        assertFalse(AdblockFilterListValidator.isValid(content, requiresAdblockHeader = true))
    }

    @Test
    fun defaultListRejectsTooFewLines() {
        val content = buildString {
            appendLine("[Adblock Plus 2.0]")
            repeat(10) { appendLine("||example$it.com^") }
        }

        assertFalse(AdblockFilterListValidator.isValid(content, requiresAdblockHeader = true))
    }

    @Test
    fun defaultListAcceptsUtf8BomBeforeHeader() {
        val content = "\uFEFF" + defaultListWithRules(99)

        assertTrue(AdblockFilterListValidator.isValid(content, requiresAdblockHeader = true))
    }

    @Test
    fun customListAllowsSingleNonBlankRuleWithoutHeader() {
        assertTrue(AdblockFilterListValidator.isValid("||ads.example.com^", requiresAdblockHeader = false))
    }

    @Test
    fun customListRejectsBlankContent() {
        assertFalse(AdblockFilterListValidator.isValid(" \n\t\n", requiresAdblockHeader = false))
    }

    @Test
    fun rejectsHtmlResponses() {
        assertFalse(AdblockFilterListValidator.isValid("<!doctype html><html></html>", requiresAdblockHeader = false))
        assertFalse(AdblockFilterListValidator.isValid("  <html></html>", requiresAdblockHeader = false))
    }

    private fun defaultListWithRules(ruleCount: Int): String {
        return listOf("[Adblock Plus 2.0]")
            .plus((0 until ruleCount).map { "||example$it.com^" })
            .joinToString("\n")
    }
}
