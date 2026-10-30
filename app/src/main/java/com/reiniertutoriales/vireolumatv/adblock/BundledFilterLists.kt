package com.reiniertutoriales.vireolumatv.adblock

import com.reiniertutoriales.vireolumatv.utils.BoundedReader
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/** Frozen source data for first-install recovery, composed with the runtime's own preprocessor. */
internal class BundledFilterLists(private val open: (String) -> InputStream) {
    private val manifest by lazy {
        val text = BoundedReader(open("manifest.json").bufferedReader(), 64 * 1024).use { it.readText() }
        JSONObject(text).also { require(it.getInt("version") == 1) }
    }

    fun read(cacheName: String): String? {
        val lists = manifest.getJSONArray("lists")
        val entry = (0 until lists.length()).asSequence().map { lists.getJSONObject(it) }
            .firstOrNull { it.getString("cache") == cacheName } ?: return null
        val main = text(entry.getString("file"))
        if (!entry.getBoolean("ubo")) return main
        val active = FilterListPreprocessor.applyDirectives(main)
        val output = StringBuilder(active)
        val visited = HashSet<String>()
        val includes = entry.getJSONObject("includes")
        fun expand(parent: String, depth: Int) {
            if (depth >= 3) return
            for (name in FilterListPreprocessor.includes(parent)) {
                if (!visited.add(name)) continue
                val part = FilterListPreprocessor.applyDirectives(text(includes.getString(name)))
                if (output.length.toLong() + part.length + 1 > MAX_TEXT_BYTES) throw IOException("Bundled list too large")
                output.append('\n').append(part)
                expand(part, depth + 1)
            }
        }
        expand(active, 0)
        return output.toString()
    }

    private fun text(name: String): String {
        if (!Regex("[a-z0-9-]+\\.filterdata").matches(name)) throw IOException("Invalid bundled list path")
        val info = manifest.getJSONObject("files").getJSONObject(name)
        val size = info.getInt("bytes")
        if (size !in 1..MAX_TEXT_BYTES) throw IOException("Invalid bundled list size")
        val bytes = open(name).use { input -> GZIPInputStream(input).use { stream ->
            val result = ByteArray(size)
            var offset = 0
            while (offset < size) {
                val count = stream.read(result, offset, size - offset)
                if (count < 0) throw IOException("Incomplete bundled list")
                offset += count
            }
            if (stream.read() != -1) throw IOException("Bundled list exceeds declared size")
            result
        } }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        if (hash != info.getString("sha256")) throw IOException("Bundled list checksum mismatch")
        return bytes.toString(Charsets.UTF_8)
    }

    private companion object { const val MAX_TEXT_BYTES = 8 * 1024 * 1024 }
}
