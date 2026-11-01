package com.reiniertutoriales.vireolumatv.utils

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class AppLanguagesTest {
    @Test fun everyBundledTranslationCanBeChosenAndNothingElse() {
        // values-iw is the resource name of Hebrew; values-zh serves simplified Chinese.
        val folders = File("src/main/res").list()!!.filter { it.matches(Regex("values-[a-z]{2}(-r[A-Z]{2})?")) }
        val fromFolders = folders.map {
            when (val q = it.removePrefix("values-")) {
                "iw" -> "he"
                "zh" -> "zh-CN"
                else -> q.replace("-r", "-")
            }
        }.toSet() + "en"
        assertEquals(fromFolders, AppLanguages.TAGS.toSet())
        assertEquals("No duplicates", AppLanguages.TAGS.size, AppLanguages.TAGS.toSet().size)
    }

    @Test fun languagesAreListedInTheirOwnNamesAfterTheSystemChoice() {
        val names = AppLanguages.names("System")
        assertEquals("System", names[0])
        assertEquals(AppLanguages.TAGS.size + 1, names.size)
        assertEquals(Locale.forLanguageTag("de").getDisplayName(Locale.GERMAN).replaceFirstChar { it.titlecase(Locale.GERMAN) },
            names[AppLanguages.TAGS.indexOf("de") + 1])
        assertTrue(names.drop(1).none { it.isBlank() })
    }

    @Test fun withNothingChosenTheSystemLanguageApplies() {
        assertEquals(0, AppLanguages.chosen())
    }
}
