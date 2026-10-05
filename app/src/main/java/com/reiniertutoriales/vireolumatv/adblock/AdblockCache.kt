package com.reiniertutoriales.vireolumatv.adblock

import java.io.File
import java.security.MessageDigest

/** Version/source-scoped caches never reuse compiled rules from a different subscription. */
internal object AdblockCache {
    fun sourceKey(url: String): String = MessageDigest.getInstance("SHA-256")
        .digest(url.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun fileFor(directory: File, engine: ContentBlockerEngine, url: String): File =
        File(directory, "v2-${sourceKey(url)}-${engine.cacheFileName}")

    /** Serialize away from the good cache: rejection, disk-full or cancellation must not truncate it. */
    fun write(file: File, blocker: ContentBlocker): Boolean {
        var temporary: File? = null
        return try {
            temporary = File.createTempFile("adblock-", ".tmp", file.parentFile)
            if (!blocker.serialize(temporary) || temporary.length() == 0L) false
            else temporary.renameTo(file)
        } catch (_: Exception) {
            false
        } finally {
            temporary?.delete()
        }
    }
}
