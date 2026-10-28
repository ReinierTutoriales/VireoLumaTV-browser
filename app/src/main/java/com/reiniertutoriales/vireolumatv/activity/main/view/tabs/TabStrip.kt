package com.reiniertutoriales.vireolumatv.activity.main.view.tabs

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.RelativeLayout
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.main.BrowserSettings
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
    private lateinit var tabsModel: TabStore
    private lateinit var settingsModel: BrowserSettings

    var current: Int by adapter::current
    var listener: TabChips.Listener? by adapter::listener

    init {
        if (!isInEditMode) {
            tabsModel = ScreenModels.get(TabStore::class, context)
            settingsModel = ScreenModels.get(BrowserSettings::class, context)
            adapter.tabsModel = tabsModel
            vb.tabList.layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            vb.addButton.setOnClickListener { listener?.onAddNewTabSelected() }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isInEditMode) return
        tabsModel.tabsStates.subscribe(listObserver)
        tabsModel.currentTab.subscribe(currentObserver)
        vb.tabList.adapter = adapter
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (isInEditMode) return
        tabsModel.tabsStates.unsubscribe(listObserver)
        tabsModel.currentTab.unsubscribe(currentObserver)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (gainFocus) focusCurrentTab()
    }

    fun onTabTitleUpdated(tab: BrowserTab) = adapter.refresh(tab)

    fun onFavIconUpdated(tab: BrowserTab) = adapter.refresh(tab)

    /** Options for [tab]; each acts on that tab, wherever it has moved meanwhile. */
    fun showTabOptions(tab: BrowserTab) {
        AlertDialog.Builder(context)
            .setTitle(R.string.tabs_title)
            .setItems(R.array.tabs_actions) { _, option ->
                val tabs = tabsModel.tabsStates
                val index = tabs.indexOf(tab)
                if (index < 0) return@setItems
                when (option) {
                    OPEN_NEW -> listener?.openInNewTab(settingsModel.homePage, index + 1)
                    CLOSE -> listener?.closeTab(tab)
                    CLOSE_ALL -> {
                        tabsModel.onCloseAllTabs()
                        listener?.openInNewTab(settingsModel.homePage, 0)
                    }
                    MOVE_LEFT -> if (index > 0) tabs.swap(index, index - 1)
                    MOVE_RIGHT -> if (index < tabs.lastIndex) tabs.swap(index, index + 1)
                }
            }
            .show()
    }

    private fun focusCurrentTab(): Boolean {
        val tab = tabsModel.tabsStates.getOrNull(current)
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
        adapter.onTabListChanged()
        revealCurrent()
    }

    private val currentObserver: (BrowserTab?) -> Unit = { tab ->
        val index = tabsModel.tabsStates.indexOf(tab)
        if (index >= 0 && index != current) {
            current = index
            revealCurrent()
        }
    }

    private companion object {
        // Positions in R.array.tabs_actions.
        const val OPEN_NEW = 0
        const val CLOSE = 1
        const val CLOSE_ALL = 2
        const val MOVE_LEFT = 3
        const val MOVE_RIGHT = 4
    }
}
