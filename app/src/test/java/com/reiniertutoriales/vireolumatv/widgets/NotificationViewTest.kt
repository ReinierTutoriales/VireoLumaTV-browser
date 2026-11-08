package com.reiniertutoriales.vireolumatv.widgets

import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvSymbol
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
        ToastBar.showBottomRight(root, TvSymbol.Block, "first")
        val second = ToastBar.showBottomRight(root, TvSymbol.Block, "second")
        assertEquals(1, root.childCount)
        assertEquals("second", second.message)
        assertFalse(second.isFocusable)
        ShadowLooper.idleMainLooper(ToastBar.VISIBLE_MS + 1000, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals("The message leaves on its own", 0, root.childCount)
    }
}
