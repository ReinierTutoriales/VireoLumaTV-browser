package com.reiniertutoriales.vireolumatv.activity.main.view.tabs

import android.app.Application
import android.widget.FrameLayout
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.main.TabStore
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import com.reiniertutoriales.vireolumatv.utils.activemodel.ScreenModels
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config as RobolectricConfig

@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(application = Application::class, sdk = [28])
class TabRowsTest {
    @Test fun remoteReachesTheLargeCloseTargetAndLongPressStillOpensOptions() = withAdapter { _, model, parent ->
        val tab = BrowserTab(url = Config.HOME_PAGE_URL)
        model.openTabs.add(tab)
        val options = mutableListOf<BrowserTab>()
        val closed = mutableListOf<BrowserTab?>()
        val adapter = TabChips { options += it }.apply { tabStore = model }
        adapter.onCommand = { command ->
            if (command is TabCommand.Show) fail("Close must not activate the title")
            if (command is TabCommand.Close) closed += command.tab
        }
        val owner = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup()
        val list = androidx.recyclerview.widget.RecyclerView(parent.context).apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context, androidx.recyclerview.widget.RecyclerView.HORIZONTAL, false)
            this.adapter = adapter
        }
        try {
            owner.get().setContentView(list)
            list.measure(android.view.View.MeasureSpec.makeMeasureSpec(800, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(100, android.view.View.MeasureSpec.EXACTLY))
            list.layout(0, 0, 800, 100)
            val row = list.getChildAt(0)
            val close = row.findViewById<android.view.View>(R.id.close_tab_button)
            val target = parent.resources.getDimensionPixelSize(R.dimen.control_size)
            assertTrue(close.width >= target && close.height >= target)
            // Inside that target the X is drawn at 18 dp and its focus disc at 32 dp, not the chip's height.
            val dp = parent.resources.displayMetrics.density
            assertEquals((18 * dp).toInt(), close.width - close.paddingLeft - close.paddingRight)
            val disc = android.graphics.Rect().also { close.background.getPadding(it) }
            assertEquals((32 * dp).toInt(), close.width - disc.left - disc.right)
            fun key(view: android.view.View, code: Int) {
                view.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code))
                view.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, code))
            }
            assertTrue(row.requestFocus())
            key(row, android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
            assertTrue(close.hasFocus())
            key(row, android.view.KeyEvent.KEYCODE_DPAD_LEFT)
            assertTrue(row.isFocused)
            assertTrue(row.performLongClick())
            key(row, android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
            assertTrue(close.performLongClick())
            assertEquals(listOf(tab, tab), options)
            key(row, android.view.KeyEvent.KEYCODE_DPAD_CENTER)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(listOf(tab), closed)
            val x = close.left + close.width / 2f
            val y = close.top + close.height / 2f
            val now = android.os.SystemClock.uptimeMillis()
            for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
                val event = android.view.MotionEvent.obtain(now, now, action, x, y, 0)
                try { assertTrue(row.dispatchTouchEvent(event)) } finally { event.recycle() }
            }
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(listOf(tab, tab), closed)
        } finally { owner.pause().stop().destroy() }
    }

    @Test fun closeButtonActsOnItsBoundTabAndIgnoresRecycledRows() = withAdapter { adapter, model, parent ->
        val tab = BrowserTab(url = Config.HOME_PAGE_URL)
        model.openTabs.add(tab)
        adapter.onTabsChanged()
        val closed = mutableListOf<BrowserTab?>()
        adapter.onCommand = { if (it is TabCommand.Close) closed += it.tab }
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.bindViewHolder(holder, 0)
        val close = holder.itemView.findViewById<android.view.View>(R.id.close_tab_button)
        assertTrue(close.isFocusable)
        close.performClick()
        assertEquals(listOf(tab), closed)
        adapter.onViewRecycled(holder)
        close.performClick()
        assertEquals(1, closed.size)
    }

    @Test fun recycledSelectedRowResetsSelection() = withAdapter { adapter, model, parent ->
        val first = BrowserTab(url = Config.HOME_PAGE_URL, position = 0)
        val second = BrowserTab(url = Config.HOME_PAGE_URL, position = 1)
        model.openTabs.add(first)
        model.openTabs.add(second)
        adapter.onTabsChanged()
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.bindViewHolder(holder, 0)
        assertTrue(holder.itemView.isSelected)
        adapter.bindViewHolder(holder, 1)
        assertFalse("A row rebound to another tab must not stay selected", holder.itemView.isSelected)
        adapter.bindViewHolder(holder, 0)
        adapter.onViewRecycled(holder)
        assertNull(holder.tab)
        assertFalse("A recycled row must not keep the selection", holder.itemView.isSelected)
    }

    @Test fun changingCurrentMovesTheSelection() = withAdapter { adapter, model, parent ->
        model.openTabs.add(BrowserTab(url = Config.HOME_PAGE_URL))
        model.openTabs.add(BrowserTab(url = Config.HOME_PAGE_URL))
        adapter.onTabsChanged()
        adapter.current = 1
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.bindViewHolder(holder, 0)
        assertFalse(holder.itemView.isSelected)
        adapter.bindViewHolder(holder, 1)
        assertTrue(holder.itemView.isSelected)
    }

    @Test fun closingTheLastTabKeepsAValidSelection() = withAdapter { adapter, model, _ ->
        model.openTabs.add(BrowserTab(url = Config.HOME_PAGE_URL))
        model.openTabs.add(BrowserTab(url = Config.HOME_PAGE_URL))
        adapter.onTabsChanged()
        adapter.current = 1
        model.openTabs.removeAt(1)
        adapter.onTabsChanged()
        assertEquals(0, adapter.current)
    }

    @Test fun movingTabsPreservesFocusedIdentityAndUnsavedRowsAreDistinct() = withAdapter { adapter, model, _ ->
        val first = BrowserTab(url = Config.HOME_PAGE_URL)
        val second = BrowserTab(url = Config.HOME_PAGE_URL)
        model.openTabs.add(first)
        model.openTabs.add(second)
        adapter.onTabsChanged()
        adapter.current = 0
        model.openTabs.swap(0, 1)
        adapter.onTabsChanged()
        assertEquals(1, adapter.current)
        assertFalse(TabChips.Diff(listOf(first), listOf(second)).areItemsTheSame(0, 0))
        assertTrue(TabChips.Diff(listOf(first), listOf(first)).areItemsTheSame(0, 0))
    }

    private fun withAdapter(test: (TabChips, TabStore, FrameLayout) -> Unit) {
        val app = RuntimeEnvironment.getApplication()
        app.setTheme(R.style.AppTheme)
        AppContext.init(app, Config(app.getSharedPreferences("tab-rows-test", 0)))
        val model = ScreenModels.get(TabStore::class, app)
        model.workScope.cancel() // This view test does not persist reordering.
        try {
            val adapter = TabChips {}.apply { tabStore = model }
            test(adapter, model, FrameLayout(app))
        } finally { ScreenModels.releaseModelsOf(app) }
    }
}
