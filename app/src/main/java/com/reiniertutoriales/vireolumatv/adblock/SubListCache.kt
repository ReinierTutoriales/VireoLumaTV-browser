package com.reiniertutoriales.vireolumatv.adblock

import com.reiniertutoriales.vireolumatv.utils.BoundedReader
import java.io.File
import java.io.IOException

/**
 * `!#include` sub-lists of one uBlock list. Each sub-list keeps its own text and validators: the
 * main list's validators say nothing about them, so an HTTP 304 for the main list must not freeze them.
 */
internal class SubListCache(private val dir: File, private val prefix: String) {
    /** [changed] is true when a sub-list was downloaded, so the compiled rules are stale. */
    class Composed(val text: String, val changed: Boolean)

    /** Revalidates every sub-list of [main]. A sub-list that cannot be fetched and has no saved copy throws. */
    fun refresh(
        main: String,
        base: String,
        fetch: (String, AdblockListDownloader.Validators?) -> AdblockListDownloader.Result
    ): Composed {
        val text = FilterListPreprocessor.applyDirectives(main)
        val names = FilterListPreprocessor.includes(text)
        if (names.isEmpty()) return Composed(text, false)
        var changed = false
        val out = StringBuilder(text.length * 2).append(text)
        for (name in names) {
            val saved = readText(name)
            val validators = if (saved != null) readValidators(validatorsFile(name)) else null
            val body = when (val result = fetch(base + name, validators)) {
                AdblockListDownloader.Result.NotModified ->
                    saved ?: throw IOException("Sub-list not modified without a saved copy: $name")
                is AdblockListDownloader.Result.Downloaded -> {
                    val downloaded = FilterListPreprocessor.applyDirectives(result.text)
                    if (AdblockCache.writeText(textFile(name), downloaded)) {
                        writeValidators(validatorsFile(name), result.validators)
                    } else {
                        validatorsFile(name).delete()
                    }
                    changed = true
                    downloaded
                }
            }
            out.append('\n').append(body)
        }
        return Composed(out.toString(), changed)
    }

    /** [main] composed from the saved sub-lists only; null when one of them is missing or unreadable. */
    fun cached(main: String): String? {
        val text = FilterListPreprocessor.applyDirectives(main)
        val names = FilterListPreprocessor.includes(text)
        if (names.isEmpty()) return text
        val out = StringBuilder(text.length * 2).append(text)
        for (name in names) out.append('\n').append(readText(name) ?: return null)
        return out.toString()
    }

    private fun textFile(name: String) = File(dir, "$prefix.inc.${name.replace(UNSAFE_NAME_CHARS, "_")}")

    private fun validatorsFile(name: String) = File(textFile(name).path + ".meta")

    private fun readText(name: String): String? {
        val file = textFile(name)
        if (!file.exists()) return null
        return try {
            BoundedReader(file.bufferedReader(), MAX_TEXT_CHARS).use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        val UNSAFE_NAME_CHARS = Regex("[^A-Za-z0-9._-]")
        const val MAX_TEXT_CHARS = 8 * 1024 * 1024
    }
}

/** HTTP validators saved next to a list; a file that does not parse counts as no validators. */
internal fun readValidators(file: File): AdblockListDownloader.Validators? = try {
    if (!file.exists() || file.length() > 4096) null else {
        val lines = file.readLines()
        val url = lines.getOrNull(0)?.takeIf { it.isNotEmpty() }
        val etag = lines.getOrNull(1)?.takeIf { it.isNotEmpty() }
        val lastModified = lines.getOrNull(2)?.takeIf { it.isNotEmpty() }
        if (url == null || (etag == null && lastModified == null)) null
        else AdblockListDownloader.Validators(url, etag, lastModified)
    }
} catch (e: Exception) {
    null
}

internal fun writeValidators(file: File, validators: AdblockListDownloader.Validators?) {
    if (validators == null) {
        file.delete()
        return
    }
    // Header values cannot contain line breaks; keep the file line based.
    val text = listOf(validators.url, validators.etag ?: "", validators.lastModified ?: "")
        .joinToString("\n") { it.replace('\n', ' ').replace('\r', ' ') }
    if (!AdblockCache.writeText(file, text)) file.delete()
}
