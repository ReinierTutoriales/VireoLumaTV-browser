package com.reiniertutoriales.vireolumatv.adblock

import com.reiniertutoriales.vireolumatv.utils.BoundedReader
import java.io.File
import java.io.IOException

/**
 * `!#include` sub-lists of one uBlock list. Each sub-list keeps its own text and validators: the
 * main list's validators say nothing about them, so an HTTP 304 for the main list must not freeze them.
 *
 * Includes are followed up to [MAX_DEPTH] levels; a name is expanded once, so cycles end there.
 * Nested names resolve against the same base URL as the main list.
 */
internal class SubListCache(private val dir: File, private val prefix: String) {

    /**
     * Composes [main] with its sub-lists, revalidating each one. A sub-list that cannot be fetched and
     * has no saved copy throws; the caller then keeps the last complete generation.
     */
    fun refresh(
        main: String,
        base: String,
        fetch: (String, AdblockListDownloader.Validators?) -> AdblockListDownloader.Result
    ): String {
        val text = FilterListPreprocessor.applyDirectives(main)
        val out = StringBuilder(text.length * 2).append(text)
        val kept = HashSet<String>()
        expand(text, 0, base, fetch, out, kept)
        pruneUnused(kept)
        return out.toString()
    }

    private fun expand(
        text: String,
        depth: Int,
        base: String,
        fetch: (String, AdblockListDownloader.Validators?) -> AdblockListDownloader.Result,
        out: StringBuilder,
        kept: MutableSet<String>
    ) {
        for (name in FilterListPreprocessor.includes(text)) {
            if (depth >= MAX_DEPTH || !kept.add(name)) continue
            val body = fetchOne(name, base, fetch)
            out.append('\n').append(body)
            expand(body, depth + 1, base, fetch, out, kept)
        }
    }

    private fun fetchOne(
        name: String,
        base: String,
        fetch: (String, AdblockListDownloader.Validators?) -> AdblockListDownloader.Result
    ): String {
        val saved = readText(name)
        val validators = if (saved != null) readValidators(validatorsFile(name)) else null
        return when (val result = fetch(base + name, validators)) {
            AdblockListDownloader.Result.NotModified ->
                saved ?: throw IOException("Sub-list not modified without a saved copy: $name")
            is AdblockListDownloader.Result.Downloaded -> {
                val text = FilterListPreprocessor.applyDirectives(result.text)
                // Same content: only the validators move on, the saved text stays as it is.
                val written = text == saved || AdblockCache.writeText(textFile(name), text)
                writeValidators(validatorsFile(name), if (written) result.validators else null)
                text
            }
        }
    }

    private fun pruneUnused(kept: Set<String>) {
        val keep = kept.mapTo(HashSet()) { textFile(it).name }
        dir.listFiles()?.forEach { file ->
            if (file.name.startsWith("$prefix.inc.") && file.name.removeSuffix(".meta") !in keep) file.delete()
        }
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
        const val MAX_DEPTH = 3
        const val MAX_TEXT_CHARS = 8 * 1024 * 1024
        val UNSAFE_NAME_CHARS = Regex("[^A-Za-z0-9._-]")
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
