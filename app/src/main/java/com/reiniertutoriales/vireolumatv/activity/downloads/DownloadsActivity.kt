package com.reiniertutoriales.vireolumatv.activity.downloads

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.Text
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvDayHeader
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvDialog
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvListRow
import com.reiniertutoriales.vireolumatv.activity.main.compose.VireoTvTheme
import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.service.downloads.DownloadFiles
import com.reiniertutoriales.vireolumatv.utils.ApkInstaller
import com.reiniertutoriales.vireolumatv.utils.AppLanguages
import com.reiniertutoriales.vireolumatv.utils.activemodel.ScreenModels
import com.reiniertutoriales.vireolumatv.utils.toast
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Download list. The normal screen pages through stored downloads and follows running ones; the
 * private screen ([IncognitoDownloadsActivity]) shows only this session's downloads, which are never
 * stored. OK opens a saved file; a long press offers the [DownloadAction]s that fit the download.
 */
open class DownloadsActivity : ComponentActivity() {
    private lateinit var board: TransferBoard
    private lateinit var stored: DownloadHistoryPages
    internal val list = DownloadList()
    private val listFocus = FocusRequester()
    /** Focus goes to the list once, when its first rows arrive. */
    private var focusOnLoad = true
    private val privateSession: Boolean get() = this is IncognitoDownloadsActivity

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLanguages.localized(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        board = ScreenModels.get(TransferBoard::class, this)
        stored = ScreenModels.get(DownloadHistoryPages::class, this)
        setContent { VireoTvTheme { DownloadsScreen() } }
        // Followed only while the screen is in front, like the list it redraws.
        board.changed.observe(this, false) { download -> download?.let(::redraw) }
        if (privateSession) return
        stored.loadedPage.observe(this, false) { list.addItems(it) }
        if (stored.allItems.isEmpty()) stored.loadNextItems() else list.addItems(stored.allItems)
    }

    override fun onResume() {
        super.onResume()
        // Private downloads exist only in memory: the running tasks are the whole list.
        if (privateSession) board.transfers.forEach { redraw(it.job) }
    }

    private fun redraw(download: Download) {
        if (privateSession) {
            if (!download.incognito) return
            list.addLiveItem(download)
        }
        list.updateDownload(download)
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

    /** The actions that fit [download], in a dialog; the remote's long press opens it. */
    internal fun offerActions(download: Download) {
        val actions = DownloadAction.available(download)
        TvDialog.Builder(this)
            .setTitle(download.filename)
            .setItems(actions.map { getString(it.label) }.toTypedArray()) { _, which ->
                when (actions[which]) {
                    DownloadAction.CANCEL -> board.cancel(download)
                    DownloadAction.INSTALL -> install(download)
                    DownloadAction.SHOW_FOLDER -> showFolder()
                    DownloadAction.DELETE -> lifecycleScope.launch {
                        DownloadFiles.erase(download)
                        list.remove(download)
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun install(download: Download) {
        if (ApkInstaller.canInstall(this)) return ApkInstaller.launch(this, download)
        val explanation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) R.string.install_allow_this_app
            else R.string.install_allow_unknown_sources
        TvDialog.Builder(this)
            .setTitle(R.string.app_name)
            .setMessage(explanation)
            .setPositiveButton(android.R.string.ok) { _, _ -> launch(ApkInstaller.permissionSettings(this), R.string.system_settings_unavailable) }
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

    @Composable
    private fun DownloadsScreen() {
        val rows = list.rows
        val state = rememberLazyListState()
        val dayFormat = remember { DateFormat.getDateInstance() }
        val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
        Box(Modifier.semantics { testTagsAsResourceId = true }.fillMaxSize()) {
            if (rows.isEmpty()) {
                Text(stringResource(R.string.downloads_empty), color = colorResource(R.color.on_surface_variant), fontSize = 22.sp,
                    modifier = Modifier.align(Alignment.Center).testTag("downloads_empty"))
            }
            LazyColumn(
                state = state,
                modifier = Modifier.fillMaxSize().testTag("downloads_list"),
                contentPadding = PaddingValues(horizontal = dimensionResource(R.dimen.safe_horizontal),
                    vertical = dimensionResource(R.dimen.safe_vertical)),
            ) {
                items(rows, key = { rowKey(it) }) { item ->
                    if (item.isDayHeader) {
                        TvDayHeader(dayFormat.format(Date(item.time)))
                    } else {
                        val first = item === rows.firstOrNull { !it.isDayHeader }
                        DownloadRow(item, timeFormat, if (first) Modifier.focusRequester(listFocus) else Modifier)
                    }
                }
            }
        }
        LaunchedEffect(rows.isNotEmpty()) {
            if (rows.isNotEmpty() && focusOnLoad) {
                focusOnLoad = false
                runCatching { listFocus.requestFocus() }
            }
        }
        // Asks for the next stored page when one of the last two rows is shown.
        if (!privateSession) LaunchedEffect(state) {
            snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }.collect { last ->
                val count = list.rows.size
                if (count > 0 && last >= count - 2) stored.loadNextItems()
            }
        }
    }

    @Composable
    private fun DownloadRow(download: Download, timeFormat: SimpleDateFormat, modifier: Modifier) {
        val progress = list.revision.let { DownloadProgress.of(LocalContext.current, download) }
        Column(modifier.padding(vertical = 2.dp)) {
            TvListRow(
                title = download.filename,
                supporting = download.url,
                onClick = { open(download) },
                onLongClick = { offerActions(download) },
                modifier = Modifier.testTag("download_row_${download.id}"),
                trailingContent = {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(timeFormat.format(Date(download.time)), color = Color.Gray, fontSize = 14.sp)
                        Text(progress.text, fontSize = 14.sp,
                            color = if (progress.error) colorResource(R.color.error) else Color.Gray)
                    }
                },
            )
            if (progress.fraction != null || progress.indeterminate) ProgressLine(progress.fraction)
        }
    }

    /** A thin bar under the row: filled to [fraction], or a moving segment when the total is unknown. */
    @Composable
    private fun ProgressLine(fraction: Float?) {
        val shape = RoundedCornerShape(2.dp)
        val brand = colorResource(R.color.brand)
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(4.dp).clipToBounds().background(Color.DarkGray, shape)) {
            if (fraction != null) {
                Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(4.dp).background(brand, shape))
            } else {
                val moving by rememberInfiniteTransition(label = "download").animateFloat(
                    initialValue = -0.3f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "segment")
                var width by remember { mutableIntStateOf(0) }
                Box(Modifier.fillMaxWidth().height(4.dp).onSizeChanged { width = it.width }) {
                    Box(Modifier.offset { IntOffset((moving * width).toInt(), 0) }
                        .fillMaxWidth(0.3f).height(4.dp).background(brand, shape))
                }
            }
        }
    }

    /** Stored downloads by id; this session's private ones have none, so by identity. */
    private fun rowKey(item: Download): String = when {
        item.isDayHeader -> "day:${item.time}"
        item.id != 0L -> "download:${item.id}"
        else -> "live:${System.identityHashCode(item)}"
    }
}
