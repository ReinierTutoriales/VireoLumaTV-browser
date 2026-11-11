package com.reiniertutoriales.vireolumatv.activity.main

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import com.reiniertutoriales.vireolumatv.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "mdpi")
class BrandResourcesTest {
    @Config(sdk = [24, 25])
    @Test fun legacyTvLaunchersKeepTheirRequiredLogicalSizes() {
        val app = RuntimeEnvironment.getApplication()
        val icon = app.getDrawable(R.mipmap.ic_launcher)!!
        assertEquals(80, icon.intrinsicWidth)
        assertEquals(80, icon.intrinsicHeight)
        val banner = app.getDrawable(R.mipmap.tv_banner)!!
        assertEquals(160, banner.intrinsicWidth)
        assertEquals(90, banner.intrinsicHeight)
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(sdk = [26, 28, 33, 37])
    @Test fun adaptiveIconAndBannerRenderTheApprovedPalette() {
        val app = RuntimeEnvironment.getApplication()
        val icon = app.getDrawable(R.mipmap.ic_launcher)!!
        val banner = app.getDrawable(R.mipmap.tv_banner)!!
        assertTrue(icon is AdaptiveIconDrawable)
        assertTrue(banner is AdaptiveIconDrawable)
        val foreground = app.getColor(R.color.brand_foreground)
        val background = app.getColor(R.color.brand_background)
        fun render(name: String, drawable: Drawable, width: Int, height: Int) {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                drawable.setBounds(0, 0, width, height)
                drawable.draw(Canvas(bitmap))
                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                assertTrue("Visible mark in $name", pixels.count { it == foreground } > width * height / 100)
                assertTrue("Full-bleed backdrop in $name", pixels.count { it == background } > width * height / 100)
                val output = File("build/reports/ui/brand-$name-api-${Build.VERSION.SDK_INT}.png")
                output.parentFile!!.mkdirs()
                output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { bitmap.recycle() }
        }
        render("icon", icon, 320, 320)
        render("banner", banner, 640, 360)
        render("internal", app.getDrawable(R.drawable.brand_logo)!!, 320, 320)
    }
}
