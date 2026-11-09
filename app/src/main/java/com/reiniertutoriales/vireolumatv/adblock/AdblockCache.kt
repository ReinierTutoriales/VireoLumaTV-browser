package com.reiniertutoriales.vireolumatv.adblock

import java.io.File
import java.security.MessageDigest

/** Version/source-scoped caches never reuse compiled rules from a different subscription. */
internal object AdblockCache {
    /**
     * Bump when the filter lists, their preprocessing or their compile options change: compiled
     * rules are not revalidated. v4: per-list permission for `trusted-*` scriptlets; v5: native
     * container with the `$to=`/`$denyallow=` rules.
     */
    const val REVISION = "v5"

    private val HEX = "0123456789abcdef".toCharArray()

    fun sourceKey(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
        val hex = CharArray(digest.size * 2)
        for (i in digest.indices) {
            val value = digest[i].toInt() and 0xff
            hex[i * 2] = HEX[value ushr 4]
            hex[i * 2 + 1] = HEX[value and 0x0f]
        }
        return String(hex)
    }

    fun fileFor(directory: File, engine: ContentBlockerEngine, url: String): File =
        File(directory, "$REVISION-${sourceKey(url)}-${engine.cacheFileName}")

    /** Serialize away from the good cache: rejection, disk-full or cancellation must not truncate it. */
    fun write(file: File, blocker: ContentBlocker): Boolean = write(file) { blocker.serialize(it) }

    fun writeText(file: File, text: String): Boolean = write(file) {
        it.writeText(text)
        true
    }

    private fun write(file: File, serialize: (File) -> Boolean): Boolean {
        var temporary: File? = null
        return try {
            temporary = File.createTempFile("adblock-", ".tmp", file.parentFile)
            if (!serialize(temporary) || temporary.length() == 0L) false
            else temporary.renameTo(file)
        } catch (_: Exception) {
            false
        } finally {
            temporary?.delete()
        }
    }
}
