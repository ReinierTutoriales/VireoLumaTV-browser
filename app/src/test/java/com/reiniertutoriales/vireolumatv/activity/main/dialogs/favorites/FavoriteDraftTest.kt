package com.reiniertutoriales.vireolumatv.activity.main.dialogs.favorites

import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.model.FavoriteItem
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.app.Application

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class FavoriteDraftTest {
    @Test fun confirmingEmitsADetachedEditAndPreservesLegacyFields() {
        val owner = Robolectric.buildActivity(AppCompatActivity::class.java)
        owner.get().setTheme(R.style.AppTheme)
        owner.setup()
        try {
            val original = FavoriteItem().apply {
                id = 7; title = "Before"; url = "https://before.test"; parent = 12
                favicon = "icon"; homePageBookmark = true; order = 3
                destUrl = "destination"; description = "description"; validUntil = 123L; useful = true
            }
            var saved: FavoriteItem? = null
            val dialog = FavoriteEditorDialog(owner.get(), object : FavoriteEditorDialog.Callback {
                override fun onDone(item: FavoriteItem) { saved = item }
            }, original)
            dialog.show()
            dialog.findViewById<EditText>(R.id.etTitle).setText("After")
            dialog.findViewById<EditText>(R.id.etUrl).setText("  after.test  ")
            dialog.findViewById<android.view.View>(R.id.btnDone).performClick()
            val edit = requireNotNull(saved)
            assertNotSame(original, edit)
            assertEquals("Before", original.title)
            assertEquals("https://before.test", original.url)
            assertEquals("After", edit.title)
            assertEquals("https://after.test", edit.url)
            assertEquals(original.id, edit.id)
            assertEquals(original.parent, edit.parent)
            assertEquals(original.favicon, edit.favicon)
            assertEquals(original.homePageBookmark, edit.homePageBookmark)
            assertEquals(original.order, edit.order)
            assertEquals(original.destUrl, edit.destUrl)
            assertEquals(original.description, edit.description)
            assertEquals(original.validUntil, edit.validUntil)
            assertEquals(original.useful, edit.useful)
        } finally { owner.pause().stop().destroy() }
    }

    @Test fun emptyAddressKeepsTheEditorOpenWithoutChangingTheBookmark() {
        val owner = Robolectric.buildActivity(AppCompatActivity::class.java)
        owner.get().setTheme(R.style.AppTheme)
        owner.setup()
        try {
            val original = FavoriteItem().apply { title = "Before"; url = "https://before.test" }
            var calls = 0
            val dialog = FavoriteEditorDialog(owner.get(), object : FavoriteEditorDialog.Callback {
                override fun onDone(item: FavoriteItem) { calls++ }
            }, original)
            dialog.show()
            dialog.findViewById<EditText>(R.id.etTitle).setText("After")
            dialog.findViewById<EditText>(R.id.etUrl).setText("  ")
            dialog.findViewById<android.view.View>(R.id.btnDone).performClick()
            assertTrue(dialog.isShowing)
            assertEquals(0, calls)
            dialog.findViewById<android.view.View>(R.id.btnCancel).performClick()
            assertEquals("Before", original.title)
            assertEquals("https://before.test", original.url)
            assertEquals(0, calls)
        } finally { owner.pause().stop().destroy() }
    }
}
