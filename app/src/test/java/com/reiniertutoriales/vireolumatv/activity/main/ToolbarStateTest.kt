package com.reiniertutoriales.vireolumatv.activity.main

import androidx.appcompat.app.AppCompatActivity
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.main.view.BrowserControls
import com.reiniertutoriales.vireolumatv.activity.main.compose.BrowserCommandActions
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Host-level contract tests. Compose editing/focus behavior belongs in Compose UI tests;
 * the host must not recreate legacy EditText or toolbar ImageButtons.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class ToolbarStateTest {
    @Test fun toolbarHostUsesOnlyOneComposeViewAndAcceptsBrowserState() {
        val owner = Robolectric.buildActivity(AppCompatActivity::class.java)
        owner.get().setTheme(R.style.AppTheme)
        owner.setup()
        try {
            val bar = BrowserControls(owner.get())
            owner.get().setContentView(bar)
            assertEquals(1, bar.childCount)
            assertTrue(bar.getChildAt(0) is androidx.compose.ui.platform.ComposeView)
            bar.commandActions = BrowserCommandActions(
                back = {}, forward = {}, reload = {}, home = {},
                shield = {}, shieldDetails = {}, menu = {},
            )
            bar.showAddress("https://before.test")
            bar.updateNavigation(back = true, forward = false)
            bar.showBlockerEnabled(true)
            bar.updateBlockerStatus("Blocker active", ready = true)
            assertEquals("https://before.test", bar.snapshot.address)
            assertTrue(bar.snapshot.canGoBack)
            assertFalse(bar.snapshot.canGoForward)
            assertTrue(bar.snapshot.blockerEnabled)
            assertEquals("Blocker active", bar.snapshot.blockerDescription)
            assertTrue(bar.snapshot.blockerReady)
            assertFalse(bar.snapshot.isEditingAddress)
            bar.takeFocus()
            bar.closeAddressEditing()
            assertFalse(bar.isEditingAddress)
        } finally {
            owner.pause().stop().destroy()
        }
    }
}
