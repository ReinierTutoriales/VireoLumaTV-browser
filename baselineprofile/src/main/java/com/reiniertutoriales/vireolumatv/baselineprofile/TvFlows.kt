package com.reiniertutoriales.vireolumatv.baselineprofile

import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.io.File
import java.util.regex.Pattern

internal val targetAppId: String
    get() = requireNotNull(InstrumentationRegistry.getArguments().getString("targetAppId")) {
        "targetAppId instrumentation argument is missing"
    }

private const val TIMEOUT_MS = 10_000L
private val HOME_SEARCH get() = By.res(targetAppId, "home_search")
private val FIRST_TAB get() = By.res(Pattern.compile("tab_0_[0-9]+"))
private val SECOND_TAB get() = By.res(Pattern.compile("tab_1_[0-9]+"))
private val TAB_CONTROL get() = By.res(Pattern.compile("tab_[0-9]+_[0-9]+|tab_close_[0-9]+|new_tab_button"))
private val MENU_ENTRIES = listOf("history", "favorites", "downloads", "incognito", "settings", "close_tab", "close_application")

/** Clear browser data before each test/measurement, never between ART collection iterations. */
internal fun resetBrowserData() {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    check(device.executeShellCommand("pm clear $targetAppId").trim() == "Success") {
        "Unable to clear $targetAppId before the test"
    }
}

/** The launcher path restores Home without appending a deep-link tab on every iteration. */
internal fun MacrobenchmarkScope.startOnHome() {
    startActivityAndWait()
    waitForHome()
}

/** Old AOSP providers can show an update notice; dismiss only this app's negative button. */
internal fun MacrobenchmarkScope.waitForHome() {
    val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
    while (SystemClock.uptimeMillis() < deadline) {
        device.findObject(By.res("android", "button2"))
            ?.takeIf { it.applicationPackage == packageName }?.click()
        if (device.wait(Until.hasObject(HOME_SEARCH), 500)) {
            device.waitForIdle()
            return
        }
    }
    failWithScreen("Native Home did not appear")
}

private fun MacrobenchmarkScope.failWithScreen(message: String): Nothing {
    val media = InstrumentationRegistry.getInstrumentation().context.externalMediaDirs.first()
    val output = File(media, "additional_test_output").apply { mkdirs() }
    device.dumpWindowHierarchy(File(output, "missing-${System.currentTimeMillis()}.xml"))
    error(message)
}

private fun MacrobenchmarkScope.requireNode(selector: BySelector): UiObject2 =
    device.wait(Until.findObject(selector), TIMEOUT_MS)
        ?: failWithScreen("Expected control/state not found: $selector")

private fun MacrobenchmarkScope.requireGone(selector: BySelector) {
    if (!device.wait(Until.gone(selector), TIMEOUT_MS)) failWithScreen("Control did not close: $selector")
}

private fun MacrobenchmarkScope.press(key: () -> Boolean) {
    if (!key()) failWithScreen("Input injection failed")
    device.waitForIdle()
}

private fun MacrobenchmarkScope.focus(selector: BySelector, key: () -> Boolean, limit: Int = 12) {
    repeat(limit) {
        if (device.hasObject(By.copy(selector).focused(true))) return
        press(key)
    }
    requireNode(By.copy(selector).focused(true))
}

private fun MacrobenchmarkScope.openCapsule() {
    repeat(3) {
        if (device.hasObject(By.res("menu_button"))) {
            requireNode(By.focused(true).clickable(true))
            return
        }
        press { device.pressBack() }
        device.wait(Until.hasObject(By.res("menu_button")), 2_000)
    }
    requireNode(By.res("menu_button"))
    requireNode(By.focused(true).clickable(true))
}

