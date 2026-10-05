package com.reiniertutoriales.vireolumatv.activity.main

import android.app.Application
import com.reiniertutoriales.vireolumatv.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], qualifiers = "mdpi")
class BrandResourcesTest {
    @Test fun launcherAndBannerHaveConsistentLogicalSizes() {
        val app = RuntimeEnvironment.getApplication()
        val icon = app.getDrawable(R.drawable.ic_launcher)!!
        assertEquals(48, icon.intrinsicWidth)
        assertEquals(48, icon.intrinsicHeight)
        val banner = app.getDrawable(R.drawable.banner)!!
        // Android TV specifies 320 x 180 px at xhdpi, i.e. 160 x 90 dp.
        assertEquals(160, banner.intrinsicWidth)
        assertEquals(90, banner.intrinsicHeight)
    }
}
