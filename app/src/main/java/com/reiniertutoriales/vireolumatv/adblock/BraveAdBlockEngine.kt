package com.reiniertutoriales.vireolumatv.adblock

import android.net.Uri
import com.brave.adblock.AdBlockClient
import java.io.File

/** [ContentBlockerEngine] backed by the brave ad-block 0.0.4 native library. */
class BraveAdBlockEngine : ContentBlockerEngine {
    override val cacheFileName: String = "adblock_ser.dat"

    override fun compile(filterText: String): ContentBlocker? {
        val client = AdBlockClient()
        return if (client.parse(filterText)) BraveAdBlocker(client) else null
    }

    override fun deserialize(file: File): ContentBlocker? {
        if (!file.exists()) return null
        val client = AdBlockClient()
        return if (client.deserialize(file.absolutePath)) BraveAdBlocker(client) else null
    }
}

private class BraveAdBlocker(private val client: AdBlockClient) : ContentBlocker {
    override fun shouldBlock(url: Uri, type: String?, baseHost: String): Boolean {
        return client.matches(url.toString(), AdblockRequestClassifier.filterOption(type), baseHost)
    }

    override fun serialize(file: File): Boolean = client.serialize(file.absolutePath)

}
