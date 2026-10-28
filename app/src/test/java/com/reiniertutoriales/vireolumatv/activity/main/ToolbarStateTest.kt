package com.reiniertutoriales.vireolumatv.activity.main

import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.downloads.ActiveDownloadsModel
import com.reiniertutoriales.vireolumatv.activity.main.view.ActionBar
import com.reiniertutoriales.vireolumatv.utils.activemodel.ActiveModelsRepository
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class ToolbarStateTest {
    @Test fun redirectsDoNotOverwriteAnEditAndCancelRestoresTheLatestPageAddress() {
        val owner = Robolectric.buildActivity(AppCompatActivity::class.java)
        owner.get().setTheme(R.style.AppTheme)
        owner.setup()
        try {
            val bar = ActionBar(owner.get())
            owner.get().setContentView(bar)
            var cancellations = 0
            val actions = mutableListOf<String>()
            bar.callback = object : ActionBar.Callback {
                override fun showAppMenu() {}
                override fun search(text: String) { actions += text }
                override fun onUrlInputDone() { actions += "commit" }
                override fun onAddressInputCancelled() { cancellations++ }
            }
            val input = bar.findViewById<EditText>(R.id.etUrl)
            bar.setAddressBoxText("https://before.test")
            input.performClick()
            input.setText("unfinished search")
            bar.setAddressBoxText("https://redirect.test")
            assertEquals("unfinished search", input.text.toString())
            assertEquals(View.GONE, bar.backButton.visibility)
            bar.cancelAddressInput()
            assertFalse(bar.isEditingAddress)
            assertEquals("https://redirect.test", input.text.toString())
            assertEquals(View.VISIBLE, bar.backButton.visibility)
            assertEquals(1, cancellations)
            assertTrue(actions.isEmpty())
            input.performClick()
            bar.submit("  selected suggestion  ")
            assertEquals(listOf("commit", "selected suggestion"), actions)
            assertEquals(1, cancellations)
            assertFalse(bar.isEditingAddress)
            // The old infinite XML animation is no longer part of the download indicator.
            assertNull(bar.findViewById<View>(R.id.ibMenu).animation)
        } finally { owner.pause().stop().destroy() }
    }
}
