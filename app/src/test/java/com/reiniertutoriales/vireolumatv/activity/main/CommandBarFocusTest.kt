package com.reiniertutoriales.vireolumatv.activity.main

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.platform.app.InstrumentationRegistry
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.main.compose.BrowserCommandActions
import com.reiniertutoriales.vireolumatv.activity.main.view.ComposeControlsHost
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Capsule focus: disabled commands and the tab row's close buttons, as reached with the D-pad. */
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

    @Test fun runningDownloadsAreAnnouncedOnTheMenuButtonAndClearedAfterwards() {
        val host = openCapsuleWithHistory()
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.runOnIdle { host.showDownloads(2) }
        compose.onNodeWithTag("menu_button").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription,
            listOf(app.resources.getQuantityString(com.reiniertutoriales.vireolumatv.R.plurals.nav_menu_downloads, 2, 2))))
        compose.runOnIdle { host.showDownloads(0) }
        compose.onNodeWithTag("menu_button").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription,
            listOf(app.getString(com.reiniertutoriales.vireolumatv.R.string.nav_menu))))
    }

    private fun tab(index: Int) = SemanticsMatcher("tab $index") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("tab_${index}_") == true
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun rightVisitsEachTabThenItsCloseButton() {
        openCapsuleWithHistory(listOf(BrowserTab(title = "Uno"), BrowserTab(title = "Dos")))
        compose.onNodeWithTag("home_button").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNode(isFocused()).performKeyInput { repeat(4) { pressKey(Key.DirectionLeft) } }
        compose.onNode(tab(0)).assertIsFocused()
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("tab_close_0").assertIsFocused()
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNode(tab(1)).assertIsFocused()
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("tab_close_1").assertIsFocused()
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("new_tab_button").assertIsFocused()
    }

    @Test fun aRenamedTabIsRedrawnWithoutReplacingTheTab() {
        val tab = BrowserTab(title = "Uno")
        val host = openCapsuleWithHistory(listOf(tab))
        compose.runOnIdle {
            tab.title = "Dos"
            host.setTabs(listOf(tab), 0)
        }
        compose.onNode(tab(0)).assertTextContains("Dos")
        // A tab without a title shows its address.
        compose.runOnIdle {
            tab.title = ""
            tab.url = "https://example.test/"
            host.setTabs(listOf(tab), 0)
        }
        compose.onNode(tab(0)).assertTextContains("https://example.test/")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun closingAFocusedTabLeavesFocusOnTheSelectedTab() {
        val tabs = mutableListOf(BrowserTab(title = "Uno"), BrowserTab(title = "Dos"), BrowserTab(title = "Tres"))
        val host = openCapsuleWithHistory(tabs.toList())
        compose.runOnIdle {
            host.onTabClose = { closed ->
                tabs.remove(closed)
                host.setTabs(tabs.toList(), 0)
                host.focusTab(0)
            }
        }
        compose.onNodeWithTag("tab_close_1").requestFocus()
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNode(tab(0)).assertIsFocused()
        compose.onNode(tab(0)).assertTextContains("Uno")
        compose.onNode(tab(1)).assertTextContains("Tres")
    }

    @Test fun suggestionsTakeTheTabRowsPlaceAndItsReturnKeepsFocus() {
        val host = openCapsuleWithHistory(listOf(BrowserTab(title = "Uno"), BrowserTab(title = "Dos")))
        compose.runOnIdle { host.showTabStrip(false) }
        compose.onNodeWithTag("new_tab_button").assertDoesNotExist()
        compose.runOnIdle {
            // A request made while the row is hidden must not pull focus when it returns.
            host.focusTab(1)
            host.showTabStrip(true)
        }
        compose.onNodeWithTag("new_tab_button").assertExists()
        compose.onNodeWithTag("back_button").assertIsFocused()
    }

    // A TV-sized window: on the default phone width the address field has no room left.
    @Config(qualifiers = "w960dp-h540dp-mdpi")
    @Test fun backWhileEditingLeavesFocusOnTheAddress() {
        val host = openCapsuleWithHistory()
        compose.onNodeWithTag("home_search").performClick()
        compose.runOnIdle { assertTrue(host.isEditingAddress) }
        compose.runOnIdle { host.cancelAddressInput() }
        compose.runOnIdle { assertFalse(host.isEditingAddress) }
        compose.onNodeWithTag("home_search").assertIsFocused()
    }

    // A TV-sized window: on the default phone width the address field has no room left.
    @Config(qualifiers = "w960dp-h540dp-mdpi")
    @Test fun closingTheCapsuleEndsEditingWithoutTakingFocus() {
        val host = openCapsuleWithHistory()
        compose.onNodeWithTag("home_search").performClick()
        compose.runOnIdle { host.closeAddressEditing() }
        compose.runOnIdle { assertFalse(host.isEditingAddress) }
        compose.onNode(isFocused()).assertDoesNotExist()
    }
}
