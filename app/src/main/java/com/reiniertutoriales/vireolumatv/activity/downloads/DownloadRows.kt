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
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.reiniertutoriales.vireolumatv.utils.sameLocalDay

/**
 * Download rows with a date header before each day. A progress update changes one row in place
 * and rebinds only that row; the list is diffed off the main thread for insertions and removals.
 *
 * [rowCount] counts downloads on screen, which is the offset for the next page.
 */
class DownloadRows(
    private val onItemClick: (Download) -> Unit,
    private val onItemLongClick: (View, Download) -> Boolean,
    config: AsyncDifferConfig<Download> = AsyncDifferConfig.Builder(DIFF_CALLBACK).build(),
) : ListAdapter<Download, RecyclerView.ViewHolder>(config) {

    private val realItems = ArrayList<Download>()
    private val dateFormat: DateFormat = DateFormat.getDateInstance()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    val rowCount: Long get() = realItems.size.toLong()

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
            if (!sameLocalDay(download.time, lastHeaderTime)) {
                lastHeaderTime = download.time
                rows.add(Download.dayHeader(download.time))
            }
            rows.add(download)
        }
        return rows
    }

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).isDayHeader) ROW_DAY else ROW_DOWNLOAD

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        if (viewType == ROW_DAY) {
            return HeaderHolder(inflater.inflate(R.layout.day_header, parent, false))
        }
        val holder = ItemHolder(inflater.inflate(R.layout.download_row, parent, false))
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
            is HeaderHolder -> holder.dayText.text = dateFormat.format(Date(download.time))
            is ItemHolder -> holder.bind(download, timeFormat)
        }
    }

    private class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dayText: TextView = view.findViewById(R.id.day_text)
    }

    private class ItemHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleText: TextView = view.findViewById(R.id.title_text)
        private val addressText: TextView = view.findViewById(R.id.address_text)
        private val timeText: TextView = view.findViewById(R.id.time_text)
        private val sizeText: TextView = view.findViewById(R.id.size_text)
        private val progressBar: ProgressBar = view.findViewById(R.id.load_progress)
        private val sizeColor = sizeText.currentTextColor

        fun bind(download: Download, timeFormat: DateFormat) {
            normalizeSize(download)
            titleText.text = download.filename
            addressText.text = download.url
            timeText.text = timeFormat.format(Date(download.time))
            showProgress(download)
        }

        private fun showProgress(download: Download) {
            val context = itemView.context
            fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
            sizeText.setTextColor(sizeColor)
            progressBar.isVisible = false
            when {
                download.size == Download.SIZE_CANCELLED -> sizeText.setText(R.string.download_cancelled)
                download.size == Download.SIZE_FAILED -> {
                    sizeText.setText(R.string.download_failed)
                    sizeText.setTextColor(ContextCompat.getColor(context, R.color.error))
                }
                download.size <= 0L -> {
                    sizeText.text = size(download.bytesReceived)
                    progressBar.isIndeterminate = true
                    progressBar.isVisible = true
                }
                download.size == download.bytesReceived -> sizeText.text = size(download.size)
                else -> {
                    sizeText.text = context.getString(R.string.download_progress_size, size(download.bytesReceived), size(download.size))
                    progressBar.isIndeterminate = false
                    progressBar.progress = (download.bytesReceived * 100 / download.size).toInt()
                    progressBar.isVisible = true
                }
            }
        }
    }

    companion object {
        const val ROW_DOWNLOAD = 0
        const val ROW_DAY = 1

        /** A wrong size from the server is ignored for future updates, as before. */
        private fun normalizeSize(download: Download) {
            if (download.size > 0 && download.bytesReceived > download.size) download.size = 0L
        }

        internal val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Download>() {
            override fun areItemsTheSame(oldItem: Download, newItem: Download): Boolean =
                if (oldItem.isDayHeader || newItem.isDayHeader) {
                    oldItem.isDayHeader && newItem.isDayHeader && sameLocalDay(oldItem.time, newItem.time)
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
