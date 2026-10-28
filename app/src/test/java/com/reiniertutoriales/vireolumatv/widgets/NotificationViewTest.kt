package com.reiniertutoriales.vireolumatv.widgets

import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class NotificationViewTest {
    @Test fun aNewMessageReplacesTheOldOneWhichLeavesWithoutFocus() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).apply { get().setTheme(R.style.AppTheme) }.setup().get()
        val root = RelativeLayout(activity)
        activity.setContentView(root)
        NotificationView.showBottomRight(root, R.drawable.ic_block, "first")
        val second = NotificationView.showBottomRight(root, R.drawable.ic_block, "second")
        assertEquals(1, root.childCount)
        assertEquals("second", second.findViewById<TextView>(R.id.message_text).text.toString())
        assertFalse(second.isFocusable)
        ShadowLooper.idleMainLooper(NotificationView.VISIBLE_MS + 1000, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals("The message leaves on its own", 0, root.childCount)
    }
}
