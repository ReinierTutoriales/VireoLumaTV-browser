package com.reiniertutoriales.vireolumatv.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Frame timing of the remote journeys, without and with the bundled profile. */
@RunWith(AndroidJUnit4::class)
class RemoteNavigationBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun capsuleAndMenuNoCompilation() = measure(CompilationMode.None(), capsuleAndMenu)

    @Test fun capsuleAndMenuBaselineProfile() = measure(CompilationMode.Partial(BaselineProfileMode.Require), capsuleAndMenu)

    @Test fun tabsAndSettingsNoCompilation() = measure(CompilationMode.None(), tabsAndSettings)

    @Test fun tabsAndSettingsBaselineProfile() = measure(CompilationMode.Partial(BaselineProfileMode.Require), tabsAndSettings)

    private val capsuleAndMenu: MacrobenchmarkScope.() -> Unit = {
        traverseCapsule(strict = true)
        openAndCloseMenu(strict = true)
    }

    private val tabsAndSettings: MacrobenchmarkScope.() -> Unit = {
        openSwitchAndCloseTab(strict = true)
        openAndCloseSettings(strict = true)
    }

    private fun measure(mode: CompilationMode, journey: MacrobenchmarkScope.() -> Unit) = rule.measureRepeated(
        packageName = targetAppId,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        // No startup mode: a cold mode kills the process after setupBlock, before the journey.
        iterations = 5,
        setupBlock = {
            pressHome()
            startOnHome()
        },
        measureBlock = journey,
    )
}
