package com.reiniertutoriales.vireolumatv.activity.main

import android.content.pm.PackageManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.platform.app.InstrumentationRegistry
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvBrowserAction
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvSymbol
import com.reiniertutoriales.vireolumatv.activity.main.compose.VireoTvTheme
import com.reiniertutoriales.vireolumatv.activity.main.compose.BrowserCommandActions
import com.reiniertutoriales.vireolumatv.activity.main.view.ComposeControlsHost
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class PointerSupportTest {
    @get:Rule val compose = createComposeRule()

    @OptIn(ExperimentalTestApi::class)
    @Test fun hostDistinguishesTabFocusFromCommandFocus() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        lateinit var host: ComposeControlsHost
        compose.setContent {
            AndroidView(factory = { context ->
                ComposeControlsHost(context).also {
                    host = it
                    it.bindActions(BrowserCommandActions({}, {}, {}, {}, {}, {}, {}))
                    it.setTabs(listOf(BrowserTab(title = "Inicio")), 0)
                    it.takeFocus()
                }
            })
        }
        compose.waitForIdle()
        assertEquals(false, host.tabStripHasFocus)
        compose.onNodeWithTag("home_button").performKeyInput { pressKey(Key.DirectionDown) }
        compose.runOnIdle { assertTrue(host.tabStripHasFocus) }
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionUp) }
        compose.runOnIdle { assertEquals(false, host.tabStripHasFocus) }
    }

    @Suppress("DEPRECATION")
    @Test fun packageDeclaresPointerSupportAsApplicationMetadata() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        assertTrue(info.metaData?.getBoolean("android.software.leanback.supports_touch") == true)
    }

    @Test @Config(qualifiers = "es")
    fun longClickAccessibilityLabelUsesTheAppLanguage() {
        compose.setContent {
            VireoTvTheme {
                TvBrowserAction(label = "Escudo", symbol = TvSymbol.ShieldOn,
                    onClick = {}, onLongClick = {})
            }
        }
        val node = compose.onNodeWithContentDescription("Escudo").fetchSemanticsNode()
        assertEquals("Más opciones: Escudo", node.config[SemanticsActions.OnLongClick].label)
    }
}
