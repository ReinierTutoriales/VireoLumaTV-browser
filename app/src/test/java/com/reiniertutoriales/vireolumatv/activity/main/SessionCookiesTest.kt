package com.reiniertutoriales.vireolumatv.activity.main

import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The real activity lifecycle asks to keep signed-in sessions when the browser leaves the screen. */
@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class SessionCookiesTest {
    @Test fun leavingTheScreenAsksToKeepTheSessionExceptInPrivateMode() {
        // Robolectric's service binding needs a component to report; the browser ignores a foreign binder.
        org.robolectric.Shadows.shadowOf(org.robolectric.RuntimeEnvironment.getApplication())
            .setComponentNameAndServiceForBindService(
                android.content.ComponentName("test", "Downloads"), android.os.Binder())
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.get()
        val modes = mutableListOf<Boolean>()
        activity.cookies = com.reiniertutoriales.vireolumatv.utils.CookiePersistence({ it.run() }, { modes += true })
        val wasPrivate = activity.config.privateBrowsing
        try {
            controller.setup()
            controller.pause().stop()
            assertEquals("One write when the browser leaves the screen", 1, modes.size)
            controller.start().resume()
            activity.config.privateBrowsing = true
            controller.pause().stop()
            assertEquals("Private sessions are not written to keep", 1, modes.size)
        } finally {
            activity.config.privateBrowsing = wasPrivate
            controller.destroy()
        }
    }
}
