package com.reiniertutoriales.vireolumatv.baselineprofile

import android.content.Intent
import android.net.Uri
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Until

/** Package of the installed flavor, passed by the flavor's instrumentation arguments. */
internal val targetAppId: String
    get() = requireNotNull(InstrumentationRegistry.getArguments().getString("targetAppId")) {
        "targetAppId instrumentation argument is missing"
    }

private const val TIMEOUT_MS = 10_000L

/** The native Home page used by the startup CI check; it needs no network. */
private val HOME = Uri.parse("https://vireolumatv.invalid/appcontent/home/")

/** Starts on native Home, dismisses the first-run WebView notice of old images, waits for Home. */
internal fun MacrobenchmarkScope.startOnHome() {
    startActivityAndWait { intent: Intent -> intent.data = HOME }
    waitForHome()
}

/** Waits for Home; only the launch requires it, later journeys continue from wherever Back lands. */
internal fun MacrobenchmarkScope.waitForHome(required: Boolean = true) {
    device.findObject(By.res("android", "button2"))?.takeIf { it.applicationPackage == packageName }?.click()
    val shown = device.wait(Until.hasObject(By.res("home_search")), TIMEOUT_MS)
    check(shown || !required) { "Native Home did not appear" }
    device.waitForIdle()
}

private fun MacrobenchmarkScope.clickIfPresent(selector: BySelector): Boolean {
    val target = device.wait(Until.findObject(selector), TIMEOUT_MS) ?: return false
    target.click()
    device.waitForIdle()
    return true
}

private fun MacrobenchmarkScope.press(times: Int, key: () -> Boolean) = repeat(times) {
    key()
    device.waitForIdle()
}

/** D-pad over the top capsule and back: address, history buttons, shield and menu. */
internal fun MacrobenchmarkScope.traverseCapsule() {
    press(1) { device.pressDPadUp() }
    press(7) { device.pressDPadRight() }
    press(7) { device.pressDPadLeft() }
    press(2) { device.pressDPadDown() }
}

/** Opens the side menu, moves through its rows and closes it with Back. */
internal fun MacrobenchmarkScope.openAndCloseMenu() {
    if (!clickIfPresent(By.res("menu_button"))) return
    press(6) { device.pressDPadDown() }
    press(6) { device.pressDPadUp() }
    press(1) { device.pressBack() }
}

/** Opens Settings from the menu, scrolls its rows and returns with Back. */
internal fun MacrobenchmarkScope.openAndCloseSettings() {
    if (!clickIfPresent(By.res("menu_button"))) return
    if (!clickIfPresent(By.text("Settings"))) {
        press(1) { device.pressBack() }
        return
    }
    press(8) { device.pressDPadDown() }
    press(8) { device.pressDPadUp() }
    press(1) { device.pressBack() }
    waitForHome(required = false)
}

/** Adds a tab from the tab rail, switches back and closes the new one with its X. */
internal fun MacrobenchmarkScope.openSwitchAndCloseTab() {
    if (!clickIfPresent(By.desc("New tab"))) return
    waitForHome(required = false)
    press(1) { device.pressDPadUp() }
    press(2) { device.pressDPadLeft() }
    val closers = device.findObjects(By.desc("Close tab"))
    if (closers.size > 1) {
        closers.last().click()
        device.waitForIdle()
    }
}
