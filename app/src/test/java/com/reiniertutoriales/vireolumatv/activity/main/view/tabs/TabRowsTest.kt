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
