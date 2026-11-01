package com.reiniertutoriales.vireolumatv.activity.main.view.tabs

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.RelativeLayout
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.main.TabStore
import com.reiniertutoriales.vireolumatv.databinding.TabStripBinding
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import com.reiniertutoriales.vireolumatv.utils.activemodel.ScreenModels
import com.reiniertutoriales.vireolumatv.utils.observable.LiveList

/**
 * The tab strip under the top capsule. The strip itself takes focus from the capsule and hands it to
 * the selected tab, or to "+" when that tab is not on screen, so focus always lands on a control.
 */
class TabStrip @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    RelativeLayout(context, attrs) {

    private val vb = TabStripBinding.inflate(LayoutInflater.from(context), this)
    private val adapter = TabChips(::showTabOptions)
    private lateinit var tabStore: TabStore

    var current: Int by adapter::current
    var onCommand: ((TabCommand) -> Unit)? by adapter::onCommand

    init {
        if (!isInEditMode) {
            tabStore = ScreenModels.get(TabStore::class, context)
            adapter.tabStore = tabStore
            vb.tabList.layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            vb.addButton.setOnClickListener { onCommand?.invoke(TabCommand.Add) }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isInEditMode) return
        tabStore.openTabs.observe(listObserver)
        tabStore.currentTab.observe(currentObserver)
        vb.tabList.adapter = adapter
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (isInEditMode) return
        tabStore.openTabs.forget(listObserver)
        tabStore.currentTab.forget(currentObserver)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (gainFocus) focusCurrentTab()
    }

    fun onTabTitle(tab: BrowserTab) = adapter.refresh(tab)

    fun onFavIconUpdated(tab: BrowserTab) = adapter.refresh(tab)

    /** Options for [tab]; each acts on that tab, wherever it has moved meanwhile. */
    fun showTabOptions(tab: BrowserTab) {
        val actions = TabAction.entries
        AlertDialog.Builder(context)
            .setTitle(R.string.tabs_title)
            .setItems(actions.map { context.getString(it.label) }.toTypedArray()) { _, option ->
                val tabs = tabStore.openTabs
                val index = tabs.indexOf(tab)
                if (index < 0) return@setItems
                when (actions[option]) {
                    TabAction.NEW_NEXT -> onCommand?.invoke(TabCommand.AddAt(index + 1))
                    TabAction.CLOSE -> onCommand?.invoke(TabCommand.Close(tab))
                    TabAction.CLOSE_ALL -> {
                        tabStore.onCloseOthers()
                        onCommand?.invoke(TabCommand.AddAt(0))
                    }
                    TabAction.MOVE_LEFT -> move(index, towardsStart = !isRtl)
                    TabAction.MOVE_RIGHT -> move(index, towardsStart = isRtl)
                    TabAction.FAVORITE -> onCommand?.invoke(TabCommand.Favorite(tab))
                }
            }
            .show()
    }

    /** Right-to-left the strip starts at the right edge, so "left" moves a tab later in the order. */
    private val isRtl: Boolean get() = layoutDirection == LAYOUT_DIRECTION_RTL

    private fun move(index: Int, towardsStart: Boolean) {
        val tabs = tabStore.openTabs
        val target = if (towardsStart) index - 1 else index + 1
        if (target in tabs.indices) tabs.swap(index, target)
    }

    private fun focusCurrentTab(): Boolean {
        val tab = tabStore.openTabs.getOrNull(current)
        for (i in 0 until vb.tabList.childCount) {
            val row = vb.tabList.getChildAt(i)
            if (row.tag === tab && row.requestFocus()) return true
        }
        return vb.addButton.requestFocus()
    }

    private fun revealCurrent() {
        if (current !in 0 until adapter.itemCount) return
        val layout = vb.tabList.layoutManager as LinearLayoutManager
        if (current < layout.findFirstCompletelyVisibleItemPosition() ||
            current > layout.findLastCompletelyVisibleItemPosition()) vb.tabList.scrollToPosition(current)
    }

    private val listObserver: (LiveList<BrowserTab>) -> Unit = {
        adapter.onTabsChanged()
        revealCurrent()
    }

    private val currentObserver: (BrowserTab?) -> Unit = { tab ->
        val index = tabStore.openTabs.indexOf(tab)
        if (index >= 0 && index != current) {
            current = index
            revealCurrent()
        }
    }

    /** The tab options dialog, in the order shown. */
    internal enum class TabAction(@StringRes val label: Int) {
        NEW_NEXT(R.string.tab_action_new),
        CLOSE(R.string.tab_action_close),
        CLOSE_ALL(R.string.tab_action_close_all),
        MOVE_LEFT(R.string.tab_action_move_left),
        MOVE_RIGHT(R.string.tab_action_move_right),
        FAVORITE(R.string.tab_action_favorite),
    }
}
