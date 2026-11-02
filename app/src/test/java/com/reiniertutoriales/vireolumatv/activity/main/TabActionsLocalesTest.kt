package com.reiniertutoriales.vireolumatv.activity.main

import android.app.Application
import android.content.res.Configuration
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.main.view.tabs.TabStrip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

/** Every language names every tab option and every pop-up level, in its own words. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class TabActionsLocalesTest {
    @Test fun everyLanguageOffersEveryTabOptionAndPopupLevel() {
        val app = RuntimeEnvironment.getApplication()
        val labels = TabStrip.TabAction.entries.map { it.label } +
            listOf(R.string.favorite_added, R.string.favorite_exists, R.string.favorite_not_web)
        val popups = app.resources.getStringArray(R.array.popups_levels)
        assertEquals("One label per stored pop-up level", 4, popups.size)
        for (tag in listOf("de", "es", "fa", "it", "iw", "pl", "ru", "uk", "vi", "zh-TW", "zh")) {
            val config = Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
            val localized = app.createConfigurationContext(config).resources
            for (id in labels) {
                val text = localized.getString(id)
                assertTrue("$tag ${app.resources.getResourceEntryName(id)}", text.isNotBlank() && text != app.getString(id))
            }
            val levels = localized.getStringArray(R.array.popups_levels)
            assertEquals(tag, popups.size, levels.size)
            assertTrue("$tag pop-up levels are translated", levels.indices.all { levels[it] != popups[it] })
        }
    }
}
