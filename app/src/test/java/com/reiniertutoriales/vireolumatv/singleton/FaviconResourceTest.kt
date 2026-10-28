package com.reiniertutoriales.vireolumatv.singleton

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.model.SiteSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config as RobolectricConfig
import java.io.File

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(application = Application::class, sdk = [28])
class FaviconResourceTest {
    @Test fun oversizedStoredIconIsSampledAndDatabaseReadRunsOffMain() = withCache { directory ->
        val file = File(directory, "large.png")
        Bitmap.createBitmap(2048, 1024, Bitmap.Config.ARGB_8888).also { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        var readOnMain = true
        Favicons.store = object : Favicons.Store {
            override fun find(host: String): SiteSettings {
                readOnMain = Looper.myLooper() == Looper.getMainLooper()
                return SiteSettings(host).apply { favicon = file.name }
            }
        }
        val icon = runBlocking { Favicons.get("https://sample.test") }
        assertNotNull(icon)
        assertTrue(icon!!.width <= 256 && icon.height <= 256)
        assertFalse(readOnMain)
        assertSame(icon, runBlocking { Favicons.get("https://SAMPLE.test") })
    }

    @Test fun cancellationIsPropagatedInsteadOfStartingNetworkDiscovery() = withCache {
        Favicons.store = object : Favicons.Store {
            override fun find(host: String): SiteSettings? { throw CancellationException("row left screen") }
        }
        var cancelled = false
        try { runBlocking { Favicons.get("https://cancelled.invalid") } }
        catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
    }

    private fun withCache(test: (File) -> Unit) {
        val app = RuntimeEnvironment.getApplication()
        val config = Config(app.getSharedPreferences("favicon-resource-test", 0)).apply { incognitoMode = false }
        AppContext.init(app, config)
        val previous = Favicons.store
        val directory = Favicons.directory
        directory.deleteRecursively()
        directory.mkdirs()
        Favicons.clear()
        try { test(directory) } finally {
            Favicons.store = previous
            Favicons.clear()
            directory.deleteRecursively()
        }
    }
}
