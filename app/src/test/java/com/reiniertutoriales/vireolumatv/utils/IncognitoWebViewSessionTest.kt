package com.reiniertutoriales.vireolumatv.utils

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class IncognitoWebViewSessionTest {
    @Test fun staleDataIsRemovedBeforeSuffixAndRecreationDoesNotWipeActiveSession() {
        val context = RuntimeEnvironment.getApplication()
        val privateDirectory = File(context.filesDir.parentFile, "app_webview_incognito").apply { mkdirs() }
        val normalDirectory = File(context.filesDir.parentFile, "app_webview").apply { mkdirs() }
        File(privateDirectory, "old-cookie").writeText("private")
        File(normalDirectory, "cookie").writeText("normal")
        val cache = File(context.cacheDir, "webview_incognito").apply { mkdirs() }
        File(cache, "old-cache").writeText("private")
        val session = IncognitoWebViewSession()
        var calls = 0
        session.configure(context) {
            calls++
            assertEquals("incognito", it)
            assertFalse(privateDirectory.exists())
            assertFalse(cache.exists())
            assertEquals("normal", File(normalDirectory, "cookie").readText())
        }
        privateDirectory.mkdirs()
        val active = File(privateDirectory, "active-cookie").apply { writeText("active") }
        session.configure(context) { fail("Suffix configured twice") }
        assertEquals(1, calls)
        assertTrue(active.exists())
        session.clear(context)
        assertFalse(active.exists())
        assertTrue(File(normalDirectory, "cookie").exists())
    }

    @Test fun clearRemovesBothPrivateCacheNamesButPreservesNormalWebViewCache() {
        val context = RuntimeEnvironment.getApplication()
        val normal = File(context.cacheDir, "WebView").apply { mkdirs() }
        val normalCookie = File(normal, "normal-cache").apply { writeText("keep") }
        val privateUpper = File(context.cacheDir, "WebView_incognito").apply { mkdirs() }
        val privateLower = File(context.cacheDir, "webview_incognito").apply { mkdirs() }
        File(privateUpper, "old-cache").writeText("private")
        File(privateLower, "old-cache").writeText("private")
        IncognitoWebViewSession().clear(context)
        assertFalse(privateUpper.exists())
        assertFalse(privateLower.exists())
        assertEquals("keep", normalCookie.readText())
    }

    @Test fun failedSuffixConfigurationCanBeRetried() {
        val context = RuntimeEnvironment.getApplication()
        val session = IncognitoWebViewSession()
        try { session.configure(context) { error("provider already initialized") }; fail() }
        catch (_: IllegalStateException) { }
        var retried = false
        session.configure(context) { retried = true }
        assertTrue(retried)
    }

    @Test fun legacyBackupReplacesDeadPrivateSessionAndIsOptional() {
        val context = RuntimeEnvironment.getApplication()
        val root = requireNotNull(context.filesDir.parentFile)
        val data = File(root, "app_webview").apply { mkdirs() }
        File(data, "cookie").writeText("private")
        File(root, "app_webview_backup").apply { mkdirs() }.let { File(it, "cookie").writeText("normal") }
        val cache = File(context.cacheDir, "WebView").apply { mkdirs() }
        File(cache, "entry").writeText("normal-without-backup")

        restoreLegacyWebViewBackups(listOf(data, cache))

        assertEquals("normal", File(data, "cookie").readText())
        assertFalse(File(root, "app_webview_backup").exists())
        assertEquals("normal-without-backup", File(cache, "entry").readText())
        restoreLegacyWebViewBackups(listOf(data, cache))
        assertEquals("normal", File(data, "cookie").readText())
    }
}
