package com.reiniertutoriales.vireolumatv.activity.main

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.platform.app.InstrumentationRegistry
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.main.compose.VireoTvTheme
import com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings.AboutPage
import com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings.GeneralPage
import com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings.SettingsContent
import com.reiniertutoriales.vireolumatv.singleton.shortcuts.RemoteShortcut
import com.reiniertutoriales.vireolumatv.utils.ReleaseUpdater
import com.reiniertutoriales.vireolumatv.webengine.EngineBackend
import com.reiniertutoriales.vireolumatv.webengine.Engines
import kotlinx.coroutines.MainScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.io.File

/** Tabs, application identity, license, updates and D-pad reach of every settings page. */
@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28], qualifiers = "w960dp-h540dp-mdpi")
class SettingsTabsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val blocker = AdBlocker(autoLoad = false)
    private lateinit var restoreEngine: () -> Unit
    private lateinit var about: AboutPage

    @Before fun remote() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        // The About page reads the version of the engine the app initialized at start-up.
        val engine = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader,
            arrayOf(EngineBackend::class.java)) { _, method, _ ->
            if (method.name == "getVersion") "test-engine 1.0" else null
        } as EngineBackend
        restoreEngine = Engines.replaceForTest(engine)
    }

    @After fun release() {
        restoreEngine()
        blocker.clear()
    }

    private fun show(aboutContext: android.content.Context = compose.activity) {
        lateinit var general: GeneralPage
        compose.runOnUiThread {
            general = GeneralPage(compose.activity, BrowserSettings(), blocker, MainScope(), onBlockerChanged = {})
            about = AboutPage(aboutContext, MainScope(), onDone = {})
        }
        compose.setContent { VireoTvTheme { SettingsContent(general, about) } }
    }

    private fun focusedTag(): String =
        compose.onNode(isFocused()).fetchSemanticsNode().config[SemanticsProperties.TestTag]

    private fun downOrder(start: String, count: Int): List<String> {
        compose.onNodeWithTag(start).requestFocus()
        return listOf(focusedTag()) + List(count - 1) {
            compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
            focusedTag()
        }
    }

    /** Activates [tag] as the remote does: focus, then OK. */
    private fun activate(tag: String) {
        compose.onNodeWithTag(tag).requestFocus()
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
    }

    private fun idle() = Shadows.shadowOf(Looper.getMainLooper()).idle()

    @Test fun focusAloneNeverSwitchesThePageAndReturningKeepsUnsavedValues() {
        show()
        compose.onNodeWithTag("general_page_tab").assertIsFocused().assertIsSelected()
        compose.onNodeWithTag("general_page_tab").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("shortcuts_page_tab").assertIsFocused()
        compose.onNodeWithTag("spEngine").assertIsDisplayed()

        activate("about_page_tab")
        compose.onNodeWithTag("about_page_tab").assertIsSelected()
        compose.onNodeWithTag("rowLicense").assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(androidx.compose.ui.test.hasTestTag("spEngine")).fetchSemanticsNodes().size)

        activate("general_page_tab")
        compose.onNodeWithTag("spEngine").assertIsDisplayed()
    }

    @Test fun aboutPageShowsTheOwnerAndMitAndReachesEveryLink() {
        show()
        activate("about_page_tab")
        compose.onNodeWithTag("rowProjectSource").assertTextContains("ReinierTutoriales", substring = true)
        compose.onNodeWithTag("rowLicense").assertTextContains("MIT", substring = true)
        assertEquals(listOf("rowProjectSource", "rowLicense", "rowPrivacyPolicy", "btnCheckUpdate"),
            downOrder("rowProjectSource", 4))
    }

    @Test fun shortcutsPageShowsOneReachableRowPerShortcutWithItsKey() {
        show()
        activate("shortcuts_page_tab")
        val tags = RemoteShortcut.entries.map { "shortcut_${it.name}" }
        assertEquals(tags, downOrder(tags.first(), tags.size))
        RemoteShortcut.entries.forEach { shortcut ->
            val texts = compose.onNodeWithTag("shortcut_${shortcut.name}").fetchSemanticsNode()
                .config[SemanticsProperties.Text].map { it.text }
            assertEquals(compose.activity.getString(shortcut.titleResId), texts.first())
            assertTrue("Each row shows its key or that none is set", texts.size == 2 && texts[1].isNotBlank())
        }
    }

    @Config(sdk = [25])
    @Test fun installerFailureIsReportedAndTheUpdateCanRunAgain() {
        show()
        val apk = File(compose.activity.cacheDir, "updates/test.apk").apply { parentFile!!.mkdirs(); writeText("test") }
        val unavailableInstaller = object : ContextWrapper(compose.activity) {
            override fun startActivity(intent: Intent) = throw ActivityNotFoundException("No installer")
        }
        try {
            compose.runOnUiThread { about.whileUpdating { ReleaseUpdater.install(unavailableInstaller, apk) } }
            idle()
            compose.waitForIdle()
            assertEquals(compose.activity.getString(R.string.update_failed), ShadowToast.getTextOfLatestToast())
            assertFalse(about.updating)
        } finally { apk.delete() }
    }

    @Config(sdk = [25])
    @Test fun retryingADownloadedUpdateUsesTheSameFileWithoutCheckingTheNetwork() {
        // Records the installer request; the test has no package installer to resolve it.
        val started = mutableListOf<Intent>()
        val recorder = object : ContextWrapper(compose.activity) {
            override fun startActivity(intent: Intent) { started += intent }
        }
        show(recorder)
        val apk = File(compose.activity.cacheDir, "updates/retry.apk").apply {
            parentFile!!.mkdirs(); writeText("verified by download before it reaches this state")
        }
        try {
            // The install path itself, outside the screen, so a failure names its cause.
            val direct = runCatching { ReleaseUpdater.install(recorder, apk) }
            assertTrue("Install path: ${direct.exceptionOrNull()}", direct.isSuccess)
            started.clear()
            about.setPendingUpdate(apk)
            activate("about_page_tab")
            activate("btnCheckUpdate")
            idle()
            compose.waitForIdle()
            assertEquals("No failure reported", null, ShadowToast.getTextOfLatestToast())
            val intent = started.single()
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals("application/vnd.android.package-archive", intent.type)
            assertEquals("content", intent.data!!.scheme)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertTrue(apk.exists())
            assertFalse(about.updating)
        } finally { apk.delete() }
    }
}
