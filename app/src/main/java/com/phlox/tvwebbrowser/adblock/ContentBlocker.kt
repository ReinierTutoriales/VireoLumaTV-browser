package com.phlox.tvwebbrowser.adblock

import android.net.Uri
import java.io.File

/**
 * Compiled set of blocking rules produced by a [ContentBlockerEngine].
 * Implementations are not required to be thread-safe; callers serialize access.
 */
interface ContentBlocker {
    /** Returns true if the request to [url] made from a page on [baseHost] must be blocked. */
    fun shouldBlock(url: Uri, type: String?, baseHost: String): Boolean

    /** Writes the compiled rules to [file]; returns false if they could not be written. */
    fun serialize(file: File): Boolean
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
