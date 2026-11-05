package com.reiniertutoriales.vireolumatv.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    private val targetAppId: String
        get() = requireNotNull(InstrumentationRegistry.getArguments().getString("targetAppId")) {
            "targetAppId instrumentation argument is missing"
        }

    @Test fun startup() = rule.collect(
        packageName = targetAppId,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
    }

    @Test fun firstRemotePresses() = rule.collect(
        packageName = targetAppId,
        includeInStartupProfile = false,
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
