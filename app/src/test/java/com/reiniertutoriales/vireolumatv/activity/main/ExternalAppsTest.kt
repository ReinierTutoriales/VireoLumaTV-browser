package com.reiniertutoriales.vireolumatv.activity.main

import android.content.DialogInterface
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import org.junit.Assert.assertEquals
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
    private val controller = Robolectric.buildActivity(AppCompatActivity::class.java).apply {
        get().setTheme(R.style.AppTheme)
        setup()
    }
    private val activity = controller.get()

    private fun answer(button: Int) {
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(button).performClick()
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
}
