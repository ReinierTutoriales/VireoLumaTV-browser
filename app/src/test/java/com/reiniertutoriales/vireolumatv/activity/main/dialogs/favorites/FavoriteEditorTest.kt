package com.reiniertutoriales.vireolumatv.activity.main.dialogs.favorites

import android.app.Activity
import android.content.DialogInterface
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.Favorite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The favorite editor works on a copy, requires an address and adds https:// when it has no scheme. */
@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class FavoriteEditorTest {
    private fun <T> withActivity(test: (Activity) -> T): T {
        val owner = Robolectric.buildActivity(Activity::class.java).setup()
        try { return test(owner.get()) } finally { owner.pause().stop().destroy() }
    }

    @Test fun confirmingEmitsADetachedEditOfTheSameBookmark() = withActivity { activity ->
        val original = Favorite().apply { id = 7; title = "Before"; url = "https://before.test" }
        var saved: Favorite? = null
        val dialog = editFavorite(activity, original) { saved = it }
        assertEquals("Before", dialog.fieldValue(0))
        assertEquals("https://before.test", dialog.fieldValue(1))
        dialog.setFieldValue(0, "After")
        dialog.setFieldValue(1, "  after.test  ")
        dialog.press(DialogInterface.BUTTON_POSITIVE)
        val edit = requireNotNull(saved)
        assertNotSame(original, edit)
        assertEquals("Before", original.title)
        assertEquals("https://before.test", original.url)
        assertEquals("After", edit.title)
        assertEquals("https://after.test", edit.url)
        assertEquals(original.id, edit.id)
    }

    @Test fun emptyAddressKeepsTheEditorOpenWithItsReasonAndChangesNothing() = withActivity { activity ->
        val original = Favorite().apply { title = "Before"; url = "https://before.test" }
        var calls = 0
        val dialog = editFavorite(activity, original) { calls++ }
        dialog.setFieldValue(0, "After")
        dialog.setFieldValue(1, "  ")
        dialog.press(DialogInterface.BUTTON_POSITIVE)
        assertTrue(dialog.isShowing)
        assertEquals(activity.getString(R.string.favorite_address_required), dialog.error)
        dialog.press(DialogInterface.BUTTON_NEGATIVE)
        assertEquals("Before", original.title)
        assertEquals("https://before.test", original.url)
        assertEquals(0, calls)
    }

    @Test fun anAddressWithASchemeIsKept() = withActivity { activity ->
        var saved: Favorite? = null
        val dialog = editFavorite(activity, Favorite()) { saved = it }
        dialog.setFieldValue(1, "http://example.test/page")
        dialog.press(DialogInterface.BUTTON_POSITIVE)
        assertEquals("http://example.test/page", saved?.url)
    }
}
