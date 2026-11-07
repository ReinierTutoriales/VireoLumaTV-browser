package com.reiniertutoriales.vireolumatv.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the bundled profiles. Only [startup] feeds the startup profile (DEX layout of the
 * first frame); the other journeys extend the baseline profile with the TV remote paths.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Before fun cleanBrowserState() = resetBrowserData()

    @Test fun startup() = rule.collect(packageName = targetAppId, includeInStartupProfile = true) {
        pressHome()
        startOnHome()
    }

    @Test fun remoteJourneys() = rule.collect(packageName = targetAppId, includeInStartupProfile = false) {
        pressHome()
        startOnHome()
        traverseCapsule()
        openAndCloseMenu()
        openSwitchAndCloseTab()
        openAndCloseSettings()
    }
}
