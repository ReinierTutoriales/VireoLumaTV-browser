package com.reiniertutoriales.vireolumatv.activity.history

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.HistoryItem
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class HistorySelectionTest {
    @Test fun longPressSelectsAndBackLeavesSelectionBeforeClosing() {
        runBlocking(Dispatchers.IO) {
            AppDatabase.db.clearAllTables()
            AppDatabase.db.visitDao().insert(HistoryItem().apply { time = 2; title = "B"; url = "https://b.test" })
            AppDatabase.db.visitDao().insert(HistoryItem().apply { time = 1; title = "A"; url = "https://a.test" })
        }
        val activity = Robolectric.buildActivity(HistoryActivity::class.java).setup().get()
        val list = activity.findViewById<RecyclerView>(R.id.item_list)
        val deadline = System.currentTimeMillis() + 5_000
        while (list.childCount < 3 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
            ShadowLooper.idleMainLooper()
            list.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY))
            list.layout(0, 0, 1920, 1080)
        }
        assertEquals("One date header and two rows", 3, list.childCount)
        val delete = activity.findViewById<View>(R.id.delete_selected_button)
        assertFalse(delete.isShown)

        list.getChildAt(1).performLongClick()
        assertEquals(View.VISIBLE, delete.visibility)

        activity.onBackPressedDispatcher.onBackPressed()
        assertEquals("Back first leaves selection mode", View.GONE, delete.visibility)
        assertFalse(activity.isFinishing)

        activity.onBackPressedDispatcher.onBackPressed()
        assertTrue("A second Back closes the screen", activity.isFinishing)
    }
}
