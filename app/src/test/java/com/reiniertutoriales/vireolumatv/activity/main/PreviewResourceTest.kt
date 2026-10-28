package com.reiniertutoriales.vireolumatv.activity.main

import android.app.Application
import android.graphics.Bitmap
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.model.SiteSettings
import com.reiniertutoriales.vireolumatv.model.BrowserTab
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
class PreviewResourceTest {
    @Test fun legacyLargePreviewIsBoundedAndDoesNotRetainBackgroundBitmap() {
        val app = RuntimeEnvironment.getApplication()
        AppContext.init(app, Config(app.getSharedPreferences("preview-test", 0)))
        val directory = File(app.cacheDir, BrowserTab.PREVIEW_DIR).apply { mkdirs() }
        val file = File(directory, "large-preview.png")
        Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888).also { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        try {
            val tab = BrowserTab(thumbnailHash = "large-preview")
            val preview = tab.loadPreview()
            assertNotNull(preview)
            assertTrue(preview!!.width <= 480 && preview.height <= 480)
            assertNull(tab.thumbnail)
        } finally { file.delete() }
    }

    @Test fun popupPolicyUsesOnlyConfigurationForTheCurrentHost() {
        val app = RuntimeEnvironment.getApplication()
        AppContext.init(app, Config(app.getSharedPreferences("popup-policy-test", 0)))
        val model = TabStore()
        try {
            val tab = BrowserTab(url = "https://one.test")
            tab.cachedSiteSettings = SiteSettings("one.test").apply { popupBlockLevel = SiteSettings.POPUPS_ALLOWED }
            assertEquals(SiteSettings.POPUPS_ALLOWED, model.popupBlockingLevel(tab))
            tab.url = "https://two.test"
            assertEquals(SiteSettings.DEFAULT_POPUP_POLICY, model.popupBlockingLevel(tab))
        } finally { model.clear() }
    }
}
