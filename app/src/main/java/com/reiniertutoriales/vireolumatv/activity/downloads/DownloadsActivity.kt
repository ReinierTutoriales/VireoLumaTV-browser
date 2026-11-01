package com.reiniertutoriales.vireolumatv.activity.downloads

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.databinding.DownloadsScreenBinding
import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.service.downloads.DownloadFiles
import com.reiniertutoriales.vireolumatv.utils.ApkInstaller
import com.reiniertutoriales.vireolumatv.utils.activemodel.ScreenModels
import com.reiniertutoriales.vireolumatv.utils.toast
import kotlinx.coroutines.launch
import java.io.File

/**
 * Download list. The normal screen pages through stored downloads and follows running ones; the
 * private screen ([IncognitoDownloadsActivity]) shows only this session's downloads, which are never
 * stored. OK opens a saved file; a long press offers the [DownloadAction]s that fit the download.
 */
open class DownloadsActivity : AppCompatActivity() {
    private lateinit var ui: DownloadsScreenBinding
    private lateinit var board: TransferBoard
    private lateinit var stored: DownloadHistoryPages
    private val rows = DownloadRows(::open, ::offerActions)
    private val privateSession: Boolean get() = this is IncognitoDownloadsActivity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = DownloadsScreenBinding.inflate(layoutInflater)
        setContentView(ui.root)
        board = ScreenModels.get(TransferBoard::class, this)
        stored = ScreenModels.get(DownloadHistoryPages::class, this)
        ui.itemList.layoutManager = LinearLayoutManager(this)
        ui.itemList.adapter = rows
        // Followed only while the screen is in front, like the list it redraws.
        board.changed.observe(this, false) { download -> download?.let(::redraw) }
        if (privateSession) return
        ui.itemList.addOnScrollListener(NearEnd { stored.loadNextItems() })
        stored.loadedPage.observe(this, false) { append(it) }
        if (stored.allItems.isEmpty()) stored.loadNextItems() else append(stored.allItems)
    }

    override fun onResume() {
        super.onResume()
        // Private downloads exist only in memory: the running tasks are the whole list.
        if (privateSession) board.transfers.forEach { redraw(it.job) }
    }

    private fun redraw(download: Download) {
        if (privateSession) {
            if (!download.incognito) return
            ui.emptyText.isVisible = false
            rows.addLiveItem(download)
        }
        rows.updateDownload(download)
    }

    private fun append(page: List<Download>) {
        if (page.isEmpty()) return
        ui.emptyText.isVisible = false
        rows.addItems(page)
        ui.itemList.requestFocus()
    }

    private fun open(download: Download) {
        if (DownloadState.of(download) != DownloadState.SAVED) return // The row already says why.
        val target = savedUri(download) ?: return toast(R.string.download_file_missing)
        launch(Intent(Intent.ACTION_VIEW)
            .setDataAndType(target, contentResolver.getType(target))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), R.string.download_no_app)
    }

    /** The MediaStore entry from Android 11; before it, the file shared through this app's provider. */
    private fun savedUri(download: Download): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return download.filepath.toUri()
        val file = File(download.filepath).takeIf { it.exists() } ?: return null
        return FileProvider.getUriForFile(this, "$packageName.provider", file)
    }

    private fun offerActions(anchor: View, download: Download): Boolean {
        val popup = PopupMenu(this, anchor, Gravity.BOTTOM)
        DownloadAction.available(download).forEach { popup.menu.add(0, it.ordinal, it.ordinal, it.label) }
        popup.setOnMenuItemClickListener { item ->
            when (DownloadAction.entries[item.itemId]) {
                DownloadAction.CANCEL -> board.cancel(download)
                DownloadAction.INSTALL -> install(download)
                DownloadAction.SHOW_FOLDER -> showFolder()
                DownloadAction.DELETE -> lifecycleScope.launch {
                    DownloadFiles.erase(download)
                    rows.remove(download)
                }
            }
            true
        }
        popup.show()
        return true
    }

    private fun install(download: Download) {
        if (ApkInstaller.canInstall(this)) return ApkInstaller.launch(this, download)
        val explanation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) R.string.install_allow_this_app
            else R.string.install_allow_unknown_sources
        AlertDialog.Builder(this)
            .setTitle(R.string.app_name)
            .setMessage(explanation)
            .setPositiveButton(android.R.string.ok) { _, _ -> launch(ApkInstaller.permissionSettings(this), R.string.error) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** A file manager that opens folders; the system Downloads screen when there is none. */
    private fun showFolder() {
        @Suppress("DEPRECATION") // Only its path is used, to name the folder for file managers.
        val folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        try {
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(folder.absolutePath.toUri(), "resource/folder"))
        } catch (_: ActivityNotFoundException) {
            launch(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS), R.string.download_no_file_manager)
        }
    }

    private fun launch(intent: Intent, whenMissing: Int) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            toast(whenMissing)
        }
    }

    /** Calls [more] when one of the last two rows comes into view. */
    private class NearEnd(private val more: () -> Unit) : RecyclerView.OnScrollListener() {
        override fun onScrolled(list: RecyclerView, dx: Int, dy: Int) {
            val count = list.adapter?.itemCount ?: 0
            val lastShown = (list.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
            if (count > 0 && lastShown >= count - 2) more()
        }
    }
}
