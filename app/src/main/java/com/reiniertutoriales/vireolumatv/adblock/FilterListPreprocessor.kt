package com.reiniertutoriales.vireolumatv.adblock

/**
 * Filter list text handling that adblock-rust leaves to the embedder.
 *
 * - uBlock Origin preprocessor directives (`!#if env_…`, `!#else`, `!#endif`): adblock-rust treats
 *   them as comments and would apply rules meant for Firefox/Safari or for capabilities the
 *   WebView does not have. [applyDirectives] keeps the branches that apply to this browser.
 * - `!#include file.txt`: sub-lists of the uBlock filters, fetched by the downloader.
 * - `$popup` / `$popunder` rules: adblock-rust has no popup request type, so they are rewritten to
 *   `$document` and compiled into a separate small engine that only screens new windows.
 */
object FilterListPreprocessor {
    class Result(val popupRules: String)

    /** Environment of an Android WebView browser for uBO `!#if` expressions. */
    private val environment = mapOf(
        "env_chromium" to true,
        "env_mobile" to true,
        "cap_user_stylesheet" to true,
        "env_firefox" to false,
        "env_safari" to false,
        "env_edge" to false,
        "env_mv3" to false,
        "ext_ubol" to false,
        "ext_devbuild" to false,
        "cap_html_filtering" to false,
        "cap_ipaddress" to false,
        "false" to false,
        "true" to true
    )

    fun extract(text: String): Result {
        val popup = StringBuilder()
        forEachLine(text) { line ->
            if (line.isEmpty() || line[0] == '!' || line[0] == '[' || isCosmetic(line)) return@forEachLine
            popupLine(line)?.let { popup.append(it).append('\n') }
        }
        return Result(popup.toString())
    }

    /** Drops the lines of `!#if` branches that do not apply. Unbalanced directives are tolerated. */
    fun applyDirectives(text: String): String {
        if (!text.contains("!#if")) return text
        val out = StringBuilder(text.length)
        // Each entry: is this branch active (all enclosing branches included)?
        val stack = ArrayDeque<Boolean>()
        forEachLine(text) { line ->
            when {
                line.startsWith("!#if ") -> {
                    val parent = stack.lastOrNull() ?: true
                    stack.addLast(parent && evaluate(line.substring(5).trim()))
                }
                line.startsWith("!#else") -> if (stack.isNotEmpty()) {
                    val current = stack.removeLast()
                    val parent = stack.lastOrNull() ?: true
                    stack.addLast(parent && !current)
                }
                line.startsWith("!#endif") -> stack.removeLastOrNull()
                stack.lastOrNull() != false -> out.append(line).append('\n')
            }
        }
        return out.toString()
    }

    /** Relative file names of `!#include` directives that survive [applyDirectives]. */
    fun includes(text: String): List<String> {
        val result = ArrayList<String>()
        forEachLine(text) { line ->
            if (line.startsWith("!#include ")) {
                val name = line.substring(10).trim()
                // Same-directory sub-lists only: never follow absolute or parent paths.
                if (name.isNotEmpty() && !name.contains("://") && !name.startsWith("/") && !name.contains(".."))
                    result.add(name)
            }
        }
        return result
    }

    /** `!`, `&&`, `||` and parentheses over [environment] tokens; unknown tokens are false. */
    internal fun evaluate(expression: String): Boolean {
        val tokens = Regex("""\(|\)|!|&&|\|\||[A-Za-z0-9_]+""").findAll(expression).map { it.value }.toList()
        var index = 0
        fun or(): Boolean {
            fun and(): Boolean {
                fun unary(): Boolean {
                    val token = tokens.getOrNull(index++) ?: return false
                    return when (token) {
                        "!" -> !unary()
                        "(" -> or().also { if (tokens.getOrNull(index) == ")") index++ }
                        else -> environment[token] ?: false
                    }
                }
                var value = unary()
                while (tokens.getOrNull(index) == "&&") { index++; value = unary() && value }
                return value
            }
            var value = and()
            while (tokens.getOrNull(index) == "||") { index++; value = and() || value }
            return value
        }
        return or()
    }

    /** `rule$popup[,opts]` -> `rule$document[,opts]`; null when the line is not a popup rule. */
    internal fun popupLine(line: String): String? {
        // Regex rules can contain '$' inside the pattern; they are rare in popup sections.
        if (line.startsWith("/") && line.endsWith("/")) return null
        val dollar = line.lastIndexOf('$')
        if (dollar <= 0 || dollar == line.length - 1) return null
        val options = line.substring(dollar + 1).split(',')
        if (options.none { it == "popup" || it == "popunder" }) return null
        val rewritten = options.filter { it != "popup" && it != "popunder" && it != "document" && it != "doc" }
            .toMutableList()
        rewritten.add(0, "document")
        return line.substring(0, dollar + 1) + rewritten.joinToString(",")
    }

    /** `##`, `#@#`, `#?#`, `#$#`, `#%#` and `##+js(...)`; network rules may contain a plain '#'. */
    private fun isCosmetic(line: String): Boolean {
        var index = line.indexOf('#')
        while (index >= 0 && index < line.length - 1) {
            val next = line[index + 1]
            if (next == '#') return true
            if ((next == '@' || next == '?' || next == '$' || next == '%') &&
                index + 2 < line.length && line[index + 2] == '#') return true
            index = line.indexOf('#', index + 1)
        }
        return false
    }

    private inline fun forEachLine(text: String, action: (String) -> Unit) {
        var start = 0
        val length = text.length
        while (start < length) {
            var end = text.indexOf('\n', start)
            if (end < 0) end = length
            action(text.substring(start, end).trim())
            start = end + 1
        }
    }
}
