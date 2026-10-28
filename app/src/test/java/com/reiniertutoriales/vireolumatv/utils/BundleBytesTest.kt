package com.reiniertutoriales.vireolumatv.utils

import android.app.Application
import android.os.Bundle
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class BundleBytesTest {
    @Test fun bundleSurvivesEncodeAndDecode() {
        val decoded = BundleBytes.decode(BundleBytes.encode(Bundle().apply {
            putString("url", "https://example.test")
            putByteArray("state", byteArrayOf(1, 2, 3))
        }))
        assertEquals("https://example.test", decoded!!.getString("url"))
        assertArrayEquals(byteArrayOf(1, 2, 3), decoded.getByteArray("state"))
    }

    @Test fun legacyJsonKeepsItsValuesAndStoresNumberArraysAsBytes() {
        val bundle = BundleBytes.fromLegacyJson(JSONObject(
            """{"s":"x","i":7,"b":true,"d":1.5,"n":{"k":"v"},"bytes":[1,2,255],"names":["a","b"],"empty":[],"nothing":null}"""))
        assertEquals("x", bundle.getString("s"))
        assertEquals(7, bundle.getInt("i"))
        assertTrue(bundle.getBoolean("b"))
        assertEquals(1.5, bundle.getDouble("d"), 0.0)
        assertEquals("v", bundle.getBundle("n")!!.getString("k"))
        assertArrayEquals(byteArrayOf(1, 2, -1), bundle.getByteArray("bytes"))
        assertArrayEquals(arrayOf("a", "b"), bundle.getStringArray("names"))
        assertFalse(bundle.containsKey("empty"))
        assertFalse(bundle.containsKey("nothing"))
    }

    @Test fun md5NameKeepsTheStoredFormatWithoutLeadingZeros() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", md5Name("abc".toByteArray()))
        // MD5("jk8ssl") starts with zero bytes: the stored name drops them.
        assertFalse(md5Name("jk8ssl".toByteArray()).startsWith("0"))
    }
}
