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
        // The baseline profile plugin passes each variant's application ID (the foss build adds
        // ".foss"), so every distribution profiles its own installed package.
        packageName = InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: "com.reiniertutoriales.vireolumatv",
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
