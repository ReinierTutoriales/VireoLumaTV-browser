package com.reiniertutoriales.vireolumatv.activity.main.dialogs.favorites

import android.app.Activity
import android.widget.Button
import android.widget.EditText
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.FavoriteItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class FavoriteEditorDialogTest {
    private fun dialog(saved: (FavoriteItem) -> Unit): FavoriteEditorDialog {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val callback = object : FavoriteEditorDialog.Callback {
            override fun onDone(item: FavoriteItem) = saved(item)
        }
        return FavoriteEditorDialog(activity, callback, FavoriteItem()).also { it.show() }
    }

    @Test fun emptyUrlIsNotSavedAndTheUserIsTold() {
        var saved: FavoriteItem? = null
        val dialog = dialog { saved = it }
        dialog.findViewById<EditText>(R.id.etUrl).setText("   ")
        dialog.findViewById<Button>(R.id.btnDone).performClick()

        assertNull(saved)
        assertEquals(dialog.context.getString(R.string.error), ShadowToast.getTextOfLatestToast())
    }

    @Test fun urlWithoutSchemeIsSavedWithHttps() {
        var saved: FavoriteItem? = null
        val dialog = dialog { saved = it }
        dialog.findViewById<EditText>(R.id.etUrl).setText("  example.test/page  ")
        dialog.findViewById<Button>(R.id.btnDone).performClick()

        assertEquals("https://example.test/page", saved?.url)
    }
}
