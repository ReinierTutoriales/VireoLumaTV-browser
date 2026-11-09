package com.reiniertutoriales.vireolumatv.activity.history

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.HistoryItem
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class HistorySelectionTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun longPressSelectsAndBackLeavesSelectionBeforeClosing() {
        val ids = runBlocking(Dispatchers.IO) {
            AppDatabase.db.clearAllTables()
            listOf(
                AppDatabase.db.visitDao().insert(HistoryItem().apply { time = 2; title = "B"; url = "https://b.test" }),
                AppDatabase.db.visitDao().insert(HistoryItem().apply { time = 1; title = "A"; url = "https://a.test" }),
            )
        }
        ActivityScenario.launch(HistoryActivity::class.java).use { scenario ->
            compose.waitUntil(5_000) {
                compose.onAllNodes(hasTestTag("history_row_${ids[0]}")).fetchSemanticsNodes().isNotEmpty()
            }
            // One date header and two rows, and the first row has the focus.
            assertEquals(1, compose.onAllNodes(hasTestTag("history_day_1")).fetchSemanticsNodes().size +
                compose.onAllNodes(hasTestTag("history_day_2")).fetchSemanticsNodes().size)
            compose.onNodeWithTag("history_row_${ids[1]}").assertExists()
            compose.onNodeWithTag("history_delete_selected").assertDoesNotExist()

            compose.onNodeWithTag("history_row_${ids[0]}").performTouchInput { longClick() }
            compose.onNodeWithTag("history_delete_selected").assertExists()

            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithTag("history_delete_selected").assertDoesNotExist()
            scenario.onActivity { activity ->
                assertFalse("Back first leaves selection mode", activity.isFinishing)
                activity.onBackPressedDispatcher.onBackPressed()
                assertTrue("A second Back closes the screen", activity.isFinishing)
            }
        }
    }

    @Test fun thePickedAddressComesBackOnlyWithAnOkResult() {
        val answer = HistoryActivity.PickAddress.answer("https://picked.test/")
        assertEquals("https://picked.test/", HistoryActivity.PickAddress.parseResult(android.app.Activity.RESULT_OK, answer))
        assertEquals(null, HistoryActivity.PickAddress.parseResult(android.app.Activity.RESULT_CANCELED, answer))
        assertEquals(null, HistoryActivity.PickAddress.parseResult(android.app.Activity.RESULT_OK, null))
    }
}
