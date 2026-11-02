package com.reiniertutoriales.vireolumatv.webengine.webview

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * A [BlobSink] backed by a private file in [dir], so an arriving blob costs disk, not memory. The
 * file is deleted when its stream closes or the transfer aborts. Opening one also removes files
 * a killed process left behind: none can still be in use after [STALE_MS].
 */
internal class BlobSpool private constructor(private val file: File) : BlobSink {
    private var output: FileOutputStream? = FileOutputStream(file)

    override fun write(bytes: ByteArray) {
        (output ?: throw IOException("Spool closed")).write(bytes)
    }

    override fun finish(): InputStream {
        val out = output ?: throw IOException("Spool closed")
        output = null
        out.close()
        return object : FileInputStream(file) {
            override fun close() {
                try { super.close() } finally { file.delete() }
            }
        }
    }

    override fun abort() {
        try { output?.close() } catch (_: IOException) { }
        output = null
        file.delete()
    }

    companion object {
        const val STALE_MS = 60L * 60 * 1000

        @Throws(IOException::class)
        fun open(dir: File, now: Long = System.currentTimeMillis()): BlobSpool {
            if (!dir.isDirectory && !dir.mkdirs()) throw IOException("No spool directory")
            dir.listFiles()?.forEach { if (now - it.lastModified() > STALE_MS) it.delete() }
            return BlobSpool(File.createTempFile("blob", ".part", dir))
        }
    }
}
