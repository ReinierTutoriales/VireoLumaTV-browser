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
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        assertEquals(0, AppLanguages.chosen(app))
        assertTrue("With nothing chosen the system language stays", AppLanguages.localized(app) === app)
    }

    @Test fun beforeAndroid13ScreensServicesAndToastsUseTheChosenLanguage() {
        val controller = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup()
        val activity = controller.get()
        try {
            AppLanguages.choose(activity, AppLanguages.TAGS.indexOf("es") + 1)
            assertEquals(AppLanguages.TAGS.indexOf("es") + 1, AppLanguages.chosen(activity))
            val localized = AppLanguages.localized(activity.applicationContext)
            assertEquals("Listo", localized.getString(com.reiniertutoriales.vireolumatv.R.string.home_done))
            assertEquals("es", AppLanguages.localeOf(localized).language)
            AppLanguages.choose(activity, 0)
            assertEquals(0, AppLanguages.chosen(activity))
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun aLanguageChosenThroughAppCompatIsKeptAfterTheUpgrade() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val record = File(app.filesDir, "androidx.appcompat.app.AppCompatDelegate.application_locales_record_file")
        record.writeText("<?xml version='1.0' encoding='utf-8' standalone='yes' ?><locales application_locales=\"de\" />")
        assertEquals(AppLanguages.TAGS.indexOf("de") + 1, AppLanguages.chosen(app))
        assertTrue("The old record is read once", !record.exists())
        assertEquals(AppLanguages.TAGS.indexOf("de") + 1, AppLanguages.chosen(app))
    }
}
