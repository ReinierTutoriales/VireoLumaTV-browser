package com.reiniertutoriales.vireolumatv.activity.history

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.databinding.ActivityHistoryBinding
import com.reiniertutoriales.vireolumatv.model.HistoryItem
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import com.reiniertutoriales.vireolumatv.utils.onAnimationEnd
import com.reiniertutoriales.vireolumatv.utils.activemodel.ActiveModelsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.core.view.isGone
import androidx.core.view.isVisible

/**
 * Created by fedex on 29.12.16.
 */

class HistoryActivity : AppCompatActivity() {

    private lateinit var vb: ActivityHistoryBinding
    private var ibDelete: ImageButton? = null
    private val adapter = HistoryAdapter(::onItemClick, ::onItemLongClick)
    private lateinit var historyModel: HistoryModel

    // Loads the next page when the last visible row is within one row of the end of the list.
    private val onListScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            val lastVisible = (recyclerView.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
            if (adapter.itemCount != 0 && lastVisible >= adapter.itemCount - 2 && "" == historyModel.searchQuery) {
                historyModel.loadItems(adapter.realCount)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vb = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(vb.root)

        historyModel = ActiveModelsRepository.get(HistoryModel::class, this)
        onBackPressedDispatcher.addCallback(this, exitMultiselectOnBack)

        ibDelete = findViewById(R.id.ibDelete)

        vb.listView.layoutManager = LinearLayoutManager(this)
        vb.listView.adapter = adapter
        vb.listView.addOnScrollListener(onListScrollListener)

        historyModel.lastLoadedItems.subscribe(this, false) {
            if (it.isEmpty()) return@subscribe
            adapter.addItems(it)
            vb.listView.requestFocus()
        }

        historyModel.loadItems()
    }

    private fun showDeleteDialog(deleteAll: Boolean) {
        if (adapter.realCount == 0L || (adapter.selectedItems.isEmpty() && !deleteAll)) return
        AlertDialog.Builder(this)
                .setTitle(R.string.delete)
                .setMessage(if (deleteAll) R.string.msg_delete_history_all else R.string.msg_delete_history)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        if (deleteAll) {
                            AppDatabase.db.historyDao().deleteWhereTimeLessThan(Long.MAX_VALUE)
                            adapter.erase()
                        } else {
                            AppDatabase.db.historyDao().delete(*(adapter.selectedItems).toTypedArray())
                            adapter.remove(adapter.selectedItems)
                        }
                    }
                }
                .setNeutralButton(android.R.string.cancel) { dialogInterface, i -> }
                .show()
    }

    private fun onItemClick(item: HistoryItem) {
        if (adapter.isMultiselectMode) {
            adapter.toggleSelection(item)
            updateMenu()
        } else {
            setResult(RESULT_OK, Intent().putExtra(KEY_URL, item.url))
            finish()
        }
    }

    private fun onItemLongClick(item: HistoryItem): Boolean {
        if (adapter.isMultiselectMode) return false
        adapter.isMultiselectMode = true
        adapter.toggleSelection(item)
        updateMenu()
        return true
    }

    private fun updateMenu() {
        exitMultiselectOnBack.isEnabled = adapter.isMultiselectMode == true
        val selection = adapter.selectedItems
        if (selection.isEmpty()) {
            if (ibDelete!!.isGone) return
            val anim = AnimationUtils.loadAnimation(this, R.anim.right_menu_out_anim)
            anim.onAnimationEnd { ibDelete!!.visibility = View.GONE }
            ibDelete!!.startAnimation(anim)
        } else {
            if (ibDelete!!.isVisible) return
            ibDelete!!.visibility = View.VISIBLE
            ibDelete!!.startAnimation(AnimationUtils.loadAnimation(this, R.anim.right_menu_in_anim))
        }
    }

    // onBackPressed() is not called for apps targeting API 36 on Android 16 (predictive back).
    private val exitMultiselectOnBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            adapter.isMultiselectMode = false
            updateMenu()
        }
    }

    fun onClearHistoryClick(view: View) {
        showDeleteDialog(true)
    }

    fun onClearHistoryItemsClick(view: View) {
        showDeleteDialog(false)
    }

    companion object {

        const val KEY_URL = "url"
    }
}
