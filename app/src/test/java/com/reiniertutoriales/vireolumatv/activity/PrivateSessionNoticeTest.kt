package com.reiniertutoriales.vireolumatv.activity

import android.app.Application
import android.content.DialogInterface
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.Config as BrowserConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class PrivateSessionNoticeTest {
    private fun settings(): BrowserConfig {
        val app = RuntimeEnvironment.getApplication()
        return BrowserConfig(app.getSharedPreferences("private-notice-test", 0)).also {
            it.privateHintSeen = false
            AppContext.init(app, it)
        }
    }

    @Test fun anOpenNoticeComesBackOnceAfterRecreationAndSuppressionIsAnExplicitChoice() {
        val settings = settings()
        val first = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        assertNotNull(PrivateSessionNotice.attach(first.get()))
        val state = Bundle()
        first.saveInstanceState(state).pause().stop().destroy()
        val restored = Robolectric.buildActivity(ComponentActivity::class.java).create(state).start().resume().visible()
        try {
            val notice = requireNotNull(PrivateSessionNotice.attach(restored.get()))
            assertTrue(notice.isShowing)
            assertFalse(settings.privateHintSeen)
            notice.press(DialogInterface.BUTTON_NEUTRAL)
            assertTrue(settings.privateHintSeen)
        } finally { restored.pause().stop().destroy() }
    }

    @Test fun aNoticeClosedBeforeRecreationDoesNotReturn() {
        settings()
        val first = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        requireNotNull(PrivateSessionNotice.attach(first.get())).press(DialogInterface.BUTTON_POSITIVE)
        val state = Bundle()
        first.saveInstanceState(state).pause().stop().destroy()
        val restored = Robolectric.buildActivity(ComponentActivity::class.java).create(state).start().resume().visible()
        try {
            assertNull(PrivateSessionNotice.attach(restored.get()))
        } finally { restored.pause().stop().destroy() }
    }
}
