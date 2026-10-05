package com.reiniertutoriales.vireolumatv.activity.main.view.tabs

import android.app.Application
import android.widget.FrameLayout
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.main.TabsModel
import com.reiniertutoriales.vireolumatv.model.WebTabState
import com.reiniertutoriales.vireolumatv.utils.activemodel.ActiveModelsRepository
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
        val first = WebTabState(url = Config.HOME_PAGE_URL, position = 0)
        val second = WebTabState(url = Config.HOME_PAGE_URL, position = 1)
        model.tabsStates.add(first)
        model.tabsStates.add(second)
        adapter.onTabListChanged()
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.bindViewHolder(holder, 0)
        assertTrue(holder.vb.root.isChecked)
        adapter.bindViewHolder(holder, 1)
        assertFalse(holder.vb.root.isChecked)
        adapter.onViewRecycled(holder)
        assertNull(holder.vb.root.tag)
        assertNull(adapter.checkedView)
    }

    @Test fun movingTabsPreservesFocusedIdentityAndUnsavedRowsAreDistinct() = withAdapter { adapter, model, _ ->
        val first = WebTabState(url = Config.HOME_PAGE_URL)
        val second = WebTabState(url = Config.HOME_PAGE_URL)
        model.tabsStates.add(first)
        model.tabsStates.add(second)
        adapter.onTabListChanged()
        adapter.current = 0
        model.tabsStates.swap(0, 1)
        adapter.onTabListChanged()
        assertEquals(1, adapter.current)
        assertFalse(TabsDiffUtillCallback(listOf(first), listOf(second)).areItemsTheSame(0, 0))
        assertTrue(TabsDiffUtillCallback(listOf(first), listOf(first)).areItemsTheSame(0, 0))
    }

    private fun withAdapter(test: (TabsAdapter, TabsModel, FrameLayout) -> Unit) {
        val app = RuntimeEnvironment.getApplication()
        app.setTheme(R.style.AppTheme)
        AppContext.init(app, Config(app.getSharedPreferences("tab-rows-test", 0)))
        val model = ActiveModelsRepository.get(TabsModel::class, app)
        model.modelScope.cancel() // This view test does not persist reordering.
        try {
            val view = TabsView(app)
            val adapter = TabsAdapter(view).apply { tabsModel = model }
            test(adapter, model, FrameLayout(app))
        } finally { ActiveModelsRepository.markAsNeedlessAllModelsUsedBy(app) }
    }
}
