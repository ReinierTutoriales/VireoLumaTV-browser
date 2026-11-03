package com.reiniertutoriales.vireolumatv.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the code the browser runs at start-up and on the first remote presses: opening the
 * home page, moving the focus and opening the menu. The result is the app's baseline profile.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test fun startupAndFirstRemotePresses() = rule.collect(
        // Each flavor passes its application ID (build.gradle.kts): foss installs with ".foss".
        packageName = requireNotNull(InstrumentationRegistry.getArguments().getString("targetAppId")) {
            "targetAppId instrumentation argument is missing"
        },
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
        repeat(3) {
            device.pressDPadDown()
            device.waitForIdle()
        }
        device.pressMenu()
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
    }
}
