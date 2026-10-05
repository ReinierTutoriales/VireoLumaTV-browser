package com.reiniertutoriales.vireolumatv.adblock

import java.util.Locale

object AdblockFilterListValidator {
    private const val MIN_DEFAULT_FILTER_LINES = 100
    private const val MIN_CUSTOM_FILTER_LINES = 1

    fun isValid(content: String, requiresAdblockHeader: Boolean): Boolean {
        val normalizedContent = content.removePrefix("\uFEFF")
        val trimmedStart = normalizedContent.trimStart()
        if (looksLikeHtml(trimmedStart)) return false
        val lineCount = normalizedContent.lineSequence().take(MIN_DEFAULT_FILTER_LINES).count()
        if (requiresAdblockHeader) {
            val firstNonBlankLine = normalizedContent.lineSequence().firstOrNull { it.isNotBlank() } ?: return false
            return firstNonBlankLine.startsWith("[Adblock Plus") && lineCount >= MIN_DEFAULT_FILTER_LINES
        }
        return normalizedContent.isNotBlank() && lineCount >= MIN_CUSTOM_FILTER_LINES
    }

    private fun looksLikeHtml(trimmedStart: String): Boolean {
        val lowerStart = trimmedStart.take(32).lowercase(Locale.US)
        return lowerStart.startsWith("<!doctype html") || lowerStart.startsWith("<html")
    }
}
