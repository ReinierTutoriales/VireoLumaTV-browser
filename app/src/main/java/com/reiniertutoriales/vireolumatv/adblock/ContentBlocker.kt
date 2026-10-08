package com.reiniertutoriales.vireolumatv.adblock

import android.net.Uri
import java.io.File

/**
 * Compiled set of blocking rules produced by a [ContentBlockerEngine].
 * Implementations are not required to be thread-safe unless [isConcurrent]; callers serialize
 * access to the others.
 */
interface ContentBlocker {
    /** Returns true if the request to [url] made from a page on [baseHost] must be blocked. */
    fun shouldBlock(url: Uri, type: String?, baseHost: String): Boolean

    /** Writes the compiled rules to [file]; returns false if they could not be written. */
    fun serialize(file: File): Boolean

    /** True when [check], [redirect] and [pageFilters] may run on several threads at once. */
    val isConcurrent: Boolean get() = false

    /** [ALLOW], [BLOCK] or [BLOCK_REDIRECT] for a request made by a document at [page]. */
    fun check(url: Uri, type: String?, page: Uri): Int =
        if (shouldBlock(url, type, page.host ?: "")) BLOCK else ALLOW

    /** `data:` URL of the `$redirect` resource for a blocked request, if the rules name one. */
    fun redirect(url: Uri, type: String?, page: Uri): String? = null

    /** Element hiding and scriptlets for a document URL as JSON (see adblock-jni), or "". */
    fun pageFilters(pageUrl: String): String = ""

    /** Frees native memory once no caller can use this instance any more. */
    fun release() {}

    companion object {
        const val ALLOW = 0
        const val BLOCK = 1
        const val BLOCK_REDIRECT = 2
    }
}

/** Engine able to compile filter lists and restore compiled rules from its own cache format. */
interface ContentBlockerEngine {
    /** Cache file name; engine specific because each engine has its own serialized format. */
    val cacheFileName: String

    /** Compiles [filterText]; returns null if the engine rejects the input. */
    fun compile(filterText: String): ContentBlocker?

    /** Restores rules previously written by [ContentBlocker.serialize]; returns null if unusable. */
    fun deserialize(file: File): ContentBlocker?
}
