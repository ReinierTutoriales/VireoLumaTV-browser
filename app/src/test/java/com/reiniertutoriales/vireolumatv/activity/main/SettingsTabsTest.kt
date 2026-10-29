package com.reiniertutoriales.vireolumatv.activity.main

import android.view.FocusFinder
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings.ShortcutsPage
import com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings.AboutPage
import com.reiniertutoriales.vireolumatv.singleton.shortcuts.RemoteShortcut
import com.reiniertutoriales.vireolumatv.webengine.Engines
import com.reiniertutoriales.vireolumatv.webengine.EngineBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Application identity, license and D-pad reach of every settings row. */
@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28], qualifiers = "w960dp-h540dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsTabsTest {
    private fun <T : ScrollView> withPage(create: (FragmentActivity) -> T, test: (ViewGroup) -> Unit) {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java)
        controller.get().setTheme(R.style.AppTheme)
        controller.setup()
        try {
            val root = FrameLayout(controller.get())
            controller.get().setContentView(root)
            val page = create(controller.get())
            root.addView(page)
            val viewRoot = controller.get().window.decorView.parent
            viewRoot.javaClass.getDeclaredMethod("ensureTouchMode", Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(viewRoot, false)
            val list = page.getChildAt(0) as ViewGroup
            list.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            list.layout(0, 0, 960, list.measuredHeight)
            test(list)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun downOrder(list: ViewGroup): List<View> {
        val order = mutableListOf<View>()
        var current = FocusFinder.getInstance().findNextFocus(list, null, View.FOCUS_DOWN)
        while (current != null && order.size < 32) {
            order += current
            current = FocusFinder.getInstance().findNextFocus(list, current, View.FOCUS_DOWN)
        }
        return order
    }

    /** The About page reads the version of the engine the app initialized at start-up. */
    private fun withEngine(test: () -> Unit) {
        val callback = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader,
            arrayOf(EngineBackend::class.java)) { _, method, _ ->
            if (method.name == "getVersion") "test-engine 1.0" else null
        } as EngineBackend
        val restore = Engines.replaceForTest(callback)
        try { test() } finally { restore() }
    }

    @Test fun aboutPageShowsTheOwnerAndMitAndReachesEveryLink() = withEngine { withPage({ AboutPage(it) }) { list ->
        val source = list.findViewById<ViewGroup>(R.id.rowProjectSource)
        assertTrue((source.getChildAt(0) as TextView).text.toString().contains("ReinierTutoriales"))
        val license = list.findViewById<ViewGroup>(R.id.rowLicense)
        assertTrue((license.getChildAt(0) as TextView).text.toString().contains("MIT"))
        assertEquals(View.VISIBLE, license.visibility)
        val names = downOrder(list).map { list.resources.getResourceEntryName(it.id) }
        assertEquals(listOf("rowProjectSource", "rowLicense", "rowPrivacyPolicy", "btnCheckUpdate"), names)
    } }

    @Config(sdk = [25])
    @GraphicsMode(GraphicsMode.Mode.LEGACY)
    @Test fun installerFailureIsReportedAndTheUpdateButtonIsEnabledAgain() = withEngine {
        lateinit var page: AboutPage
        withPage({ AboutPage(it).also { value -> page = value } }) { list ->
            val apk = java.io.File(list.context.cacheDir, "updates/test.apk").apply {
                parentFile!!.mkdirs(); writeText("test")
            }
            val unavailableInstaller = object : android.content.ContextWrapper(list.context) {
                override fun startActivity(intent: android.content.Intent) {
                    throw android.content.ActivityNotFoundException("No installer")
                }
            }
            val work: suspend () -> Unit = {
                com.reiniertutoriales.vireolumatv.utils.ReleaseUpdater.install(unavailableInstaller, apk)
            }
            try {
                AboutPage::class.java.getDeclaredMethod("whileUpdating", kotlin.jvm.functions.Function1::class.java)
                    .apply { isAccessible = true }.invoke(page, work)
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                assertEquals(list.context.getString(R.string.update_failed),
                    org.robolectric.shadows.ShadowToast.getTextOfLatestToast())
                assertTrue(list.findViewById<View>(R.id.btnCheckUpdate).isEnabled)
            } finally { apk.delete() }
        }
    }

    @Config(sdk = [25])
    @GraphicsMode(GraphicsMode.Mode.LEGACY)
    @Test fun retryingADownloadedUpdateUsesTheSameFileWithoutCheckingTheNetwork() = withEngine {
        lateinit var page: AboutPage
        withPage({ AboutPage(it).also { value -> page = value } }) { list ->
            val apk = java.io.File(list.context.cacheDir, "updates/retry.apk").apply {
                parentFile!!.mkdirs(); writeText("verified by download before it reaches this state")
            }
            try {
                AboutPage::class.java.getDeclaredField("pendingUpdate").apply { isAccessible = true }.set(page, apk)
                list.findViewById<View>(R.id.btnCheckUpdate).performClick()
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                val intent = org.robolectric.Shadows.shadowOf(org.robolectric.RuntimeEnvironment.getApplication())
                    .nextStartedActivity
                assertEquals(android.content.Intent.ACTION_VIEW, intent.action)
                assertEquals("application/vnd.android.package-archive", intent.type)
                assertEquals("content", intent.data!!.scheme)
                assertTrue(intent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                assertTrue(apk.exists())
                assertTrue(list.findViewById<View>(R.id.btnCheckUpdate).isEnabled)
            } finally { apk.delete() }
        }
    }

    @Test fun shortcutsPageShowsOneReachableRowPerShortcut() = withPage({ ShortcutsPage(it) }) { list ->
        val order = downOrder(list)
        assertEquals(RemoteShortcut.entries.size, order.size)
        order.zip(RemoteShortcut.entries).forEach { (row, shortcut) ->
            val title = ((row as ViewGroup).getChildAt(0) as TextView).text.toString()
            assertEquals(list.context.getString(shortcut.titleResId), title)
            val key = (row.getChildAt(1) as TextView)
            assertTrue("Each row shows its key or that none is set", key.text.isNotBlank())
        }
    }
}
