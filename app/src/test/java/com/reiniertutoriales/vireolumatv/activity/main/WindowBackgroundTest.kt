package com.reiniertutoriales.vireolumatv.activity.main

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Compares the composed image, including the window, rather than treating transparency as black. */
@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28], qualifiers = "w960dp-h540dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WindowBackgroundTest {
    @Test fun windowOwnsTheOpaqueBackdropAndRemovingDuplicateRootsKeepsEveryPixel() {
        val owner = Robolectric.buildActivity(AppCompatActivity::class.java)
        owner.get().setTheme(R.style.AppTheme)
        owner.setup()
        try {
            val activity = owner.get()
            val attributes = activity.obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground))
            val background = requireNotNull(attributes.getDrawable(0))
            attributes.recycle()
            val color = ContextCompat.getColor(activity, R.color.background)
            for (layout in listOf(R.layout.browser_window, R.layout.history_screen,
                R.layout.downloads_screen, R.layout.settings_screen)) {
                val root = activity.layoutInflater.inflate(layout, null)
                assertNull("Only the window draws the base background: $layout", root.background)
                root.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, 960, 540)
                fun pixels(): IntArray {
                    val bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    val bounds = Rect(background.bounds)
                    background.setBounds(0, 0, bitmap.width, bitmap.height)
                    background.draw(canvas)
                    background.bounds = bounds
                    root.draw(canvas)
                    val result = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(result, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    bitmap.recycle()
                    return result
                }
                val current = pixels()
                root.setBackgroundColor(color) // The former redundant root paint.
                assertArrayEquals("Backdrop removal must preserve the composed image: $layout", current, pixels())
                root.background = null
                if (layout == R.layout.browser_window) {
                    // Preview overlays obscure the page and therefore keep their own background.
                    assertNotNull(root.findViewById<View>(R.id.tab_preview).background)
                    assertNotNull(root.findViewById<View>(R.id.preview_placeholder).background)
                }
            }
        } finally { owner.pause().stop().destroy() }
    }
}
