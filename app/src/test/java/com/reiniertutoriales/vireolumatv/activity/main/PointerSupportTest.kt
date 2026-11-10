package com.reiniertutoriales.vireolumatv.activity.main

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
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

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

    // The official form is an application <property> (Robolectric does not implement getProperty).
    @Test fun manifestDeclaresPointerSupportAsApplicationProperty() {
        val manifest = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val android = "http://schemas.android.com/apk/res/android"
        val properties = manifest.getElementsByTagName("property")
        val declared = (0 until properties.length).map { properties.item(it) as Element }.filter {
            it.parentNode.nodeName == "application" &&
                it.getAttributeNS(android, "name") == "android.software.leanback.supports_touch"
        }
        assertEquals(listOf("true"), declared.map { it.getAttributeNS(android, "value") })
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
