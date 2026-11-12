package com.reiniertutoriales.vireolumatv.adblock

import android.net.Uri
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * [ContentBlockerEngine] backed by Brave's adblock-rust (MPL-2.0) through native/adblock-jni.
 * It understands uBlock Origin syntax (`3p`, `xhr`, `badfilter`, `redirect`, `##+js(...)`,
 * entities such as `site.*`), is safe to query from several threads and matches a request in
 * microseconds instead of the ~0.1 ms of the former ad-block 0.0.4 engine.
 *
 * [resources] supplies the redirect and scriptlet resources (see [AdblockResources]); they are not
 * part of the serialized engine and are attached again on every load.
 */
class RustAdBlockEngine(private val resources: () -> String) : ContentBlockerEngine {
    override val cacheFileName: String = "adblock_rust.dat"

    override fun compile(filterLists: List<String>): ContentBlocker? = compile(filterLists, emptyList())

    override fun compile(filterLists: List<String>, trustedLists: List<Boolean>): ContentBlocker? {
        if (!RustAdblock.isAvailable) return null
        val trusted = BooleanArray(filterLists.size) { trustedLists.getOrElse(it) { false } }
        val handle = RustAdblock.nativeCompile(filterLists.toTypedArray(), trusted, resources())
        return if (handle == 0L) null else RustContentBlocker(handle)
    }

    override fun deserialize(file: File): ContentBlocker? {
        if (!RustAdblock.isAvailable || !file.exists()) return null
        val handle = RustAdblock.nativeDeserialize(file.path, resources())
        return if (handle == 0L) null else RustContentBlocker(handle)
    }
}

private class RustContentBlocker(private var handle: Long) : ContentBlocker {
    // Queries share the read lock; release() waits for them before freeing native memory.
    private val lifecycle = ReentrantReadWriteLock()

    override val isConcurrent: Boolean get() = true

    // The caller already has the site host. Avoid constructing and parsing a temporary Uri
    // on every request; JNI consumes the page URL as a String in both entry points.
    override fun shouldBlock(url: Uri, type: String?, baseHost: String): Boolean = lifecycle.read {
        handle != 0L && RustAdblock.nativeCheck(
            handle, url.toString(), "https://$baseHost/", requestType(type)
        ) != ContentBlocker.ALLOW
    }

    override fun check(url: Uri, type: String?, page: Uri): Int = lifecycle.read {
        if (handle == 0L) ContentBlocker.ALLOW
        else RustAdblock.nativeCheck(handle, url.toString(), page.toString(), requestType(type))
    }

    override fun verdict(url: Uri, type: String?, page: Uri, method: String): AdVerdict = lifecycle.read {
        if (handle == 0L) return@read AdVerdict.ALLOW
        val source = page.host?.let { "https://$it/" } ?: page.toString()
        when (val answer = RustAdblock.nativeVerdict(handle, url.toString(), source, requestType(type), method)) {
            "" -> AdVerdict.ALLOW
            BLOCK_ANSWER -> AdVerdict.BLOCK
            else -> AdVerdict.redirect(answer)
        }
    }

    override fun pageFilters(pageUrl: String): String = lifecycle.read {
        if (handle == 0L) "" else RustAdblock.nativeCosmetic(handle, pageUrl)
    }

    override fun hiddenSelectors(pageUrl: String, classes: String, ids: String): String = lifecycle.read {
        if (handle == 0L) "[]" else RustAdblock.nativeHiddenSelectors(handle, pageUrl, classes, ids)
    }

    // The read lock keeps the rules alive while they are written; checks keep running meanwhile.
    override fun serialize(file: File): Boolean = lifecycle.read {
        handle != 0L && RustAdblock.nativeSerialize(handle, file.path)
    }

    override fun release() = lifecycle.write {
        val current = handle
        handle = 0L
        if (current != 0L) RustAdblock.nativeDestroy(current)
    }

    private companion object {
        /** nativeVerdict's answer for a block without a `$redirect` resource; any other non-empty answer is one. */
        const val BLOCK_ANSWER = "!"

        /** [AdblockRequestClassifier] names to adblock-rust request types. */
        fun requestType(type: String?): String = when (type) {
            "style" -> "stylesheet"
            "subdocument" -> "sub_frame"
            "document" -> "main_frame"
            null, "unknown" -> "other"
            else -> type
        }
    }
}
