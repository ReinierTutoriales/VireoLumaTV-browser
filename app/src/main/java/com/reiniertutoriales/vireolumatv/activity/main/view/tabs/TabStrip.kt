package com.reiniertutoriales.vireolumatv.activity.main.view.tabs

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.main.TabStore
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvTabStrip
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import com.reiniertutoriales.vireolumatv.utils.activemodel.ScreenModels
import com.reiniertutoriales.vireolumatv.utils.observable.LiveList

/**
 * Browser model bridge. The tab rail and its remote-control focus are Compose-native;
 * this host only observes the existing TabStore and forwards commands.
 */
class TabStrip @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {
    private val tabs = mutableStateListOf<BrowserTab>()
    private val selected = mutableIntStateOf(0)
    private val tabStore: TabStore? =
        if (isInEditMode) null else ScreenModels.get(TabStore::class, context)

    var current: Int
        get() = selected.intValue
        set(value) { selected.intValue = value }

    var onCommand: ((TabCommand) -> Unit)? = null

    init {
        if (!isInEditMode) {
            addView(ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    TvTabStrip(
                        tabs = tabs,
                        selected = selected.intValue,
                        onActivate = { index -> onCommand?.invoke(TabCommand.Show(index)) },
                        onClose = { tab -> onCommand?.invoke(TabCommand.Close(tab)) },
                        onOptions = ::showTabOptions,
                        onAdd = { onCommand?.invoke(TabCommand.Add) },
                    )
                }
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        tabStore?.openTabs?.observe(listObserver)
        tabStore?.currentTab?.observe(currentObserver)
        refreshTabs()
    }

    override fun onDetachedFromWindow() {
        tabStore?.openTabs?.forget(listObserver)
        tabStore?.currentTab?.forget(currentObserver)
        super.onDetachedFromWindow()
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (gainFocus) getChildAt(0)?.requestFocus()
    }

    fun onTabTitle(tab: BrowserTab) = refreshTabs()
    fun onFavIconUpdated(tab: BrowserTab) = refreshTabs()

    private fun refreshTabs() {
        val store = tabStore ?: return
        tabs.clear()
        tabs.addAll(store.openTabs.toList())
        val index = store.openTabs.indexOf(store.currentTab.value)
        if (index >= 0) selected.intValue = index
    }

    fun showTabOptions(tab: BrowserTab) {
        val store = tabStore ?: return
        val actions = TabAction.entries
        AlertDialog.Builder(context)
            .setTitle(R.string.tabs_title)
            .setItems(actions.map { context.getString(it.label) }.toTypedArray()) { _, option ->
                val index = store.openTabs.indexOf(tab)
                if (index < 0) return@setItems
                when (actions[option]) {
                    TabAction.NEW_NEXT -> onCommand?.invoke(TabCommand.AddAt(index + 1))
                    TabAction.CLOSE -> onCommand?.invoke(TabCommand.Close(tab))
                    TabAction.CLOSE_ALL -> {
                        store.onCloseOthers()
                        onCommand?.invoke(TabCommand.AddAt(0))
                    }
                    TabAction.MOVE_LEFT -> move(index, towardsStart = layoutDirection != LAYOUT_DIRECTION_RTL)
                    TabAction.MOVE_RIGHT -> move(index, towardsStart = layoutDirection == LAYOUT_DIRECTION_RTL)
                    TabAction.FAVORITE -> onCommand?.invoke(TabCommand.Favorite(tab))
                }
            }.show()
    }

    private fun move(index: Int, towardsStart: Boolean) {
        val store = tabStore ?: return
        val target = if (towardsStart) index - 1 else index + 1
        if (target in store.openTabs.indices) store.openTabs.swap(index, target)
    }

    private val listObserver: (LiveList<BrowserTab>) -> Unit = { refreshTabs() }
    private val currentObserver: (BrowserTab?) -> Unit = { tab ->
        val store = tabStore
        if (store != null) {
            val index = store.openTabs.indexOf(tab)
            if (index >= 0) selected.intValue = index
        }
    }

    internal enum class TabAction(@StringRes val label: Int) {
        NEW_NEXT(R.string.tab_action_new),
        CLOSE(R.string.tab_action_close),
        CLOSE_ALL(R.string.tab_action_close_all),
        MOVE_LEFT(R.string.tab_action_move_left),
        MOVE_RIGHT(R.string.tab_action_move_right),
        FAVORITE(R.string.tab_action_favorite),
    }
}
