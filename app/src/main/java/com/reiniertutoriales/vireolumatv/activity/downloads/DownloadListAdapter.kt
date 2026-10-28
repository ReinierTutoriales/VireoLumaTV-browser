package com.reiniertutoriales.vireolumatv.activity.downloads

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.utils.Utils
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Download rows with a date header before each day. A progress update changes one row in place
 * and rebinds only that row; the list is diffed off the main thread for insertions and removals.
 *
 * [realCount] counts downloads on screen, which is the offset for the next page.
 */
class DownloadListAdapter(
    private val onItemClick: (Download) -> Unit,
    private val onItemLongClick: (View, Download) -> Boolean,
    config: AsyncDifferConfig<Download> = AsyncDifferConfig.Builder(DIFF_CALLBACK).build(),
) : ListAdapter<Download, RecyclerView.ViewHolder>(config) {

    private val realItems = ArrayList<Download>()
    private val dateFormat: DateFormat = DateFormat.getDateInstance()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    val realCount: Long get() = realItems.size.toLong()

    fun addItems(items: List<Download>) {
        if (items.isEmpty()) return
        realItems.addAll(items)
        submit()
    }

    fun addLiveItem(download: Download) {
        if (realItems.none { it.matches(download) }) addItems(listOf(download))
    }

    fun remove(download: Download) {
        realItems.remove(download)
        submit()
    }

    /** Copies a progress or error update into the row that shows the same download. */
    fun updateDownload(info: Download) {
        val row = realItems.firstOrNull { it.matches(info) } ?: return
        row.filename = info.filename
        row.filepath = info.filepath
        row.size = info.size
        row.bytesReceived = info.bytesReceived
        normalizeSize(row)
        val position = currentList.indexOf(row)
        if (position >= 0) notifyItemChanged(position)
    }

    private fun submit() = submitList(withDateHeaders(realItems))

    private fun withDateHeaders(items: List<Download>): List<Download> {
        val rows = ArrayList<Download>(items.size + 8)
        var lastHeaderTime = -1L
        for (download in items) {
            if (!Utils.isSameDate(download.time, lastHeaderTime)) {
                lastHeaderTime = download.time
                rows.add(Download.createDateHeaderInfo(download.time))
            }
            rows.add(download)
        }
        return rows
    }

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).isDateHeader) VIEW_TYPE_HEADER else VIEW_TYPE_DOWNLOAD_ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        if (viewType == VIEW_TYPE_HEADER) {
            return HeaderHolder(inflater.inflate(R.layout.view_list_date_header, parent, false))
        }
        val holder = ItemHolder(inflater.inflate(R.layout.view_download_item, parent, false))
        holder.itemView.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onItemClick(getItem(position))
        }
        holder.itemView.setOnLongClickListener {
            val position = holder.bindingAdapterPosition
            position != RecyclerView.NO_POSITION && onItemLongClick(holder.itemView, getItem(position))
        }
        return holder
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val download = getItem(position)
        when (holder) {
            is HeaderHolder -> holder.tvDate.text = dateFormat.format(Date(download.time))
            is ItemHolder -> holder.bind(download, timeFormat)
        }
    }

    private class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tvDate)
    }

    private class ItemHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        private val tvUrl: TextView = view.findViewById(R.id.tvURL)
        private val tvTime: TextView = view.findViewById(R.id.tvTime)
        private val tvSize: TextView = view.findViewById(R.id.tvSize)
        private val progressBar: ProgressBar = view.findViewById(R.id.progressBar)
        private val sizeColor = tvSize.currentTextColor

        fun bind(download: Download, timeFormat: DateFormat) {
            normalizeSize(download)
            tvTitle.text = download.filename
            tvUrl.text = download.url
            tvTime.text = timeFormat.format(Date(download.time))
            showProgress(download)
        }

        private fun showProgress(download: Download) {
            val context = itemView.context
            fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
            tvSize.setTextColor(sizeColor)
            progressBar.isVisible = false
            when {
                download.size == Download.CANCELLED_MARK -> tvSize.setText(R.string.cancelled)
                download.size == Download.BROKEN_MARK -> {
                    tvSize.setText(R.string.error)
                    tvSize.setTextColor(ContextCompat.getColor(context, R.color.ui_error))
                }
                download.size <= 0L -> {
                    tvSize.text = size(download.bytesReceived)
                    progressBar.isIndeterminate = true
                    progressBar.isVisible = true
                }
                download.size == download.bytesReceived -> tvSize.text = size(download.size)
                else -> {
                    tvSize.text = context.getString(R.string.size_progress, size(download.bytesReceived), size(download.size))
                    progressBar.isIndeterminate = false
                    progressBar.progress = (download.bytesReceived * 100 / download.size).toInt()
                    progressBar.isVisible = true
                }
            }
        }
    }

    companion object {
        const val VIEW_TYPE_DOWNLOAD_ITEM = 0
        const val VIEW_TYPE_HEADER = 1

        /** A wrong size from the server is ignored for future updates, as before. */
        private fun normalizeSize(download: Download) {
            if (download.size > 0 && download.bytesReceived > download.size) download.size = 0L
        }

        internal val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Download>() {
            override fun areItemsTheSame(oldItem: Download, newItem: Download): Boolean =
                if (oldItem.isDateHeader || newItem.isDateHeader) {
                    oldItem.isDateHeader && newItem.isDateHeader && Utils.isSameDate(oldItem.time, newItem.time)
                } else {
                    oldItem.matches(newItem)
                }

            override fun areContentsTheSame(oldItem: Download, newItem: Download): Boolean =
                oldItem.filename == newItem.filename && oldItem.filepath == newItem.filepath &&
                    oldItem.url == newItem.url && oldItem.time == newItem.time &&
                    oldItem.size == newItem.size && oldItem.bytesReceived == newItem.bytesReceived
        }
    }
}
