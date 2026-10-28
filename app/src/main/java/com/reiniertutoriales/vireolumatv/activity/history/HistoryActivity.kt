package com.reiniertutoriales.vireolumatv.activity.history

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.databinding.HistoryScreenBinding
import com.reiniertutoriales.vireolumatv.model.HistoryItem
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import com.reiniertutoriales.vireolumatv.utils.activemodel.ActiveModelsRepository
import kotlinx.coroutines.launch

/**
 * Visited pages, newest first. OK returns the address to the browser through [KEY_URL]. A long
 * press starts selecting rows; "Delete" then removes the selection and Back leaves selection mode.
 */
class HistoryActivity : AppCompatActivity() {
    private lateinit var vb: HistoryScreenBinding
    private lateinit var model: HistoryModel
    private var consumedPage: List<HistoryItem>? = null
    private val adapter = HistoryAdapter(::onItemClick, ::onItemLongClick)

    /** Back leaves selection mode first; only then does it close the screen. */
    private val leaveSelection = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            adapter.isMultiselectMode = false
            updateCommands()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vb = HistoryScreenBinding.inflate(layoutInflater)
        setContentView(vb.root)
        model = ActiveModelsRepository.get(HistoryModel::class, this)
        onBackPressedDispatcher.addCallback(this, leaveSelection)

        vb.itemList.layoutManager = LinearLayoutManager(this)
        vb.itemList.adapter = adapter
        vb.itemList.addOnScrollListener(nextPageLoader)
        vb.clearButton.setOnClickListener { confirmDelete(all = true) }
        vb.deleteSelectedButton.setOnClickListener { confirmDelete(all = false) }

        model.lastLoadedItems.subscribe(this) {
            if (it.isEmpty() || it === consumedPage) return@subscribe
            consumedPage = it
            adapter.addItems(it)
            vb.itemList.requestFocus()
        }
        model.loadItems()
    }

    /** Asks for the next page when the last visible row is one of the last two; search shows one page. */
    private val nextPageLoader = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            val last = (recyclerView.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
            if (model.searchQuery.isEmpty() && adapter.itemCount > 0 && last >= adapter.itemCount - 2) {
                model.loadItems(append = true)
            }
        }
    }

    private fun onItemClick(item: HistoryItem) {
        if (adapter.isMultiselectMode) {
            adapter.toggleSelection(item)
            updateCommands()
        } else {
            setResult(RESULT_OK, Intent().putExtra(KEY_URL, item.url))
            finish()
        }
    }

    private fun onItemLongClick(item: HistoryItem): Boolean {
        if (adapter.isMultiselectMode) return false
        adapter.isMultiselectMode = true
        adapter.toggleSelection(item)
        updateCommands()
        return true
    }

    private fun updateCommands() {
        leaveSelection.isEnabled = adapter.isMultiselectMode
        vb.deleteSelectedButton.isVisible = adapter.selectedItems.isNotEmpty()
    }

    private fun confirmDelete(all: Boolean) {
        val selection = adapter.selectedItems
        if (adapter.realCount == 0L || (!all && selection.isEmpty())) return
        AlertDialog.Builder(this)
            .setTitle(R.string.action_delete)
            .setMessage(if (all) R.string.history_delete_all else R.string.history_delete_selected)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch {
                    val history = AppDatabase.db.historyDao()
                    if (all) {
                        history.deleteWhereTimeLessThan(Long.MAX_VALUE)
                        adapter.erase()
                    } else {
                        history.delete(*selection.toTypedArray())
                        adapter.remove(selection)
                    }
                    adapter.isMultiselectMode = false
                    updateCommands()
                    vb.clearButton.requestFocus()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        const val KEY_URL = "url"
    }
}
