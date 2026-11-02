package com.reiniertutoriales.vireolumatv.singleton

import android.app.Application
import android.graphics.Bitmap
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.model.SiteSettings
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config as RobolectricConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress

@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(application = Application::class, sdk = [28])
class IncognitoFaviconTest {
    @Test fun downloadedPrivateIconNeverReadsOrWritesPersistentCache() = verify(true)
    @Test fun downloadedNormalIconStillPersists() = verify(false)
    @Test fun anIconWhoseRecordFailsIsStillShown() = verify(false, recordFails = true)

    private fun verify(privateMode: Boolean, recordFails: Boolean = false) = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        val config = Config(app.getSharedPreferences("favicon-test", 0))
        config.privateBrowsing = privateMode
        AppContext.init(app, config)
        Favicons.clear()
        val directory = Favicons.directory
        directory.deleteRecursively()
        var reads = 0
        var writes = 0
        val previousDelegate = Favicons.store
        Favicons.store = object : Favicons.Store {
            override fun find(host: String): SiteSettings? { reads++; return null }
            override suspend fun insert(row: SiteSettings) { writes++; if (recordFails) error("disk full") }
            override suspend fun update(row: SiteSettings) { writes++ }
        }
        val icon = ByteArrayOutputStream().also {
            Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val bytes = if (exchange.requestURI.path == "/icon.png") icon
                else "<head><link rel=\"icon\" href=\"/icon.png\" sizes=\"120x120\"></head>".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            assertNotNull(Favicons.get("http://127.0.0.1:${server.address.port}/"))
            assertEquals(if (privateMode) 0 else 1, reads)
            assertEquals(if (privateMode) 0 else 1, writes)
            assertEquals(!privateMode, directory.listFiles()?.isNotEmpty() == true)
        } finally {
            server.stop(0)
            Favicons.store = previousDelegate
            Favicons.clear()
            directory.deleteRecursively()
        }
    }
}
