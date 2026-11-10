package com.reiniertutoriales.vireolumatv.activity.main.compose

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.input.key.Key
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The chrome follows TV Material 3 tokens and the TV safe margins, and adapts to narrow windows. */
@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [34])
class ChromeMetricsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun aTvUsesTvMaterialSizesInsideTheSafeArea() {
        val tv = ChromeMetrics.forWindow(960.dp, television = true)
        assertEquals(40.dp, tv.control)
        assertEquals(20.dp, tv.icon)
        assertEquals(48.dp, tv.edge)
        assertEquals(24.dp, tv.top)
        assertEquals(16.sp, tv.body)
        assertEquals(14.sp, tv.label)
        assertEquals(188.dp, tv.tabMaxWidth)
        assertFalse(tv.stacked)
    }

    @Test fun touchScreensKeepTouchTargetsAndNarrowOnesStackTheAddress() {
        val phone = ChromeMetrics.forWindow(360.dp, television = false)
        assertEquals(48.dp, phone.control)
        assertEquals(16.dp, phone.edge)
        assertTrue(phone.stacked)
        val tablet = ChromeMetrics.forWindow(700.dp, television = false)
        assertEquals(24.dp, tablet.edge)
        assertFalse(tablet.stacked)
    }

    private fun showBar() = compose.setContent {
        VireoTvTheme {
            TvBrowserCommandBar(
                actions = BrowserCommandActions({}, {}, {}, {}, {}, {}, {}),
                address = "https://example.test/", addressWarning = false,
                onSubmitAddress = {}, onCancelAddress = {}, onAddressDraftChanged = {},
                onAddressEditingChanged = {}, onFocusSuggestions = { false },
                canGoBack = true, canGoForward = true, blockerEnabled = true,
            )
        }
    }

    @Config(qualifiers = "w960dp-h540dp-television-mdpi")
    @Test fun onATvTheCommandsAre40dpAndTheAddressStartsAtTheSafeMargin() {
        showBar()
        val back = compose.onNodeWithTag("back_button").getBoundsInRoot()
        assertEquals(40.dp, back.bottom - back.top)
        assertEquals(40.dp, back.right - back.left)
        val address = compose.onNodeWithTag("home_search").getBoundsInRoot()
        assertEquals(48.dp, address.left)
        assertEquals(24.dp, address.top)
        assertEquals(back.top, address.top + (address.bottom - address.top - (back.bottom - back.top)) / 2)
    }

    @Config(qualifiers = "w360dp-h640dp-mdpi")
    @Test fun aNarrowWindowPutsTheAddressAboveTheCommands() {
        showBar()
        val back = compose.onNodeWithTag("back_button").getBoundsInRoot()
        val address = compose.onNodeWithTag("home_search").getBoundsInRoot()
        assertEquals(48.dp, back.bottom - back.top)
        assertTrue("Address above the commands", address.bottom <= back.top)
        assertTrue("Address spans the row", address.right - address.left > 300.dp)
    }

    @Config(qualifiers = "w320dp-h640dp-mdpi")
    @Test fun aCompactWindowKeepsTheLastCommandReachableAtItsFullTouchSize() {
        showBar()
        compose.onNodeWithTag("menu_button").performScrollTo()
            .assertIsDisplayed().assertWidthIsEqualTo(48.dp)
    }

    @OptIn(ExperimentalTestApi::class)
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    @Test fun dpadFocusBringsTheLastCompactCommandIntoView() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        showBar()
        compose.onNodeWithTag("back_button").requestFocus()
        for (tag in listOf("back_button", "forward_button", "refresh_button", "home_button", "blocker_button")) {
            compose.onNodeWithTag(tag).assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        }
        compose.onNodeWithTag("menu_button").assertIsFocused().assertIsDisplayed().assertWidthIsEqualTo(48.dp)
    }
}
