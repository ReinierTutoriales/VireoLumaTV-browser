package com.reiniertutoriales.vireolumatv.activity.main

import android.content.pm.PackageManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.platform.app.InstrumentationRegistry
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvBrowserAction
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvSymbol
import com.reiniertutoriales.vireolumatv.activity.main.compose.VireoTvTheme
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
