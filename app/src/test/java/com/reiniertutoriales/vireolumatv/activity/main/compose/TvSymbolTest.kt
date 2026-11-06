package com.reiniertutoriales.vireolumatv.activity.main.compose

import android.content.res.Resources
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The icon catalog is an API-24-compatible resource contract.
 * A missing drawable must fail this test rather than silently switch renderer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [24, 28, 34])
class TvSymbolTest {
    @Test fun allSymbolsResolveToLocalDrawables() {
        val resources = RuntimeEnvironment.getApplication().resources
        val packageName = RuntimeEnvironment.getApplication().packageName
        assertTrue(TvSymbol.entries.isNotEmpty())
        for (symbol in TvSymbol.entries) {
            assertNotEquals("Missing resource for $symbol", 0, symbol.drawable)
            assertEquals("drawable", resources.getResourceTypeName(symbol.drawable))
            assertEquals(packageName, resources.getResourcePackageName(symbol.drawable))
            try {
                resources.getDrawable(symbol.drawable, null)
            } catch (e: Resources.NotFoundException) {
                throw AssertionError("Missing local icon for $symbol", e)
            }
        }
    }

    @Test fun browserCommandSymbolsAreDistinct() {
        val actions = listOf(
            TvSymbol.Back, TvSymbol.Forward, TvSymbol.Refresh, TvSymbol.Home,
            TvSymbol.More, TvSymbol.ShieldOn, TvSymbol.ShieldOff,
            TvSymbol.Add, TvSymbol.TabClose,
        )
        assertEquals(actions.size, actions.map { it.drawable }.toSet().size)
    }
}
