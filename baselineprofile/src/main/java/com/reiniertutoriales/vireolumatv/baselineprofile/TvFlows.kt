package com.reiniertutoriales.vireolumatv.baselineprofile

import android.content.Intent
import android.net.Uri
import java.io.File
import java.util.regex.Pattern
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
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

/**
 * Clears the app's data before a measured iteration: every launch intent adds a tab, and restored
 * tabs would make later iterations and tests heavier than earlier ones. Compiled code is kept.
 */
internal fun MacrobenchmarkScope.resetBrowserData() {
    device.executeShellCommand("pm clear $packageName")
}

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
    if (!shown && required) failWithScreen("Native Home did not appear")
    device.waitForIdle()
}

/** Keeps the screen hierarchy with the test outputs, then fails with [message]. */
private fun MacrobenchmarkScope.failWithScreen(message: String): Nothing {
    val media = InstrumentationRegistry.getInstrumentation().context.externalMediaDirs.first()
    val output = File(media, "additional_test_output").apply { mkdirs() }
    device.dumpWindowHierarchy(File(output, "missing-${System.currentTimeMillis()}.xml"))
    error(message)
}

/**
 * Clicks [selector] when it appears. Benchmarks pass [strict] so a missing control fails the run
 * instead of timing a journey that did not happen; the profile generator tolerates it.
 */
private fun MacrobenchmarkScope.clickIfPresent(selector: BySelector, strict: Boolean): Boolean {
    val target = device.wait(Until.findObject(selector), TIMEOUT_MS)
        ?: if (strict) failWithScreen("Control not found: $selector") else return false
    target.click()
    device.waitForIdle()
    return true
}

private fun MacrobenchmarkScope.press(times: Int, key: () -> Boolean) = repeat(times) {
    key()
    device.waitForIdle()
}

/**
 * Back opens the hidden capsule from a page without history, as on the remote. A first press can
 * be taken by an inner state (D-pad mode, see MainActivity.consumeBack), so up to three are sent.
 */
private fun MacrobenchmarkScope.openCapsule(strict: Boolean): Boolean {
    repeat(3) {
        if (device.hasObject(By.res("menu_button"))) return true
        press(1) { device.pressBack() }
        if (device.wait(Until.hasObject(By.res("menu_button")), 2_000)) return true
    }
    if (strict) failWithScreen("Back did not open the capsule")
    return false
}

/** Opens the capsule with Back and moves over it with the D-pad: address, buttons, shield and menu. */
internal fun MacrobenchmarkScope.traverseCapsule(strict: Boolean = false) {
    if (!openCapsule(strict)) return
    press(7) { device.pressDPadRight() }
    press(7) { device.pressDPadLeft() }
    press(1) { device.pressDPadDown() }
    press(1) { device.pressDPadUp() }
}

/** Opens the side menu, moves through its rows and closes it with Back. */
internal fun MacrobenchmarkScope.openAndCloseMenu(strict: Boolean = false) {
    if (!openCapsule(strict)) return
    if (!clickIfPresent(By.res("menu_button"), strict)) return
    press(6) { device.pressDPadDown() }
    press(6) { device.pressDPadUp() }
    press(1) { device.pressBack() }
}

/** Opens Settings from the menu, scrolls its rows and returns with Back. */
internal fun MacrobenchmarkScope.openAndCloseSettings(strict: Boolean = false) {
    if (!openCapsule(strict)) return
    if (!clickIfPresent(By.res("menu_button"), strict)) return
    if (!clickIfPresent(By.text("Settings"), strict)) {
        press(1) { device.pressBack() }
        return
    }
    press(8) { device.pressDPadDown() }
    press(8) { device.pressDPadUp() }
    press(1) { device.pressBack() }
    waitForHome(required = false)
}

/** Adds a tab from the tab rail, switches back and closes the new one with its X. */
internal fun MacrobenchmarkScope.openSwitchAndCloseTab(strict: Boolean = false) {
    if (!openCapsule(strict)) return
    // Each launch intent adds a tab, so "+" can be past the end of the scrolling tab row.
    val row = device.findObject(By.clazz("android.widget.HorizontalScrollView"))
    repeat(6) {
        if (device.hasObject(By.desc("New tab"))) return@repeat
        row?.scroll(Direction.RIGHT, 1f)
        device.waitForIdle()
    }
    if (!clickIfPresent(By.desc("New tab"), strict)) return
    waitForHome(required = false)
    if (!openCapsule(strict)) return
    // Down reaches the tab row; Left/Right move across the tabs and their close buttons.
    press(1) { device.pressDPadDown() }
    press(3) { device.pressDPadLeft() }
    press(3) { device.pressDPadRight() }
    val closers = device.findObjects(By.desc("Close tab"))
    if (closers.size > 1) {
        closers.last().click()
        device.waitForIdle()
    } else if (strict) {
        failWithScreen("The new tab has no close button")
    }
}
