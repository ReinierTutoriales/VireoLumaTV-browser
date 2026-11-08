package com.reiniertutoriales.vireolumatv.activity.main

import android.content.DialogInterface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvDialog
import com.reiniertutoriales.vireolumatv.activity.main.compose.VireoTvTheme
import com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings.GeneralPage
import kotlinx.coroutines.MainScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class MainSettingsLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun reopeningSettingsReleasesObserversAndKeepsRefreshInTheFocusPath() {
        // A model with networking disabled: this test exercises the real settings content.
        val model = AdBlocker(autoLoad = false)
        model.config.adBlockEnabled = true
        val sourceObservers = model.config.blockListUrl.watchers.size
        var shown by mutableStateOf(false)
        lateinit var page: GeneralPage
        compose.setContent { VireoTvTheme { if (shown) page.Content() } }
        try {
            repeat(3) {
                compose.runOnUiThread {
                    page = GeneralPage(compose.activity, BrowserSettings(), model, MainScope(), onBlockerChanged = {})
                    shown = true
                }
                compose.waitForIdle()
                assertEquals(1, model.listLoading.watchers.size)
                assertEquals(1, model.updateResult.watchers.size)
                assertEquals(sourceObservers + 1, model.config.blockListUrl.watchers.size)
                compose.runOnUiThread { model.listLoading.value = true }
                // The refresh row stays in place and enabled while busy; the status shows every line.
                compose.onNodeWithTag("btnAdBlockerUpdate").assertIsDisplayed().assertIsEnabled()
                val status = compose.onNodeWithTag("tvAdBlockerListInfo").fetchSemanticsNode()
                    .config[SemanticsProperties.Text].joinToString()
                assertTrue(status.lines().size >= 3)
                assertTrue(status.contains(compose.activity.getString(R.string.blocker_updating)))
                compose.runOnUiThread { shown = false }
                compose.waitForIdle()
                assertTrue(model.listLoading.watchers.isEmpty())
                assertTrue(model.updateResult.watchers.isEmpty())
                assertEquals(sourceObservers, model.config.blockListUrl.watchers.size)
                compose.runOnUiThread { model.listLoading.value = false }
            }
        } finally {
            model.clear()
        }
    }

    @Test fun textValuesSurviveUntilCloseAndApplyOnlyThen() {
        val model = AdBlocker(autoLoad = false)
        val settings = BrowserSettings()
        lateinit var page: GeneralPage
        compose.runOnUiThread {
            settings.setHomePageProperties(com.reiniertutoriales.vireolumatv.Config.HomePageMode.CUSTOM, "https://saved.example/")
            page = GeneralPage(compose.activity, settings, model, MainScope(), onBlockerChanged = {})
        }
        compose.setContent { VireoTvTheme { Column(Modifier.verticalScroll(rememberScrollState())) { page.Content() } } }
        try {
            val source = model.config.blockListUrl.value
            compose.onNodeWithTag("etCustomHomePageUrl").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("etAdBlockerListUrl").performScrollTo().performClick()
            compose.runOnUiThread {
                val editor = ShadowDialog.getLatestDialog() as TvDialog
                editor.setFieldValue(0, "https://lists.example/filters.txt")
                editor.press(DialogInterface.BUTTON_POSITIVE)
            }
            assertEquals(source, model.config.blockListUrl.value)
            compose.onNodeWithTag("etAdBlockerListUrl").assertTextContains("https://lists.example/filters.txt")
            compose.runOnUiThread { page.save() }
            assertEquals("https://lists.example/filters.txt", model.config.blockListUrl.value)
            assertEquals("https://saved.example/", settings.homePage)
            compose.runOnUiThread { model.config.blockListUrl.value = source }
        } finally {
            model.clear()
        }
    }
}
