package com.phlox.tvwebbrowser.adblock

import android.net.Uri
import com.brave.adblock.AdBlockClient
import com.brave.adblock.AdBlockClient.FilterOption
import com.brave.adblock.Utils
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
        val filterOption = try {
            mapRequestToFilterOption(url, type)
        } catch (e: Exception) {
            return false
        }
        return client.matches(url.toString(), filterOption, baseHost)
    }

    override fun serialize(file: File): Boolean = client.serialize(file.absolutePath)

    private fun mapRequestToFilterOption(url: Uri?, type: String?): FilterOption? {
        if (type != null) {
            if (type == "image" || type.contains("image/")) {
                return FilterOption.IMAGE
            }
            if (type == "style" || type.contains("/css")) {
                return FilterOption.CSS
            }
            if (type == "script" || type.contains("javascript")) {
                return FilterOption.SCRIPT
            }
            if (type.contains("video/")) {
                return FilterOption.OBJECT
            }
        }
        if (url != null) {
            if (Utils.uriHasExtension(url, "css")) {
                return FilterOption.CSS
            }
            if (Utils.uriHasExtension(url, "js")) {
                return FilterOption.SCRIPT
            }
            if (Utils.uriHasExtension(
                    url,
                    "png",
                    "jpg",
                    "jpeg",
                    "webp",
                    "svg",
                    "gif",
                    "bmp",
                    "tiff"
                )
            ) {
                return FilterOption.IMAGE
            }
            if (Utils.uriHasExtension(url, "mp4", "mov", "avi")) {
                return FilterOption.OBJECT
            }
        }
        return FilterOption.UNKNOWN
    }
}
