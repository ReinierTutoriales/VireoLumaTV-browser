package com.reiniertutoriales.vireolumatv.activity.main

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.platform.app.InstrumentationRegistry
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.main.compose.BrowserCommandActions
import com.reiniertutoriales.vireolumatv.activity.main.view.ComposeControlsHost
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Focus stays in the capsule when the history update disables the focused command. */
@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class CommandBarFocusTest {
    @get:Rule val compose = createComposeRule()

    private fun openCapsuleWithHistory(tabs: List<BrowserTab> = listOf(BrowserTab(title = "Inicio"))): ComposeControlsHost {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        lateinit var host: ComposeControlsHost
        compose.setContent {
            AndroidView(factory = { context ->
                ComposeControlsHost(context).also {
                    host = it
                    it.bindActions(BrowserCommandActions({}, {}, {}, {}, {}, {}, {}))
                    it.setTabs(tabs, 0)
                    it.updateNavigation(back = true, forward = true)
                }
            })
        }
        compose.runOnIdle { host.takeFocus() }
        compose.onNodeWithTag("back_button").assertIsFocused()
        return host
    }

    @Test fun backDisabledWhileFocusedMovesFocusToHome() {
        val host = openCapsuleWithHistory()
        compose.runOnIdle { host.updateNavigation(back = false, forward = true) }
        compose.onNodeWithTag("home_button").assertIsFocused()
    }

    @Test fun disablingAnUnfocusedCommandLeavesFocusInPlace() {
        val host = openCapsuleWithHistory()
        compose.runOnIdle { host.updateNavigation(back = true, forward = false) }
        compose.onNodeWithTag("back_button").assertIsFocused()
    }
}
