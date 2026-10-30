package com.reiniertutoriales.vireolumatv.adblock

import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class BundledFilterListsTest {
    private class Fixture {
        val streams = HashMap<String, ByteArray>()
        val opened = ArrayList<String>()
        val files = JSONObject()
        val entry = JSONObject().put("cache", "list.txt").put("ubo", true).put("file", "main.txt.gz")
            .put("includes", JSONObject().put("mobile.txt", "mobile.txt.gz"))
        fun text(name: String, text: String) {
            val bytes = text.toByteArray()
            streams[name] = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()
            files.put(name, JSONObject().put("bytes", bytes.size).put("sha256",
                MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }))
        }
        fun reader(): BundledFilterLists {
            streams["manifest.json"] = JSONObject().put("version", 1).put("lists", JSONArray().put(entry))
                .put("files", files).toString().toByteArray()
            return BundledFilterLists { name -> opened.add(name); checkNotNull(streams[name]).inputStream() }
        }
    }

    @Test fun assetsAreLazyAndOnlyActiveIncludesAreReadWithRuntimePreprocessing() {
        val f = Fixture()
        f.text("main.txt.gz", "! Title: uBlock test\n||common.test^\n!#if env_mobile\n!#include mobile.txt\n!#endif\n!#if env_firefox\n!#include missing-firefox.txt\n!#endif\n")
        f.text("mobile.txt.gz", "||mobile-ad.test^\n!#include mobile.txt\n")
        val reader = f.reader()
        assertTrue(f.opened.isEmpty())
        val text = requireNotNull(reader.read("list.txt"))
        assertTrue(text.contains("||common.test^"))
        assertTrue(text.contains("||mobile-ad.test^"))
        assertFalse(text.contains("missing-firefox"))
        assertEquals(listOf("manifest.json", "main.txt.gz", "mobile.txt.gz"), f.opened)
        assertNull(reader.read("custom.txt"))
        assertEquals(3, f.opened.size)
    }

    @Test fun corruptedOrOversizedDataIsRejectedBeforeCompilation() {
        for (wrongSize in listOf(false, true)) {
            val f = Fixture()
            f.text("main.txt.gz", "||ad.test^\n")
            val info = f.files.getJSONObject("main.txt.gz")
            if (wrongSize) info.put("bytes", 1) else info.put("sha256", "incorrect")
            try {
                f.reader().read("list.txt")
                fail("Corrupt snapshot must be rejected")
            } catch (_: IOException) { }
        }
    }

    @Test fun packagedSevenListsArePresentValidAndFullyComposedWithoutNetwork() {
        val assets = VireoLumaTVApp.instance.assets
        val manifest = JSONObject(assets.open("adblock/baseline/manifest.json").bufferedReader().use { it.readText() })
        val lists = manifest.getJSONArray("lists")
        assertEquals(7, lists.length())
        val reader = BundledFilterLists { assets.open("adblock/baseline/$it") }
        for (i in 0 until lists.length()) {
            val entry = lists.getJSONObject(i)
            val text = requireNotNull(reader.read(entry.getString("cache")))
            assertTrue(AdblockFilterListValidator.isValid(text, !entry.getBoolean("ubo")))
            if (entry.getBoolean("ubo")) assertFalse(text.contains("!#if "))
        }
    }
}
