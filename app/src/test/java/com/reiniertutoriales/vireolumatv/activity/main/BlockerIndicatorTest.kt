package com.reiniertutoriales.vireolumatv.activity.main

import org.junit.Assert.assertEquals
import org.junit.Test

class BlockerIndicatorTest {
    @Test fun indicatorReportsEffectiveProtectionRatherThanJustTheToggle() {
        val idle = AdblockModel.UpdateResult.IDLE
        assertEquals(BlockerIndicator.PAUSED, blockerIndicator(false, true, false, idle))
        assertEquals(BlockerIndicator.LOADING, blockerIndicator(true, false, true, idle))
        assertEquals(BlockerIndicator.UNAVAILABLE,
            blockerIndicator(true, false, false, AdblockModel.UpdateResult.ERROR))
        assertEquals(BlockerIndicator.REFRESHING, blockerIndicator(true, true, true, idle))
        assertEquals(BlockerIndicator.ACTIVE,
            blockerIndicator(true, true, false, AdblockModel.UpdateResult.UPDATED))
        assertEquals(BlockerIndicator.PARTIAL,
            blockerIndicator(true, true, false, AdblockModel.UpdateResult.PARTIAL))
        for (result in listOf(AdblockModel.UpdateResult.CACHED,
            AdblockModel.UpdateResult.ERROR)) {
            assertEquals(BlockerIndicator.SAVED, blockerIndicator(true, true, false, result))
        }
    }
}
