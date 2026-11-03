package com.reiniertutoriales.vireolumatv.webengine.webview

import android.app.Application
import android.content.res.Configuration
import com.reiniertutoriales.vireolumatv.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale

/** The start page is served in the app's language, with nothing left unfilled. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class HomePageTest {
    private val template = File("src/main/assets/pages/home/index.html").readText()

    private fun context(tag: String) = RuntimeEnvironment.getApplication().let { app ->
        app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })
    }

    @Test fun everyPlaceholderIsFilledInEveryLanguage() {
        for (tag in listOf("en", "es", "de", "it", "pl", "ru", "uk", "vi", "zh-CN", "zh-TW", "he", "fa")) {
            val page = HomePage.render(context(tag), template)
            assertFalse(tag, page.contains("{{"))
            assertTrue(tag, page.contains("lang=\"${Locale.forLanguageTag(tag).toLanguageTag()}\""))
        }
    }

    @Test fun rightToLeftLanguagesGetRtlAndOthersLtr() {
        assertTrue(HomePage.render(context("he"), template).contains("dir=\"rtl\""))
        assertTrue(HomePage.render(context("fa"), template).contains("dir=\"rtl\""))
        assertTrue(HomePage.render(context("es"), template).contains("dir=\"ltr\""))
    }

    @Test fun theDocumentKeepsHistoryAndStorageWithoutRenderingApplicationUi() {
        val es = context("es")
        val page = HomePage.render(es, template)
        assertTrue(page.contains("<title>" + es.getString(R.string.home_title) + "</title>"))
        assertTrue(page.contains("<body></body>"))
        assertFalse(page.contains("<script"))
        assertFalse(page.contains("<form"))
        assertFalse(page.contains("<input"))
    }
}
