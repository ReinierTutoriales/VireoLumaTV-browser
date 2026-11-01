package com.reiniertutoriales.vireolumatv.activity.main.dialogs.favorites

import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.model.Favorite
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
    @Test fun confirmingEmitsADetachedEditOfTheSameBookmark() {
        val owner = Robolectric.buildActivity(AppCompatActivity::class.java)
        owner.get().setTheme(R.style.AppTheme)
        owner.setup()
        try {
            val original = Favorite().apply { id = 7; title = "Before"; url = "https://before.test" }
            var saved: Favorite? = null
            val dialog = FavoriteEditor(owner.get(), original) { item -> saved = item }
            dialog.show()
            dialog.findViewById<EditText>(R.id.title_field)!!.setText("After")
            dialog.findViewById<EditText>(R.id.address_field)!!.setText("  after.test  ")
            dialog.findViewById<android.view.View>(R.id.done_button)!!.performClick()
            val edit = requireNotNull(saved)
            assertNotSame(original, edit)
            assertEquals("Before", original.title)
            assertEquals("https://before.test", original.url)
            assertEquals("After", edit.title)
            assertEquals("https://after.test", edit.url)
            assertEquals(original.id, edit.id)
        } finally { owner.pause().stop().destroy() }
    }

    @Test fun emptyAddressKeepsTheEditorOpenWithoutChangingTheBookmark() {
        val owner = Robolectric.buildActivity(AppCompatActivity::class.java)
        owner.get().setTheme(R.style.AppTheme)
        owner.setup()
        try {
            val original = Favorite().apply { title = "Before"; url = "https://before.test" }
            var calls = 0
            val dialog = FavoriteEditor(owner.get(), original) { item -> calls++ }
            dialog.show()
            dialog.findViewById<EditText>(R.id.title_field)!!.setText("After")
            dialog.findViewById<EditText>(R.id.address_field)!!.setText("  ")
            dialog.findViewById<android.view.View>(R.id.done_button)!!.performClick()
            assertTrue(dialog.isShowing)
            assertEquals(0, calls)
            dialog.findViewById<android.view.View>(R.id.cancel_button)!!.performClick()
            assertEquals("Before", original.title)
            assertEquals("https://before.test", original.url)
            assertEquals(0, calls)
        } finally { owner.pause().stop().destroy() }
    }
}
