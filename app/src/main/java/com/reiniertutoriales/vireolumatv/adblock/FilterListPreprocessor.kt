package com.reiniertutoriales.vireolumatv.adblock

import java.util.Locale

/**
 * Extracts the parts of a filter list that the brave ad-block 0.0.4 native engine ignores.
 *
 * - `$popup` rules: filter.h puts FOPopup in FOUnsupportedSoSkipCheck, so the native matcher
 *   silently skips all ~3000 EasyList popup rules. They are rewritten to `$document` and compiled
 *   into a separate small client that is only queried for new windows.
 * - Site-specific element hiding (`example.com##.ad`): the native client keeps no cosmetic
 *   filters. Generic `##` rules are skipped on purpose: ~14k selectors on every page cost style
 *   recalculation time on low-end TV SoCs.
 */
object FilterListPreprocessor {
    class Result(val popupRules: String, val cosmeticRules: String)

    // Extended/procedural syntaxes that are not plain CSS or need scriptlets.
    private val proceduralMarkers = listOf(
        ":-abp-", ":has-text(", ":matches-", ":upward(", ":xpath(", ":remove(", ":style(",
        ":min-text-length(", ":watch-attr(", ":others(", ":if(", ":if-not(", ":nth-ancestor(",
        ":contains(", ":matches-path(", ":matches-attr(", ":matches-css", "+js("
    )

    fun extract(text: String): Result {
        val popup = StringBuilder()
        val cosmetic = StringBuilder()
        var start = 0
        val length = text.length
        while (start < length) {
            var end = text.indexOf('\n', start)
            if (end < 0) end = length
            val line = text.substring(start, end).trim()
            start = end + 1
            if (line.isEmpty() || line[0] == '!' || line[0] == '[') continue
            val hashIndex = cosmeticSeparator(line)
            if (hashIndex >= 0) {
                if (hashIndex > 0) cosmeticLine(line, hashIndex)?.let { cosmetic.append(it).append('\n') }
                continue
            }
            popupLine(line)?.let { popup.append(it).append('\n') }
        }
        return Result(popup.toString(), cosmetic.toString())
    }

    /** Index of `##`, `#@#`, `#?#`, `#$#` or `#%#`; -1 for network rules (which may contain '#'). */
    private fun cosmeticSeparator(line: String): Int {
        var index = line.indexOf('#')
        while (index >= 0 && index < line.length - 1) {
            val next = line[index + 1]
            if (next == '#') return index
            if ((next == '@' || next == '?' || next == '$' || next == '%') &&
                index + 2 < line.length && line[index + 2] == '#') return index
            index = line.indexOf('#', index + 1)
        }
        return -1
    }

    /** `rule$popup[,opts]` -> `rule$document[,opts]`; null when the line is not a popup rule. */
    internal fun popupLine(line: String): String? {
        // Regex rules can contain '$' inside the pattern; they are rare in popup sections.
        if (line.startsWith("/") && line.endsWith("/")) return null
        val dollar = line.lastIndexOf('$')
        if (dollar <= 0 || dollar == line.length - 1) return null
        val options = line.substring(dollar + 1).split(',')
        if (options.none { it == "popup" }) return null
        val rewritten = options.filter { it != "popup" && it != "document" }.toMutableList()
        rewritten.add(0, "document")
        return line.substring(0, dollar + 1) + rewritten.joinToString(",")
    }

    /**
     * Returns `+domain<TAB>selector` / `-domain<TAB>selector` (exception) lines, one per positive
     * or negated domain. Generic, procedural and HTML filters return null.
     */
    internal fun cosmeticLine(line: String, hashIndex: Int): String? {
        val exception: Boolean
        val selectorStart: Int
        when {
            line.startsWith("##", hashIndex) -> { exception = false; selectorStart = hashIndex + 2 }
            line.startsWith("#@#", hashIndex) -> { exception = true; selectorStart = hashIndex + 3 }
            else -> return null // #?#, #$#, #%#, $$ and other extended syntaxes
        }
        val domains = line.substring(0, hashIndex)
        val selector = line.substring(selectorStart).trim()
        if (selector.isEmpty() || selector.length > 1024 || selector.contains('{') ||
            selector.contains('}') || selector.startsWith("+js") ||
            proceduralMarkers.any { selector.contains(it) }) return null
        if (domains.any { it == '/' || it == ' ' || it == '*' || it == '$' }) return null
        val out = StringBuilder()
        for (raw in domains.split(',')) {
            val negated = raw.startsWith("~")
            val domain = raw.removePrefix("~").trim().lowercase(Locale.ROOT)
            if (domain.isEmpty() || domain.endsWith(".")) continue
            // A negated domain is an exception for that subdomain of a positive entry.
            out.append(if (exception || negated) '-' else '+').append(domain).append('\t')
                .append(selector).append('\n')
        }
        return out.toString().trimEnd('\n').ifEmpty { null }
    }
}
