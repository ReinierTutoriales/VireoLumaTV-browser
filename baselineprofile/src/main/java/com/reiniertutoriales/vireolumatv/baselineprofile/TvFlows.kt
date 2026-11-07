package com.reiniertutoriales.vireolumatv.baselineprofile

import android.content.Intent
import android.net.Uri
import java.io.File
import java.util.regex.Pattern
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

/** Home's search field: the native View id or the exported Compose tag, as in the startup CI check. */
private val HOME_SEARCH = By.res(Pattern.compile("(.+:id/)?home_search"))

/** Opens native Home with the same intent as the startup CI check (`am start -n … -d …`). */
internal fun MacrobenchmarkScope.startOnHome() {
    startActivityAndWait { intent: Intent ->
        intent.action = null
        intent.removeCategory(Intent.CATEGORY_LAUNCHER)
        intent.data = HOME
    }
    waitForHome()
}

/**
 * Waits for Home, closing the app's notice about an outdated WebView that old emulator images
 * show after launch. Only the launch requires Home; later journeys continue wherever Back lands.
 */
internal fun MacrobenchmarkScope.waitForHome(required: Boolean = true) {
    val deadline = System.currentTimeMillis() + TIMEOUT_MS
    var shown = false
    while (!shown && System.currentTimeMillis() < deadline) {
        device.findObject(By.res("android", "button2"))
            ?.takeIf { it.applicationPackage == packageName }
            ?.click()
        shown = device.wait(Until.hasObject(HOME_SEARCH), 500)
    }
    if (!shown && required) {
        // Kept with the test outputs to show what was on screen instead of Home.
        val media = InstrumentationRegistry.getInstrumentation().context.externalMediaDirs.first()
        device.dumpWindowHierarchy(File(media, "home-missing-${System.currentTimeMillis()}.xml"))
        error("Native Home did not appear")
    }
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
