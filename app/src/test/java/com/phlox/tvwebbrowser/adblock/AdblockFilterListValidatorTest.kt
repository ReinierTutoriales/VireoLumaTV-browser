package com.phlox.tvwebbrowser.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdblockFilterListValidatorTest {
    @Test
    fun defaultListRequiresAdblockHeaderAndMinimumLines() {
        val content = buildString {
            appendLine("[Adblock Plus 2.0]")
            repeat(99) { appendLine("||example$it.com^") }
        }

        assertTrue(AdblockFilterListValidator.isValid(content, requiresAdblockHeader = true))
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
        val content = buildString {
            append("\uFEFF")
            appendLine("[Adblock Plus 2.0]")
            repeat(99) { appendLine("||example$it.com^") }
        }

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
}
