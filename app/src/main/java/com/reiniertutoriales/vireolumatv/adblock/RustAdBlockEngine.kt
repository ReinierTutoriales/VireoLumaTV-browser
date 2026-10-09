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

    override fun compile(filterLists: List<String>): ContentBlocker? {
        if (!RustAdblock.isAvailable) return null
        val handle = RustAdblock.nativeCompile(filterLists.toTypedArray(), resources())
        return if (handle == 0L) null else RustContentBlocker(handle)
    }

    override fun deserialize(file: File): ContentBlocker? {
        if (!RustAdblock.isAvailable || !file.exists()) return null
        val handle = RustAdblock.nativeDeserialize(file.readBytes(), resources())
        return if (handle == 0L) null else RustContentBlocker(handle)
    }
}

private class RustContentBlocker(private var handle: Long) : ContentBlocker {
    // Queries share the read lock; release() waits for them before freeing native memory.
    private val lifecycle = ReentrantReadWriteLock()

    override val isConcurrent: Boolean get() = true

    override fun shouldBlock(url: Uri, type: String?, baseHost: String): Boolean =
        check(url, type, Uri.parse("https://$baseHost/")) != ContentBlocker.ALLOW

    override fun check(url: Uri, type: String?, page: Uri): Int = lifecycle.read {
        if (handle == 0L) ContentBlocker.ALLOW
        else RustAdblock.nativeCheck(handle, url.toString(), page.toString(), requestType(type))
    }

    override fun redirect(url: Uri, type: String?, page: Uri): String? = lifecycle.read {
        if (handle == 0L) null
        else RustAdblock.nativeRedirect(handle, url.toString(), page.toString(), requestType(type)).ifEmpty { null }
    }

    override fun pageFilters(pageUrl: String): String = lifecycle.read {
        if (handle == 0L) "" else RustAdblock.nativeCosmetic(handle, pageUrl)
    }

    override fun hiddenSelectors(pageUrl: String, classes: String, ids: String): String = lifecycle.read {
        if (handle == 0L) "[]" else RustAdblock.nativeHiddenSelectors(handle, pageUrl, classes, ids)
    }

    override fun serialize(file: File): Boolean {
        val data = lifecycle.read { if (handle == 0L) ByteArray(0) else RustAdblock.nativeSerialize(handle) }
        if (data.isEmpty()) return false
        file.writeBytes(data)
        return true
    }

    override fun release() = lifecycle.write {
        val current = handle
        handle = 0L
        if (current != 0L) RustAdblock.nativeDestroy(current)
    }

    private companion object {
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