/** Reach the address with the remote, without activating the editor. */
private fun MacrobenchmarkScope.focusAddress() {
    openCapsule()
    if (device.hasObject(TAB_CONTROL.focused(true))) {
        press { device.pressDPadUp() }
    }
    focus(By.res("home_search"), { device.pressDPadLeft() })
    requireNode(By.res("home_search").focused(true).clickable(true))
}

/** Every enabled command is reached in both directions; focus alone must not invoke it. */
internal fun MacrobenchmarkScope.traverseCapsule() {
    focusAddress()
    val commands = listOf("refresh_button", "home_button", "blocker_button", "menu_button")
    for (tag in commands) {
        press { device.pressDPadRight() }
        requireNode(By.res(tag).focused(true))
    }
    for (tag in commands.dropLast(1).reversed() + "home_search") {
        press { device.pressDPadLeft() }
        requireNode(By.res(tag).focused(true))
    }
    requireGone(By.res("menu_history"))
}

private fun MacrobenchmarkScope.openMenu() {
    focusAddress()
    focus(By.res("menu_button"), { device.pressDPadRight() })
    press { device.pressDPadCenter() }
    requireNode(By.res("menu_history").focused(true))
}

internal fun MacrobenchmarkScope.openAndCloseMenu() {
    openMenu()
    for (entry in MENU_ENTRIES.drop(1)) {
        press { device.pressDPadDown() }
        requireNode(By.res("menu_$entry").focused(true))
    }
    for (entry in MENU_ENTRIES.dropLast(1).reversed()) {
        press { device.pressDPadUp() }
        requireNode(By.res("menu_$entry").focused(true))
    }
    press { device.pressBack() }
    requireGone(By.res("menu_history"))
    requireNode(By.res("menu_button").focused(true))
}

internal fun MacrobenchmarkScope.openAndCloseSettings() {
    openMenu()
    for (entry in MENU_ENTRIES.drop(1).take(4)) {
        press { device.pressDPadDown() }
        requireNode(By.res("menu_$entry").focused(true))
    }
    press { device.pressDPadCenter() }
    requireNode(By.res(packageName, "general_page_tab"))
    requireGone(By.res("menu_settings"))
    val initial = requireNode(By.focused(true))
    val initialBounds = initial.visibleBounds
    val initialId = initial.resourceName
    press { device.pressDPadDown() }
    val next = requireNode(By.focused(true))
    if (next.resourceName == initialId && next.visibleBounds == initialBounds) {
        failWithScreen("D-pad Down did not move focus in Settings")
    }
    repeat(7) { press { device.pressDPadDown() } }
    repeat(8) { press { device.pressDPadUp() } }
    press { device.pressBack() }
    requireGone(By.res(packageName, "general_page_tab"))
    requireNode(By.res("menu_button").focused(true))
    requireNode(HOME_SEARCH)
}

private fun MacrobenchmarkScope.focusFirstTab() {
    focusAddress()
    press { device.pressDPadDown() }
    requireNode(TAB_CONTROL.focused(true))
    focus(FIRST_TAB, { device.pressDPadLeft() })
}

/** Add a tab, activate the first with OK, then close the background tab and verify selection. */
internal fun MacrobenchmarkScope.openSwitchAndCloseTab() {
    focusFirstTab()
    val originalTab = requireNotNull(requireNode(FIRST_TAB).resourceName)
    requireGone(SECOND_TAB)
    focus(By.res("new_tab_button"), { device.pressDPadRight() })
    press { device.pressDPadCenter() }
    waitForHome()
    openCapsule()
    requireNode(SECOND_TAB.selected(true))
    focusFirstTab()
    press { device.pressDPadCenter() }
    waitForHome()
    openCapsule()
    requireNode(By.res(originalTab).selected(true))
    focusFirstTab()
    focus(By.res("tab_close_1"), { device.pressDPadRight() })
    press { device.pressDPadCenter() }
    requireGone(SECOND_TAB)
    requireNode(By.res(originalTab).selected(true))
    requireNode(HOME_SEARCH)
}
