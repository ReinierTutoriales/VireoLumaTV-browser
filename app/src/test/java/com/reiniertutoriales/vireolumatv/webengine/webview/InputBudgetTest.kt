package com.reiniertutoriales.vireolumatv.webengine.webview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputBudgetTest {
    private var time = 5_000L
    private val budget = InputBudget(100) { time }

    @Test fun inputAboveTheSecondsBudgetIsRefusedUntilTheNextSecond() {
        assertTrue(budget.take(60))
        assertTrue(budget.take(40))
        assertFalse(budget.take(1))
        time += 999
        assertFalse(budget.take(1))
        time += 1
        assertTrue(budget.take(100))
    }

    @Test fun oneRefusedCallDoesNotUseTheBudget() {
        assertTrue(budget.take(90))
        assertFalse(budget.take(20))
        assertTrue(budget.take(10))
    }
}
