package com.reiniertutoriales.vireolumatv.activity.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.platform.app.InstrumentationRegistry
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.main.compose.VireoTvTheme
import com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings.AboutPage
import com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings.GeneralPage
import com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings.SettingsContent
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import com.reiniertutoriales.vireolumatv.webengine.EngineBackend
import com.reiniertutoriales.vireolumatv.webengine.Engines
import kotlinx.coroutines.MainScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** D-pad order of the General settings page and what its rows change, on the Compose screen. */
@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28], qualifiers = "w960dp-h540dp-mdpi")
class MainSettingsFocusTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val blocker = AdBlocker(autoLoad = false)
    private val debugCalls = mutableListOf<Boolean>()
    private lateinit var restoreEngine: () -> Unit
    private lateinit var general: GeneralPage

    @Before fun remote() {
        // A remote key leaves touch mode; only then are the rows focus stops.
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        restoreEngine = Engines.replaceForTest(object : EngineBackend {
            override fun create(tab: BrowserTab) = error("unused")
            override suspend fun clearCache(context: android.content.Context) {}
            override val version = "test"
            override fun setPageDebugging(enabled: Boolean) { debugCalls += enabled }
        })
    }

    @After fun release() {
        restoreEngine()
        blocker.clear()
    }

    private fun show() {
        compose.runOnUiThread {
            general = GeneralPage(compose.activity, BrowserSettings(), blocker, MainScope(), onBlockerChanged = {})
        }
        val about = AboutPage(compose.activity, MainScope(), onDone = {})
        compose.setContent { VireoTvTheme { SettingsContent(general, about) } }
    }

    private fun focusedTag(): String =
        compose.onNode(isFocused()).fetchSemanticsNode().config[SemanticsProperties.TestTag]

    private fun walk(start: String, key: Key, steps: Int): List<String> {
        compose.onNodeWithTag(start).requestFocus()
        val visited = mutableListOf(focusedTag())
        repeat(steps) {
            compose.onNode(isFocused()).performKeyInput { pressKey(key) }
            visited += focusedTag()
        }
        return visited
    }

    @Test fun downAndUpVisitEveryControlOnceInSectionOrder() {
        blocker.config.adBlockEnabled = true
        show()
        val expected = listOf(
            "spEngine", "spHomePage",
            "llAdblock", "btnAdBlockerUpdate",
            "spAppLanguage", "llWebViewAlgorithmicDarkening", "llAllowAutoplayMedia", "spVideoMaxQuality", "llKeepScreenOn",
            "llNavigateWithJoystickAxes", "sbCursorMaxSpeed", "sbCursorAcceleration",
            "etAdBlockerListUrl", "llWebEngineDebug", "btnClearWebCache",
        )
        // One more press at the end must stay on the last row.
        assertEquals(expected + expected.last(), walk("spEngine", Key.DirectionDown, expected.size))
        // Up from the last row walks back and then reaches the selected tab.
        assertEquals(expected.reversed() + "general_page_tab", walk("btnClearWebCache", Key.DirectionUp, expected.size))

        val headings = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .fetchSemanticsNodes().map { it.config[SemanticsProperties.Text].joinToString() }
        assertEquals(listOf(R.string.settings_section_browsing, R.string.settings_section_protection,
            R.string.settings_section_display, R.string.settings_section_remote, R.string.settings_section_advanced)
            .map(compose.activity::getString), headings)
    }

    @Test fun activatingARowFlipsItsSwitchAndStoresTheSettingAtOnce() {
        show()
        val autoplay = blocker.config.allowAutoplayMedia
        compose.onNodeWithTag("llAllowAutoplayMedia").performScrollTo().performClick()
        assertEquals(!autoplay, blocker.config.allowAutoplayMedia)
        compose.onNodeWithTag("llAllowAutoplayMedia")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState(!autoplay)))
    }

    @Test fun leftAndRightChangeASliderByOneStepWithinItsRange() {
        val min = com.reiniertutoriales.vireolumatv.Config.POINTER_PERCENT_MIN
        blocker.config.pointerSpeedPercent = min + 3
        show()
        compose.onNodeWithTag("sbCursorMaxSpeed").requestFocus()
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) }
        assertEquals(min + 8, blocker.config.pointerSpeedPercent)
        assertEquals("sbCursorMaxSpeed", focusedTag())
        // Left and Right adjust the value and never move focus; the range is a hard limit.
        repeat(3) { compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionLeft) } }
        assertEquals(min, blocker.config.pointerSpeedPercent)
        assertEquals("sbCursorMaxSpeed", focusedTag())
    }

    @Test fun turningDebuggingOffClosesItForPagesAlreadyOpen() {
        blocker.config.pageDebugging = true
        show()
        compose.onNodeWithTag("llWebEngineDebug").performScrollTo().performClick()
        assertFalse(blocker.config.pageDebugging)
        assertEquals(listOf(false), debugCalls)
    }

    @Test fun tappingTrackCanRestoreInitialValueAfterRemoteAdjustment() {
        val min = com.reiniertutoriales.vireolumatv.Config.POINTER_PERCENT_MIN
        blocker.config.pointerSpeedPercent = min
        show()
        val slider = compose.onNodeWithTag("sbCursorMaxSpeed")
        slider.requestFocus()
        slider.performKeyInput { pressKey(Key.DirectionRight) }
        assertEquals(min + 5, blocker.config.pointerSpeedPercent)
        // mdpi fixture: 16 px row padding followed by the 160 px track.
        slider.performTouchInput { click(Offset(width - 176f + 0.1f, centerY)) }
        compose.waitForIdle()
        assertEquals(min, blocker.config.pointerSpeedPercent)
    }
}
