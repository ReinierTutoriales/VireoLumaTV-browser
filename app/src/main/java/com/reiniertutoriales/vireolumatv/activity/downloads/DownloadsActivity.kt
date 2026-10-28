package com.reiniertutoriales.vireolumatv.activity.downloads

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
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
import com.reiniertutoriales.vireolumatv.utils.ApkInstaller
import com.reiniertutoriales.vireolumatv.utils.activemodel.ScreenModels
import kotlinx.coroutines.launch
import java.io.File
import com.reiniertutoriales.vireolumatv.utils.toast

/**
 * Download list. The normal screen pages through stored downloads and follows running ones; the
 * private screen ([IncognitoDownloadsActivity]) shows only this session's downloads, which are never
 * stored. OK opens a finished file; a long press offers install, folder and delete, or cancel while
 * the download runs.
 */
open class DownloadsActivity : AppCompatActivity(), RunningDownloads.Listener {
    private lateinit var vb: DownloadsScreenBinding
    private lateinit var active: RunningDownloads
    private lateinit var history: DownloadHistoryPages
    private val adapter = DownloadRows(::open, ::showOptions)
    private val privateSession: Boolean get() = this is IncognitoDownloadsActivity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vb = DownloadsScreenBinding.inflate(layoutInflater)
        setContentView(vb.root)
        active = ScreenModels.get(RunningDownloads::class, this)
        history = ScreenModels.get(DownloadHistoryPages::class, this)

        vb.itemList.layoutManager = LinearLayoutManager(this)
        vb.itemList.adapter = adapter
        if (privateSession) return
        vb.itemList.addOnScrollListener(nextPageLoader)
        history.loadedPage.observe(this, false) { show(it) }
        if (history.allItems.isEmpty()) history.loadNextItems() else show(history.allItems)
    }

    override fun onResume() {
        super.onResume()
        active.registerListener(this)
        if (privateSession) active.runningDownloads.forEach { showLive(it.job) }
    }

    override fun onPause() {
        active.unregisterListener(this)
        super.onPause()
    }

    override fun onDownloadUpdated(job: Download) {
        if (privateSession) showLive(job)
        adapter.updateDownload(job)
    }

    override fun onDownloadError(job: Download, responseCode: Int, responseMessage: String) =
        onDownloadUpdated(job)

    override fun onIdle() {}

    private fun show(items: List<Download>) {
        if (items.isEmpty()) return
        vb.emptyText.isVisible = false
        adapter.addItems(items)
        vb.itemList.requestFocus()
    }

    private fun showLive(download: Download) {
        if (!download.incognito) return
        vb.emptyText.isVisible = false
        adapter.addLiveItem(download)
    }

    /** Asks for the next page when the last visible row is one of the last two. */
    private val nextPageLoader = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            val last = (recyclerView.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
            if (adapter.itemCount > 0 && last >= adapter.itemCount - 2) history.loadNextItems()
        }
    }

    private fun isFinished(download: Download) = download.size >= 0 && download.size == download.bytesReceived

    private fun open(download: Download) {
        if (!isFinished(download)) return // Running, cancelled or broken rows already say so.
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            download.filepath.toUri()
        } else {
            val file = File(download.filepath)
            if (!file.exists()) {
                toast(R.string.download_file_missing)
                return
            }
            FileProvider.getUriForFile(this, "$packageName.provider", file)
        }
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, contentResolver.getType(uri))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        start(view, R.string.download_no_app)
    }

    private fun showOptions(anchor: View, download: Download): Boolean {
        val menu = PopupMenu(this, anchor, Gravity.BOTTOM)
        val running = download.size != Download.SIZE_FAILED && download.size != Download.SIZE_CANCELLED &&
            !isFinished(download)
        if (running) {
            menu.menu.add(0, CANCEL, 0, R.string.action_cancel)
        } else {
            if (isFinished(download) && download.filename.endsWith(".apk", ignoreCase = true)) {
                menu.menu.add(0, INSTALL, 0, R.string.download_install)
            }
            menu.menu.add(0, OPEN_FOLDER, 1, R.string.download_open_folder)
            menu.menu.add(0, DELETE, 2, R.string.action_delete)
        }
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                CANCEL -> active.cancelDownload(download)
                INSTALL -> install(download)
                OPEN_FOLDER -> openFolder()
                DELETE -> lifecycleScope.launch {
                    active.deleteItem(download)
                    adapter.remove(download)
                }
            }
            true
        }
        menu.show()
        return true
    }

    private fun install(download: Download) {
        if (ApkInstaller.canInstall(this)) {
            ApkInstaller.launch(this, download)
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.app_name)
            .setMessage(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) R.string.install_allow_this_app
                else R.string.install_allow_unknown_sources)
            .setPositiveButton(android.R.string.ok) { _, _ -> start(ApkInstaller.permissionSettings(this), R.string.error) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** File managers that browse folders first; the system Downloads screen when none does. */
    private fun openFolder() {
        @Suppress("DEPRECATION") // Only its path is used, to name the folder for file managers.
        val folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val browse = Intent(Intent.ACTION_VIEW).setDataAndType(folder.absolutePath.toUri(), "resource/folder")
        try {
            startActivity(browse)
        } catch (_: ActivityNotFoundException) {
            start(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS), R.string.download_no_file_manager)
        }
    }

    private fun start(intent: Intent, missing: Int) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            toast(missing)
        }
    }

    private companion object {
        const val CANCEL = 0
        const val INSTALL = 1
        const val OPEN_FOLDER = 2
        const val DELETE = 3
    }
}
