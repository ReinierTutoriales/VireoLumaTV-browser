package com.reiniertutoriales.vireolumatv.activity.main.view.tabs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.main.TabsModel
import com.reiniertutoriales.vireolumatv.activity.main.view.tabs.TabsAdapter.TabViewHolder
import com.reiniertutoriales.vireolumatv.databinding.ViewHorizontalWebtabItemBinding
import com.reiniertutoriales.vireolumatv.model.WebTabState
import com.reiniertutoriales.vireolumatv.utils.ViewFaviconLoader
import com.reiniertutoriales.vireolumatv.widgets.CheckableContainer


class TabsAdapter(private val tabsView: TabsView) : RecyclerView.Adapter<TabViewHolder>() {
    var tabsModel: TabsModel? = null
        set(value) {
            field = value
            onTabListChanged()
        }
    private val tabsCopy =
        ArrayList<WebTabState>().apply { addAll(tabsModel?.tabsStates ?: emptyList()) }
    var current: Int = 0
    var listener: Listener? = null
    var checkedView: CheckableContainer? = null

    interface Listener {
        fun onTitleChanged(index: Int)
        fun onTitleSelected(index: Int)
        fun onAddNewTabSelected()
        fun closeTab(tabState: WebTabState?)
        fun openInNewTab(url: String, tabIndex: Int)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_horizontal_webtab_item, parent, false)
        return TabViewHolder(view)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        holder.bind(tabsCopy[position])
    }

    override fun onViewRecycled(holder: TabViewHolder) {
        if (checkedView === holder.vb.root) checkedView = null
        holder.clear()
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int {
        return tabsCopy.size
    }

    fun onTabListChanged() {
        val focusedTab = tabsCopy.getOrNull(current)
        val previous = current
        val tabsDiffUtilCallback =
            TabsDiffUtillCallback(tabsCopy, tabsModel?.tabsStates ?: emptyList())
        val tabsDiffResult = DiffUtil.calculateDiff(tabsDiffUtilCallback)
        tabsCopy.apply { clear() }.addAll(tabsModel?.tabsStates ?: emptyList())
        current = tabsCopy.indexOfFirst { it === focusedTab }.takeIf { it >= 0 }
            ?: previous.coerceIn(0, maxOf(0, tabsCopy.lastIndex))
        tabsDiffResult.dispatchUpdatesTo(this)
        if (previous in tabsCopy.indices) notifyItemChanged(previous)
        if (current != previous && current in tabsCopy.indices) notifyItemChanged(current)
    }

    inner class TabViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val vb = ViewHorizontalWebtabItemBinding.bind(itemView)

        private val faviconLoader = ViewFaviconLoader(itemView, vb.ivFavicon)

        fun clear() {
            faviconLoader.clear()
            vb.root.tag = null
            vb.root.isChecked = false
        }

        fun bind(tabState: WebTabState) {
            vb.root.tag = tabState
            vb.tvTitle.text = tabState.title
            // A recycled selected row must also explicitly reset its unselected state.
            if (checkedView === vb.root) checkedView = null
            vb.root.isChecked = current == tabState.position
            if (vb.root.isChecked) {
                checkedView?.isChecked = false
                checkedView = vb.root
            }
            faviconLoader.bind(tabState.url)

            vb.root.setOnFocusChangeListener { _, hasFocus ->
                val index = currentPosition(tabState)
                if (hasFocus && index != RecyclerView.NO_POSITION && current != index) {
                    current = index
                    checkedView?.isChecked = false
                    vb.root.isChecked = true
                    checkedView = vb.root
                    listener?.onTitleChanged(index)
                }
            }
            vb.root.setOnClickListener {
                val index = currentPosition(tabState)
                if (index != RecyclerView.NO_POSITION) listener?.onTitleSelected(index)
            }
            vb.root.setOnLongClickListener {
                if (currentPosition(tabState) == RecyclerView.NO_POSITION) false
                else { tabsView.showTabOptions(tabState); true }
            }
        }

        private fun currentPosition(tab: WebTabState): Int {
            val index = bindingAdapterPosition
            return if (index in tabsCopy.indices && tabsCopy[index] === tab) index else RecyclerView.NO_POSITION
        }
    }
}
