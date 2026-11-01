package com.reiniertutoriales.vireolumatv.activity.main

import android.app.Application
import android.content.res.Configuration
import com.reiniertutoriales.vireolumatv.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

/** The tab options are picked by position, so every language must list the same options. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class TabActionsLocalesTest {
    @Test fun everyLanguageOffersEveryTabOption() {
        val app = RuntimeEnvironment.getApplication()
        val english = app.resources.getStringArray(R.array.tabs_actions)
        assertEquals(6, english.size)
        for (tag in listOf("de", "es", "fa", "it", "iw", "pl", "ru", "uk", "vi", "zh-TW", "zh")) {
            val config = Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
            val localized = app.createConfigurationContext(config).resources
            val actions = localized.getStringArray(R.array.tabs_actions)
            assertEquals(tag, english.size, actions.size)
            assertTrue(tag, actions.none { it.isBlank() })
            assertTrue("$tag is translated", actions.last() != english.last())
            for (id in listOf(R.string.favorite_added, R.string.favorite_exists, R.string.favorite_not_web)) {
                assertTrue(tag, localized.getString(id) != app.getString(id))
            }
        }
    }
}
