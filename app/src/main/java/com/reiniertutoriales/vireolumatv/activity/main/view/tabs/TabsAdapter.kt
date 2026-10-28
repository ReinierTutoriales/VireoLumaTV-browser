package com.reiniertutoriales.vireolumatv.activity.main.view.tabs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.main.TabsModel
import com.reiniertutoriales.vireolumatv.model.WebTabState
import com.reiniertutoriales.vireolumatv.utils.ViewFaviconLoader

/**
 * Tab rows of the strip. The selected row is the one at [current]; it is derived on every bind, so a
 * recycled row can never keep a stale selection. Focusing a row selects it (preview, contract N06),
 * activating it opens it and a long press asks [onLongPress] for the tab options.
 */
class TabsAdapter(private val onLongPress: (WebTabState) -> Unit) : RecyclerView.Adapter<TabsAdapter.Row>() {

    interface Listener {
        fun onTitleChanged(index: Int)
        fun onTitleSelected(index: Int)
        fun onAddNewTabSelected()
        fun closeTab(tabState: WebTabState?)
        fun openInNewTab(url: String, tabIndex: Int)
    }

    var listener: Listener? = null
    var tabsModel: TabsModel? = null
        set(value) {
            field = value
            onTabListChanged()
        }

    private val shown = ArrayList<WebTabState>()

    private var selected = 0

    /** Index of the selected tab in the strip; changing it redraws the old and the new row. */
    var current: Int
        get() = selected
        set(value) {
            if (selected == value) return
            val previous = selected
            selected = value
            if (previous in shown.indices) notifyItemChanged(previous)
            if (value in shown.indices) notifyItemChanged(value)
        }

    /** Re-reads the model's tabs; the selected tab keeps its selection when tabs move. */
    fun onTabListChanged() {
        val selectedTab = shown.getOrNull(selected)
        val previous = selected
        val next = tabsModel?.tabsStates?.toList().orEmpty()
        val diff = DiffUtil.calculateDiff(Diff(shown, next))
        shown.clear()
        shown.addAll(next)
        // Only the index changes here; row updates must wait until the diff has been dispatched.
        selected = shown.indexOfFirst { it === selectedTab }.takeIf { it >= 0 }
            ?: previous.coerceIn(0, maxOf(0, shown.lastIndex))
        diff.dispatchUpdatesTo(this)
        if (previous in shown.indices) notifyItemChanged(previous)
        if (selected != previous && selected in shown.indices) notifyItemChanged(selected)
    }

    fun refresh(tab: WebTabState) {
        val index = shown.indexOfFirst { it === tab }
        if (index >= 0) notifyItemChanged(index)
    }

    override fun getItemCount() = shown.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Row(LayoutInflater.from(parent.context).inflate(R.layout.view_horizontal_webtab_item, parent, false))

    override fun onBindViewHolder(holder: Row, position: Int) = holder.bind(shown[position], position == selected)

    override fun onViewRecycled(holder: Row) {
        holder.unbind()
        super.onViewRecycled(holder)
    }

    inner class Row(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.tvTitle)
        private val favicon = ViewFaviconLoader(view, view.findViewById<ImageView>(R.id.ivFavicon))

        /** The tab this row shows, or null once recycled. */
        val tab: WebTabState? get() = itemView.tag as? WebTabState

        fun bind(tab: WebTabState, isCurrent: Boolean) {
            itemView.tag = tab
            itemView.isSelected = isCurrent
            title.text = tab.title
            favicon.bind(tab.url)
            itemView.setOnFocusChangeListener { _, focused ->
                val index = positionOf(tab)
                if (focused && index != RecyclerView.NO_POSITION && index != selected) {
                    current = index
                    listener?.onTitleChanged(index)
                }
            }
            itemView.setOnClickListener {
                positionOf(tab).takeIf { it != RecyclerView.NO_POSITION }?.let { listener?.onTitleSelected(it) }
            }
            itemView.setOnLongClickListener {
                (positionOf(tab) != RecyclerView.NO_POSITION).also { if (it) onLongPress(tab) }
            }
        }

        fun unbind() {
            favicon.clear()
            itemView.tag = null
            itemView.isSelected = false
        }

        /** This row's position, if it still shows [tab]; a stale callback must not act on another tab. */
        private fun positionOf(tab: WebTabState): Int {
            val index = bindingAdapterPosition
            return if (index in shown.indices && shown[index] === tab) index else RecyclerView.NO_POSITION
        }
    }

    /** Rows are the same tab by identity, or by stored id once saved; a row redraws on title or address. */
    internal class Diff(private val old: List<WebTabState>, private val new: List<WebTabState>) : DiffUtil.Callback() {
        override fun getOldListSize() = old.size
        override fun getNewListSize() = new.size
        override fun areItemsTheSame(o: Int, n: Int) = old[o] === new[n] || (old[o].id != 0L && old[o].id == new[n].id)
        override fun areContentsTheSame(o: Int, n: Int) = old[o].title == new[n].title && old[o].url == new[n].url
    }
}
