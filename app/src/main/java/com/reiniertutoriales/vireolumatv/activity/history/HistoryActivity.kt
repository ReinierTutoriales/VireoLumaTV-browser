package com.reiniertutoriales.vireolumatv.activity.history

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.Text
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvDayHeader
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvDialog
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvListRow
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvSymbol
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvSymbolIcon
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvTextButton
import com.reiniertutoriales.vireolumatv.activity.main.compose.VireoTvTheme
import com.reiniertutoriales.vireolumatv.model.HistoryItem
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import com.reiniertutoriales.vireolumatv.utils.AppLanguages
import com.reiniertutoriales.vireolumatv.utils.activemodel.ScreenModels
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Visited pages, newest first. OK hands the address back through [PickAddress]. A long press
 * starts selecting rows; "Delete" then removes the selection and Back leaves selection mode.
 */
class HistoryActivity : ComponentActivity() {
    private lateinit var model: HistoryPages
    private var consumedPage: List<HistoryItem>? = null
    internal val list = HistoryList()
    private val listFocus = FocusRequester()
    private val clearFocus = FocusRequester()
    /** Focus goes to the list once, when its first page arrives. */
    private var focusOnLoad = true

    /** Back leaves selection mode first; only then does it close the screen. */
    private val leaveSelection = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = setSelectionMode(false)
    }

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLanguages.localized(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ScreenModels.get(HistoryPages::class, this)
        onBackPressedDispatcher.addCallback(this, leaveSelection)
        setContent { VireoTvTheme { HistoryScreen() } }
        model.loadedPage.observe(this) {
            if (it.isEmpty() || it === consumedPage) return@observe
            consumedPage = it
            list.addItems(it)
        }
        model.loadItems()
    }

    private fun setSelectionMode(on: Boolean) {
        list.selectionMode = on
        leaveSelection.isEnabled = on
    }

    private fun onItemClick(item: HistoryItem) {
        if (list.selectionMode) {
            list.toggleSelection(item)
        } else {
            setResult(RESULT_OK, PickAddress.answer(item.url))
            finish()
        }
    }

    private fun onItemLongClick(item: HistoryItem) {
        if (list.selectionMode) return
        setSelectionMode(true)
        list.toggleSelection(item)
    }

    private fun confirmDelete(all: Boolean) {
        val selection = list.selectedItems
        if (list.rowCount == 0L || (!all && selection.isEmpty())) return
        TvDialog.Builder(this)
            .setTitle(R.string.action_delete)
            .setMessage(if (all) R.string.history_delete_all else R.string.history_delete_selected)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch {
                    val history = AppDatabase.db.visitDao()
                    if (all) {
                        history.deleteWhereTimeLessThan(Long.MAX_VALUE)
                        list.erase()
                    } else {
                        history.delete(*selection.toTypedArray())
                        list.remove(selection)
                    }
                    setSelectionMode(false)
                    runCatching { clearFocus.requestFocus() }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    @Composable
    private fun HistoryScreen() {
        val rows = list.rows
        val state = rememberLazyListState()
        val dayFormat = remember { DateFormat.getDateInstance() }
        val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
        Column(
            Modifier.semantics { testTagsAsResourceId = true }.fillMaxSize()
                .padding(start = dimensionResource(R.dimen.safe_horizontal), end = dimensionResource(R.dimen.safe_horizontal),
                    top = dimensionResource(R.dimen.safe_vertical)),
        ) {
            Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                list.revision.let {
                    if (list.selectedItems.isNotEmpty()) {
                        TvTextButton(stringResource(R.string.action_delete), { confirmDelete(all = false) },
                            Modifier.testTag("history_delete_selected"))
                    }
                }
                TvTextButton(stringResource(R.string.action_clear), { confirmDelete(all = true) },
                    Modifier.testTag("history_clear").focusRequester(clearFocus))
            }
            LazyColumn(
                state = state,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp).testTag("history_list"),
                contentPadding = PaddingValues(bottom = dimensionResource(R.dimen.safe_vertical)),
            ) {
                // contentType lets the list reuse a header's composition only for headers, a row's for rows.
                items(rows, key = { if (it.isDayHeader) "day:${it.time}" else "visit:${it.id}" },
                    contentType = { it.isDayHeader }) { item ->
                    if (item.isDayHeader) {
                        TvDayHeader(dayFormat.format(Date(item.time)), Modifier.testTag("history_day_${item.time}"))
                    } else {
                        val first = item === rows.firstOrNull { !it.isDayHeader }
                        HistoryRow(item, timeFormat, if (first) Modifier.focusRequester(listFocus) else Modifier)
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
        // Asks for the next page when one of the last two rows is shown; search shows one page.
        LaunchedEffect(state) {
            snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }.collect { last ->
                val count = list.rows.size
                if (model.searchQuery.isEmpty() && count > 0 && last >= count - 2) model.loadItems(append = true)
            }
        }
    }

    @Composable
    private fun HistoryRow(item: HistoryItem, timeFormat: SimpleDateFormat, modifier: Modifier) {
        val selecting = list.selectionMode
        val selected = list.revision.let { item.selected }
        TvListRow(
            title = item.title,
            supporting = item.url,
            onClick = { onItemClick(item) },
            onLongClick = { onItemLongClick(item) },
            selected = selected,
            modifier = modifier.padding(vertical = 2.dp).testTag("history_row_${item.id}"),
            leadingContent = {
                if (selecting) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        if (selected) TvSymbolIcon(TvSymbol.Done, Modifier.size(22.dp))
                    }
                }
                Text(timeFormat.format(Date(item.time)), color = Color.Gray, fontSize = 14.sp, modifier = Modifier.width(48.dp))
            },
        )
    }

    /** Opens the history and returns the address picked there, or null when it was left without one. */
    object PickAddress : ActivityResultContract<Unit, String?>() {
        private const val ADDRESS = "address"

        override fun createIntent(context: Context, input: Unit) = Intent(context, HistoryActivity::class.java)

        override fun parseResult(resultCode: Int, intent: Intent?): String? =
            intent?.getStringExtra(ADDRESS)?.takeIf { resultCode == RESULT_OK }

        internal fun answer(address: String) = Intent().putExtra(ADDRESS, address)
    }
}
