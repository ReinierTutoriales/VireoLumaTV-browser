package com.reiniertutoriales.vireolumatv.adblock

import java.util.Locale

/**
 * Immutable site-specific element hiding rules built from [FilterListPreprocessor] output.
 * Lookups walk the host and its parent domains; immutable maps are safe to read from the
 * JavaBridge/WebView threads without locking.
 */
class CosmeticFilters private constructor(
    private val hide: Map<String, List<String>>,
    private val allow: Map<String, Set<String>>
) {
    val isEmpty: Boolean get() = hide.isEmpty()

    /** CSS that hides the selectors for [host], or an empty string. */
    fun cssFor(host: String?): String {
        if (host.isNullOrEmpty() || hide.isEmpty()) return ""
        val domains = parentDomains(host.lowercase(Locale.ROOT))
        var selectors: LinkedHashSet<String>? = null
        for (domain in domains) {
            val list = hide[domain] ?: continue
            if (selectors == null) selectors = LinkedHashSet()
            selectors.addAll(list)
        }
        if (selectors == null) return ""
        for (domain in domains) allow[domain]?.let { selectors.removeAll(it) }
        if (selectors.isEmpty()) return ""
        // One rule per selector: an unsupported selector must not invalidate the whole group.
        val css = StringBuilder()
        for (selector in selectors.take(MAX_SELECTORS_PER_PAGE)) {
            css.append(selector).append("{display:none!important}")
        }
        return css.toString()
    }

    companion object {
        private const val MAX_SELECTORS_PER_PAGE = 512
        private const val MAX_DOMAIN_LABELS = 8
        val EMPTY = CosmeticFilters(emptyMap(), emptyMap())

        fun parse(text: String): CosmeticFilters {
            if (text.isEmpty()) return EMPTY
            val hide = HashMap<String, MutableList<String>>()
            val allow = HashMap<String, MutableSet<String>>()
            for (line in text.lineSequence()) {
                if (line.length < 4) continue
                val tab = line.indexOf('\t')
                if (tab < 2) continue
                val domain = line.substring(1, tab)
                val selector = line.substring(tab + 1)
                when (line[0]) {
                    '+' -> hide.getOrPut(domain) { ArrayList(2) }.add(selector)
                    '-' -> allow.getOrPut(domain) { HashSet(2) }.add(selector)
                }
            }
            return CosmeticFilters(hide, allow)
        }

        internal fun parentDomains(host: String): List<String> {
            val result = ArrayList<String>(4)
            var current = host.trimEnd('.')
            while (current.isNotEmpty() && result.size < MAX_DOMAIN_LABELS) {
                result.add(current)
                val dot = current.indexOf('.')
                if (dot < 0) break
                current = current.substring(dot + 1)
            }
            return result
        }
    }
}
