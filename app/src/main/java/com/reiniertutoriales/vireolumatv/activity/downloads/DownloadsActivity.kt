package com.reiniertutoriales.vireolumatv.activity.downloads

import android.app.AlertDialog
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.reiniertutoriales.vireolumatv.BuildConfig
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.databinding.ActivityDownloadsBinding
import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.utils.Utils
import com.reiniertutoriales.vireolumatv.utils.activemodel.ActiveModelsRepository
import com.reiniertutoriales.vireolumatv.utils.ApkInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.*
import androidx.core.net.toUri

open class DownloadsActivity : AppCompatActivity(), ActiveDownloadsModel.Listener {
    private lateinit var vb: ActivityDownloadsBinding
    private val adapter = DownloadListAdapter(::onItemClick, ::onItemLongClick)
    private val privateSession: Boolean get() = this is IncognitoDownloadsActivity

    private lateinit var activeDownloadsModel: ActiveDownloadsModel
    private lateinit var downloadsHistoryModel: DownloadsHistoryModel

    // Loads the next page when the last visible row is within one row of the end of the list.
    private val onListScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            val lastVisible = (recyclerView.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
            if (!privateSession && adapter.itemCount != 0 && lastVisible >= adapter.itemCount - 2) {
                downloadsHistoryModel.loadNextItems()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate this:" + System.identityHashCode(this))
        vb = ActivityDownloadsBinding.inflate(layoutInflater)
        setContentView(vb.root)

        activeDownloadsModel = ActiveModelsRepository.get(ActiveDownloadsModel::class, this)
        downloadsHistoryModel = ActiveModelsRepository.get(DownloadsHistoryModel::class, this)

        vb.listView.layoutManager = LinearLayoutManager(this)
        vb.listView.adapter = adapter
        vb.listView.addOnScrollListener(onListScrollListener)

        downloadsHistoryModel.lastLoadedItems.subscribe(this, false, {
            if (it.isNotEmpty()) {
                vb.tvPlaceholder.visibility = View.GONE
                adapter.addItems(it)
                vb.listView.requestFocus()
            }
        })

        if (privateSession) {
            showActivePrivateDownloads()
        } else if (downloadsHistoryModel.allItems.isEmpty()) {
            downloadsHistoryModel.loadNextItems()
        } else {
            vb.tvPlaceholder.visibility = View.GONE
            adapter.addItems(downloadsHistoryModel.allItems)
            vb.listView.requestFocus()
        }
    }

    override fun onResume() {
        super.onResume()
        activeDownloadsModel.registerListener(this)
        if (privateSession) showActivePrivateDownloads()
    }

    private fun showActivePrivateDownloads() {
        activeDownloadsModel.activeDownloads.filter { it.downloadInfo.incognito }
            .forEach { showPrivateDownload(it.downloadInfo) }
    }

    private fun showPrivateDownload(download: Download) {
        adapter.addLiveItem(download)
        vb.tvPlaceholder.visibility = View.GONE
    }

    override fun onPause() {
        activeDownloadsModel.unregisterListener(this@DownloadsActivity)
        super.onPause()
    }

    private fun onItemClick(download: Download) {
        if (download.size != download.bytesReceived) {
            //file should be already marked in UI as broken
            return
        }


        val fileURI = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            download.filepath.toUri()
        } else {
            val file = File(download.filepath)
            if (!file.exists()) {
                Utils.showToast(this, R.string.file_not_found)
                return
            }

            FileProvider.getUriForFile(this@DownloadsActivity,
                BuildConfig.APPLICATION_ID + ".provider",
                file)
        }


        val openIntent = Intent(Intent.ACTION_VIEW)
        val mimeType = contentResolver.getType(fileURI)
        openIntent.setDataAndType(fileURI, mimeType)
        openIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        try {
            startActivity(openIntent)
        } catch (e: ActivityNotFoundException) {
            Utils.showToast(this, getString(R.string.no_app_for_file_type))
        }

    }

    private fun onItemLongClick(anchor: View, download: Download): Boolean {
        if (download.size == Download.BROKEN_MARK || download.size == Download.CANCELLED_MARK ||
                download.size == download.bytesReceived) {
            showFinishedDownloadOptionsPopup(anchor, download)
        } else {
            showUnfinishedDownloadOptionsPopup(anchor, download)
        }
        return true
    }

    private fun showUnfinishedDownloadOptionsPopup(anchor: View, download: Download) {
        val pm = PopupMenu(this, anchor, Gravity.BOTTOM)
        pm.menu.add(R.string.cancel)
        pm.setOnMenuItemClickListener {
            activeDownloadsModel.cancelDownload(download)
            true
        }
        pm.show()
    }

    private fun showFinishedDownloadOptionsPopup(anchor: View, download: Download) {
        val pm = PopupMenu(this, anchor, Gravity.BOTTOM)
        if (download.filename.endsWith(".apk", true) &&
            download.size == download.bytesReceived) {
            pm.menu.add(0, 0, 0, R.string.install)
        }
        pm.menu.add(0, 1, 1, R.string.open_folder)
        pm.menu.add(0, 2, 2, R.string.delete)
        pm.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                0 -> installAPK(download)
                1 -> openFolder()
                2 -> deleteItem(download)
            }
            true
        }
        pm.show()
    }

    private fun installAPK(download: Download) {
        val canInstallFromOtherSources = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            packageManager.canRequestPackageInstalls()
        } else
            Settings.Secure.getInt(this.contentResolver, Settings.Secure.INSTALL_NON_MARKET_APPS) == 1

        if(canInstallFromOtherSources) {
            ApkInstaller.launch(this, download)
        } else {
            AlertDialog.Builder(this)
                    .setTitle(R.string.app_name)
                    .setMessage(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        R.string.turn_on_unknown_sources_for_app else R.string.turn_on_unknown_sources)
                    .setPositiveButton(android.R.string.ok, DialogInterface.OnClickListener { dialog, which -> run {
                        val intentSettings = Intent()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            intentSettings.action = Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES
                            intentSettings.data = "package:${BuildConfig.APPLICATION_ID}".toUri()
                        } else {
                            intentSettings.action = Settings.ACTION_SECURITY_SETTINGS
                        }
                        intentSettings.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            startActivity(intentSettings)
                        } catch (e: Exception) {
                            Log.w(TAG, "Opening the unknown-sources settings failed", e)
                            Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
                        }
                    }})
                    .show()

        }
    }

    private fun openFolder() {
        val uri = Uri.parse(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "resource/folder")
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Utils.showToast(this, R.string.no_file_explorer_msg)
        }
    }

    private fun deleteItem(download: Download) = lifecycleScope.launch(Dispatchers.Main) {
        activeDownloadsModel.deleteItem(download)
        adapter.remove(download)
    }

    override fun onDownloadUpdated(downloadInfo: Download) {
        if (privateSession && downloadInfo.incognito) showPrivateDownload(downloadInfo)
        adapter.updateDownload(downloadInfo)
    }

    override fun onDownloadError(downloadInfo: Download, responseCode: Int, responseMessage: String) {
        if (privateSession && downloadInfo.incognito) showPrivateDownload(downloadInfo)
        adapter.updateDownload(downloadInfo)
    }

    override fun onAllDownloadsComplete() {}

    companion object {
        val TAG: String = DownloadsActivity::class.java.simpleName

    }
}
