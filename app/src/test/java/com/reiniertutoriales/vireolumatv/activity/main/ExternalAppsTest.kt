package com.reiniertutoriales.vireolumatv.activity.main

import android.content.DialogInterface
import android.content.Intent
import androidx.activity.ComponentActivity
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper

/** A page's non-web address reaches another app only after the user agrees, and only a browsable activity. */
@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class ExternalAppsTest {
    private val controller = Robolectric.buildActivity(ComponentActivity::class.java).apply {
        get().setTheme(R.style.AppTheme)
        setup()
    }
    private val activity = controller.get()

    private fun answer(button: Int) {
        (ShadowDialog.getLatestDialog() as TvDialog).press(button)
        ShadowLooper.idleMainLooper()
    }

    @Test fun aNonWebAddressOpensOnlyBrowsableActivitiesAfterConfirmation() {
        val apps = ExternalApps(activity)
        apps.askToOpenInApp("market://details?id=example.app")
        assertNull("Nothing starts before the user agrees", shadowOf(activity).peekNextStartedActivity())
        answer(DialogInterface.BUTTON_POSITIVE)
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("market://details?id=example.app", started.dataString)
        assertTrue(started.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertNull(started.component)
        assertNull(started.selector)
        controller.pause().stop().destroy()
    }

    @Test fun cancellingStartsNothing() {
        ExternalApps(activity).askToOpenInApp("tel:123")
        answer(DialogInterface.BUTTON_NEGATIVE)
        assertNull(shadowOf(activity).nextStartedActivity)
        controller.pause().stop().destroy()
    }

    @Test fun openInAppOpensTheSingleOtherAppOrAChooserExcludingThisBrowser() {
        val pm = shadowOf(activity.packageManager)
        val selfInfo = android.content.pm.ResolveInfo().apply {
            activityInfo = android.content.pm.ActivityInfo().apply {
                packageName = activity.packageName
                name = MainActivity::class.java.name
            }
        }
        val firstExternal = android.content.pm.ResolveInfo().apply {
            activityInfo = android.content.pm.ActivityInfo().apply {
                packageName = "com.example.videoplayer"
                name = "PlayerActivity"
            }
        }
        val secondExternal = android.content.pm.ResolveInfo().apply {
            activityInfo = android.content.pm.ActivityInfo().apply {
                packageName = "com.example.otherbrowser"
                name = "BrowserActivity"
            }
        }
        val probe = ExternalWebNavigation.parseIntentUrl("https://example.com/watch")!!
        pm.addResolveInfoForIntent(probe, selfInfo)
        ExternalApps(activity).openInApp("https://example.com/watch")
        assertNull("Must not reopen itself when no other app handles the link", shadowOf(activity).nextStartedActivity)

        pm.addResolveInfoForIntent(probe, firstExternal)
        ExternalApps(activity).openInApp("https://example.com/watch")
        val single = shadowOf(activity).nextStartedActivity
        assertEquals(android.content.ComponentName("com.example.videoplayer", "PlayerActivity"), single.component)
        assertEquals("https://example.com/watch", single.dataString)

        pm.addResolveInfoForIntent(probe, secondExternal)
        ExternalApps(activity).openInApp("https://example.com/watch")
        val chooser = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val excluded = chooser.getParcelableArrayExtra(Intent.EXTRA_EXCLUDE_COMPONENTS)
        assertEquals(
            listOf(android.content.ComponentName(activity.packageName, MainActivity::class.java.name)),
            excluded?.toList()
        )

        // When another app (e.g. YouTube TV) is the default handler, open it directly without a chooser.
        val preferredProbe = ExternalWebNavigation.parseIntentUrl("https://video.example/watch")!!
        pm.addResolveInfoForIntent(preferredProbe, firstExternal)
        pm.addResolveInfoForIntent(preferredProbe, secondExternal)
        pm.addResolveInfoForIntent(preferredProbe, selfInfo)
        ExternalApps(activity).openInApp("https://video.example/watch")
        val direct = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, direct.action)
        assertNull(direct.component)
        assertEquals("https://video.example/watch", direct.dataString)
        controller.pause().stop().destroy()
    }
}
