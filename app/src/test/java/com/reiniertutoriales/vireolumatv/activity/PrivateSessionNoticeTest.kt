package com.reiniertutoriales.vireolumatv.activity

import android.app.Application
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.Config as BrowserConfig
import com.reiniertutoriales.vireolumatv.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class PrivateSessionNoticeTest {
    @Test fun restoredNoticeRemainsSingleAndSuppressionIsAnExplicitChoice() {
        val app = RuntimeEnvironment.getApplication()
        val settings = BrowserConfig(app.getSharedPreferences("private-notice-test", 0))
        settings.privateHintSeen = false
        AppContext.init(app, settings)
        val first = Robolectric.buildActivity(AppCompatActivity::class.java)
        first.get().setTheme(R.style.AppTheme)
        first.setup()
        PrivateSessionNotice().showNow(first.get().supportFragmentManager, "notice")
        val state = Bundle()
        first.saveInstanceState(state).pause().stop().destroy()
        val restored = Robolectric.buildActivity(AppCompatActivity::class.java)
        restored.get().setTheme(R.style.AppTheme)
        restored.create(state).start().resume().visible()
        try {
            val fragments = restored.get().supportFragmentManager.fragments.filterIsInstance<PrivateSessionNotice>()
            assertEquals(1, fragments.size)
            assertFalse(settings.privateHintSeen)
            (fragments.single().requireDialog() as AlertDialog)
                .getButton(DialogInterface.BUTTON_NEUTRAL).performClick()
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            assertTrue(settings.privateHintSeen)
        } finally { restored.pause().stop().destroy() }
    }
}
