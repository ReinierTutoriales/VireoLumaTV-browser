package com.reiniertutoriales.vireolumatv.activity.main

import android.app.Application
import com.reiniertutoriales.vireolumatv.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Counts agree with their noun: "1 download", "2 downloads", and each language's own forms. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class PluralStringsTest {
    private fun downloads(count: Int) =
        RuntimeEnvironment.getApplication().resources.getQuantityString(R.plurals.nav_menu_downloads, count, count)

    private fun blocked(count: Int) =
        RuntimeEnvironment.getApplication().resources.getQuantityString(R.plurals.blocker_summary, count, "on", count)

    @Test fun english() {
        assertEquals("Menu. 1 download in progress", downloads(1))
        assertEquals("Menu. 3 downloads in progress", downloads(3))
        assertEquals("Ad blocking: on. 1 blocked on this page.", blocked(1))
    }

    @Config(qualifiers = "es")
    @Test fun spanish() {
        assertEquals("Menú. 1 descarga en curso", downloads(1))
        assertEquals("Menú. 2 descargas en curso", downloads(2))
        assertEquals("Bloqueo de anuncios: on. 1 bloqueado en esta página.", blocked(1))
        assertEquals("Bloqueo de anuncios: on. 5 bloqueados en esta página.", blocked(5))
    }

    @Config(qualifiers = "de")
    @Test fun german() {
        assertEquals("Menü. 1 Download läuft", downloads(1))
        assertEquals("Menü. 4 Downloads laufen", downloads(4))
    }

    @Config(qualifiers = "ru")
    @Test fun russianKeepsAFormValidForEveryNumber() {
        listOf(1, 2, 5, 21).forEach { assertEquals("Меню. Идёт загрузок: $it", downloads(it)) }
    }
}
