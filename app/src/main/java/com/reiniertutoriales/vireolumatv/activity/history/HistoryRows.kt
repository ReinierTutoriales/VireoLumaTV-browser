package com.reiniertutoriales.vireolumatv.activity.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.model.HistoryItem
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.reiniertutoriales.vireolumatv.utils.sameLocalDay

/**
 * History rows with a date header before each day. Only the rows that changed are rebound:
 * the list is diffed off the main thread and applied with [submitList].
 *
 * [realCount] counts visible stored entries; the model owns the next-page boundary.
 */
class HistoryRows(
    private val onItemClick: (HistoryItem) -> Unit,
    private val onItemLongClick: (HistoryItem) -> Boolean,
    config: AsyncDifferConfig<HistoryItem> = AsyncDifferConfig.Builder(DIFF_CALLBACK).build(),
) : ListAdapter<HistoryItem, RecyclerView.ViewHolder>(config) {

    private val realItems = ArrayList<HistoryItem>()
    private val dateFormat: DateFormat = DateFormat.getDateInstance()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    val realCount: Long get() = realItems.size.toLong()

    val selectedItems: List<HistoryItem> get() = realItems.filter { it.selected }

    var isMultiselectMode = false
        set(value) {
            if (field == value) return
            field = value
            if (!value) realItems.forEach { it.selected = false }
            notifyItemRangeChanged(0, itemCount)
        }

    fun addItems(items: List<HistoryItem>) {
        if (items.isEmpty()) return
        realItems.addAll(items)
        submit()
    }

    fun remove(item: HistoryItem) {
        realItems.remove(item)
        submit()
    }

    fun remove(items: List<HistoryItem>) {
        realItems.removeAll(items.toSet())
        submit()
    }

    fun erase() {
        realItems.clear()
        submit()
    }

    fun toggleSelection(item: HistoryItem) {
        item.selected = !item.selected
        val position = currentList.indexOf(item)
        if (position >= 0) notifyItemChanged(position)
    }

    private fun submit() = submitList(withDateHeaders(realItems))

    private fun withDateHeaders(items: List<HistoryItem>): List<HistoryItem> {
        val rows = ArrayList<HistoryItem>(items.size + 8)
        var lastHeaderTime = -1L
        for (item in items) {
            if (!sameLocalDay(item.time, lastHeaderTime)) {
                lastHeaderTime = item.time
                rows.add(HistoryItem.createDateHeaderInfo(item.time))
            }
            rows.add(item)
        }
        return rows
    }

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).isDateHeader) VIEW_TYPE_HEADER else VIEW_TYPE_HISTORY_ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        if (viewType == VIEW_TYPE_HEADER) {
            return HeaderHolder(inflater.inflate(R.layout.day_header, parent, false))
        }
        val holder = ItemHolder(inflater.inflate(R.layout.history_row, parent, false))
        holder.itemView.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onItemClick(getItem(position))
        }
        holder.itemView.setOnLongClickListener {
            val position = holder.bindingAdapterPosition
            position != RecyclerView.NO_POSITION && onItemLongClick(getItem(position))
        }
        return holder
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is HeaderHolder -> holder.dayText.text = dateFormat.format(Date(item.time))
            is ItemHolder -> holder.bind(item, isMultiselectMode, timeFormat)
        }
    }

    private class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dayText: TextView = view.findViewById(R.id.day_text)
    }

    private class ItemHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleText: TextView = view.findViewById(R.id.title_text)
        private val addressText: TextView = view.findViewById(R.id.address_text)
        private val timeText: TextView = view.findViewById(R.id.time_text)
        private val selectBox: CheckBox = view.findViewById(R.id.select_box)

        fun bind(item: HistoryItem, multiselect: Boolean, timeFormat: SimpleDateFormat) {
            titleText.text = item.title
            addressText.text = item.url
            timeText.text = timeFormat.format(Date(item.time))
            selectBox.visibility = if (multiselect) View.VISIBLE else View.GONE
            selectBox.isChecked = item.selected
        }
    }

    companion object {
        const val VIEW_TYPE_HISTORY_ITEM = 0
        const val VIEW_TYPE_HEADER = 1

        internal val DIFF_CALLBACK = object : DiffUtil.ItemCallback<HistoryItem>() {
            override fun areItemsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean =
                if (oldItem.isDateHeader || newItem.isDateHeader) {
                    oldItem.isDateHeader && newItem.isDateHeader && sameLocalDay(oldItem.time, newItem.time)
                } else {
                    oldItem.id == newItem.id
                }

            override fun areContentsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean =
                oldItem.title == newItem.title && oldItem.url == newItem.url &&
                    oldItem.time == newItem.time && oldItem.selected == newItem.selected
        }
    }
}
