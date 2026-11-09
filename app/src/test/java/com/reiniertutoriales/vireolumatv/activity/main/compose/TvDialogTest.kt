package com.reiniertutoriales.vireolumatv.activity.main.compose

import android.content.DialogInterface
import android.os.Looper
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class TvDialogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun remote() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    private fun idle() = Shadows.shadowOf(Looper.getMainLooper()).idle()

    @Test fun aConfirmationStartsOnTheSafeAnswer() {
        compose.runOnUiThread {
            TvDialog.Builder(compose.activity).setMessage("Delete?")
                .setNegativeButton("No", null).setPositiveButton("Yes", null).show()
        }
        compose.onNodeWithTag("dialog_button_${DialogInterface.BUTTON_NEGATIVE}").assertIsFocused()
    }

    @Test fun aChoiceStartsOnTheCheckedOption() {
        compose.runOnUiThread {
            TvDialog.Builder(compose.activity).setSingleChoiceItems(android.R.array.emailAddressTypes, 2, null).show()
        }
        compose.onNodeWithTag("dialog_item_2").assertIsFocused()
    }

    @Test fun choosingAnOptionRunsItOnceAndCloses() {
        val chosen = mutableListOf<Int>()
        lateinit var dialog: TvDialog
        compose.runOnUiThread {
            dialog = TvDialog.Builder(compose.activity)
                .setItems(arrayOf("A", "B"), DialogInterface.OnClickListener { _, which -> chosen += which }).show()
        }
        compose.onNodeWithTag("dialog_item_1").performClick()
        compose.runOnUiThread { dialog.choose(0) }
        assertEquals(listOf(1), chosen)
        assertFalse(dialog.isShowing)
    }

    @Test fun pressingAButtonRunsItOnceAndCloses() {
        var yes = 0
        lateinit var dialog: TvDialog
        compose.runOnUiThread {
            dialog = TvDialog.Builder(compose.activity).setMessage("m")
                .setPositiveButton("Yes") { _, _ -> yes++ }.show()
        }
        compose.onNodeWithTag("dialog_button_${DialogInterface.BUTTON_POSITIVE}").performClick()
        compose.runOnUiThread { dialog.press(DialogInterface.BUTTON_POSITIVE) }
        assertEquals(1, yes)
        assertFalse(dialog.isShowing)
    }

    @Test fun backDoesNotCloseADialogThatIsNotCancelable() {
        lateinit var dialog: TvDialog
        compose.runOnUiThread {
            dialog = TvDialog.Builder(compose.activity).setMessage("m").setCancelable(false)
                .setPositiveButton("OK", null).show()
            dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        }
        idle()
        assertTrue(dialog.isShowing)
    }

    @Test fun fieldsKeepWhatIsTyped() {
        lateinit var dialog: TvDialog
        var read: Pair<String, String>? = null
        compose.runOnUiThread {
            dialog = TvDialog.Builder(compose.activity)
                .setFields(TvDialog.Field(android.R.string.untitled), TvDialog.Field(android.R.string.untitled, password = true))
                .setPositiveButton("OK") { d, _ -> read = (d as TvDialog).fieldValue(0) to d.fieldValue(1) }.show()
            dialog.setFieldValue(0, "ana")
            dialog.setFieldValue(1, "secret")
        }
        compose.onNodeWithTag("dialog_field_0").assertIsFocused()
        compose.runOnUiThread { dialog.press(DialogInterface.BUTTON_POSITIVE) }
        assertEquals("ana" to "secret", read)
    }
}
