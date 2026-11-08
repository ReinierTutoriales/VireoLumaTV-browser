package com.reiniertutoriales.vireolumatv.activity.main

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class WindowBackgroundTest {
    /**
     * The window draws the opaque backdrop. The browser root does not paint it again; the preview
     * overlays, which hide the page, keep their own. Compose screens draw no full-screen fill.
     */
    @Test fun windowOwnsTheOpaqueBackdropAndOnlyOverlaysPaintTheirOwn() {
        val owner = Robolectric.buildActivity(AppCompatActivity::class.java)
        owner.get().setTheme(R.style.AppTheme)
        owner.setup()
        try {
            val activity = owner.get()
            val attributes = activity.obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground))
            assertNotNull(attributes.getDrawable(0))
            attributes.recycle()
            val root = activity.layoutInflater.inflate(R.layout.browser_window, null)
            assertNull("Only the window draws the base background", root.background)
            assertNotNull(root.findViewById<View>(R.id.tab_preview).background)
            assertNotNull(root.findViewById<View>(R.id.preview_placeholder).background)
        } finally { owner.pause().stop().destroy() }
    }
}
