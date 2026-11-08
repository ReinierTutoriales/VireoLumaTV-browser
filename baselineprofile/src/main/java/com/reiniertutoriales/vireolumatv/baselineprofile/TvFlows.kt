package com.reiniertutoriales.vireolumatv.baselineprofile

import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Condition
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
    // A real navigation key establishes remote input mode before the app requests focus.
    press { device.pressDPadUp() }
    startActivityAndWait()
    waitForHome()
}

/** A new selector each time: BySelector's filters mutate the instance they are called on. */
private fun noticeDismiss() = By.res("dialog_button_-2")

/** Old AOSP providers can show an update notice; dismiss only this app's negative button. */
internal fun MacrobenchmarkScope.waitForHome() {
    val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
    while (SystemClock.uptimeMillis() < deadline) {
        // TvDialog starts on its negative (safe) button, which exports the tag dialog_button_-2.
        device.findObject(noticeDismiss())?.takeIf { it.applicationPackage == packageName }?.let {
            requireNode(noticeDismiss().focused(true))
            press { device.pressDPadCenter() }
        }
        if (device.wait(Until.hasObject(HOME_SEARCH), 500)) {
            device.waitForIdle()
            return
        }
    }
    failWithScreen("Native Home did not appear")
}

private fun MacrobenchmarkScope.failWithScreen(message: String): Nothing {
    println("Focus at failure: " + device.findObjects(By.focused(true)).joinToString {
        "id=${it.resourceName}, class=${it.className}, clickable=${it.isClickable}, bounds=${it.visibleBounds}"
    })
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

/**
 * Injects one key. UI Automator reports `false` when the key coincides with a window change (the
 * menu dialog opening) even though it was delivered; retrying would press twice, so the state
 * check after each step decides instead.
 */
private fun MacrobenchmarkScope.press(key: () -> Boolean) {
    if (!key()) println("Key injection reported false; the next state check decides")
    device.waitForIdle()
}

/**
 * True once [selector] is the only focused node of its window. Under load Compose can report the
 * previous node as focused for a moment too; acting on such a read sends OK to the wrong control.
 * Other windows keep their own focused View (the capsule behind the menu dialog), so the check
 * stays within the target's window.
 */
private fun MacrobenchmarkScope.soleFocus(selector: BySelector, timeout: Long): Boolean =
    device.wait(Condition<UiDevice, Boolean> { ui ->
        val node = ui.findObject(By.copy(selector).focused(true)) ?: return@Condition false
        var root = node
        while (true) root = root.parent ?: break
        root.findObjects(By.focused(true)).size == 1
    }, timeout) == true

private fun MacrobenchmarkScope.requireFocus(selector: BySelector) {
    if (!soleFocus(selector, TIMEOUT_MS)) failWithScreen("Expected sole focus not found: $selector")
}

private fun MacrobenchmarkScope.focus(selector: BySelector, key: () -> Boolean, limit: Int = 12) {
    repeat(limit) {
        if (soleFocus(selector, 300)) return
        val before = device.findObject(By.focused(true))?.let { it.resourceName to it.visibleBounds }
        press(key)
        // Wait until the accessibility tree reports the move; a stale read would press again
        // and step past the target on a slow, uncompiled run.
        if (soleFocus(selector, 300)) return
        device.wait(Condition<UiDevice, Boolean> { ui ->
            ui.findObject(By.focused(true))?.let { it.resourceName to it.visibleBounds } != before
        }, 1_000L)
    }
    if (!soleFocus(selector, TIMEOUT_MS)) failWithScreen("Expected sole focus not reached: $selector")
}

private fun MacrobenchmarkScope.openCapsule() {
    if (device.hasObject(By.res("menu_button"))) {
        // A selected Tab can have remote focus without an accessibility click action.
        requireNode(By.focused(true).focusable(true))
        return
    }
    repeat(3) {
        press { device.pressBack() }
        if (device.wait(Until.hasObject(By.res("menu_button")), 2_000)) {
            requireFocus(By.res(Pattern.compile("back_button|home_button")))
            return
        }
    }
    failWithScreen("Back did not open the capsule")
}

/** Reach the address with the remote, without activating the editor. */
private fun MacrobenchmarkScope.focusAddress() {
    openCapsule()
    if (device.hasObject(TAB_CONTROL.focused(true))) {
        press { device.pressDPadUp() }
    }
    focus(By.res("home_search"), { device.pressDPadLeft() })
    requireFocus(By.res("home_search").clickable(true))
}

/** Every enabled command is reached in both directions; focus alone must not invoke it. */
internal fun MacrobenchmarkScope.traverseCapsule() {
    focusAddress()
    val commands = listOf("refresh_button", "home_button", "blocker_button", "menu_button")
    for (tag in commands) {
        press { device.pressDPadRight() }
        requireFocus(By.res(tag))
    }
    for (tag in commands.dropLast(1).reversed() + "home_search") {
        press { device.pressDPadLeft() }
        requireFocus(By.res(tag))
    }
    requireGone(By.res("menu_history"))
}

private fun MacrobenchmarkScope.openMenu() {
    focusAddress()
    focus(By.res("menu_button"), { device.pressDPadRight() })
    press { device.pressDPadCenter() }
    requireFocus(By.res("menu_history"))
}

internal fun MacrobenchmarkScope.openAndCloseMenu() {
    openMenu()
    for (entry in MENU_ENTRIES.drop(1)) {
        press { device.pressDPadDown() }
        requireFocus(By.res("menu_$entry"))
    }
    for (entry in MENU_ENTRIES.dropLast(1).reversed()) {
        press { device.pressDPadUp() }
        requireFocus(By.res("menu_$entry"))
    }
    press { device.pressBack() }
    requireGone(By.res("menu_history"))
    requireFocus(By.res("menu_button"))
}

internal fun MacrobenchmarkScope.openAndCloseSettings() {
    openMenu()
    for (entry in MENU_ENTRIES.drop(1).take(4)) {
        press { device.pressDPadDown() }
        requireFocus(By.res("menu_$entry"))
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
    requireFocus(By.res("menu_button"))
    requireNode(HOME_SEARCH)
}

/**
 * One D-pad step that must land on [expected]. Each step waits for its own result, so queued
 * presses never overshoot while accessibility lags on a loaded emulator, and the order is checked.
 */
private fun MacrobenchmarkScope.step(expected: BySelector, key: () -> Boolean) {
    press(key)
    if (!soleFocus(expected, TIMEOUT_MS)) failWithScreen("D-pad step did not land on $expected")
}

private fun MacrobenchmarkScope.focusFirstTab() {
    focusAddress()
    press { device.pressDPadDown() }
    requireFocus(TAB_CONTROL)
    focus(FIRST_TAB, { device.pressDPadLeft() })
}

/** Add a tab, activate the first with OK, then close the background tab and verify selection. */
internal fun MacrobenchmarkScope.openSwitchAndCloseTab() {
    focusFirstTab()
    val originalTab = requireNotNull(requireNode(FIRST_TAB).resourceName)
    requireGone(SECOND_TAB)
    // Title -> its X -> "+": the X of each tab is a D-pad stop of its own.
    step(By.res("tab_close_0")) { device.pressDPadRight() }
    step(By.res("new_tab_button")) { device.pressDPadRight() }
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
    step(By.res("tab_close_0")) { device.pressDPadRight() }
    step(SECOND_TAB) { device.pressDPadRight() }
    step(By.res("tab_close_1")) { device.pressDPadRight() }
    press { device.pressDPadCenter() }
    requireGone(SECOND_TAB)
    requireNode(By.res(originalTab).selected(true))
    requireNode(HOME_SEARCH)
}
