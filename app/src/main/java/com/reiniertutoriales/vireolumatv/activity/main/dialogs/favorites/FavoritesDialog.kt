package com.reiniertutoriales.vireolumatv.activity.main.dialogs.favorites

import android.app.Dialog
import android.content.Context
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.databinding.DialogFavoritesBinding
import com.reiniertutoriales.vireolumatv.model.FavoriteItem
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import com.reiniertutoriales.vireolumatv.utils.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Writes a detached bookmark, then submits a fresh database snapshot to the row diff. */
class FavoritesDialog(
    context: Context,
    scope: CoroutineScope,
    private val callback: Callback,
    private val currentPageTitle: String?,
    private val currentPageUrl: String?,
) : Dialog(context) {
    interface Callback { fun onFavoriteChoosen(item: FavoriteItem?) }

    private val ui = DialogFavoritesBinding.inflate(layoutInflater)
    private val operations = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
    private var busy = false
    private val rows = FavoritesListAdapter(
        onOpen = { callback.onFavoriteChoosen(it); dismiss() },
        onEdit = ::edit,
        onDelete = { item -> refresh { AppDatabase.db.favoritesDao().delete(item) } },
    )

    init {
        setTitle(R.string.bookmarks)
        setContentView(ui.root)
        ui.listView.layoutManager = LinearLayoutManager(context)
        ui.listView.adapter = rows
        ui.btnAdd.setOnClickListener {
            edit(FavoriteItem().apply { title = currentPageTitle; url = currentPageUrl })
        }
        ui.btnEdit.setOnClickListener {
            rows.editMode = !rows.editMode
            ui.btnEdit.setText(if (rows.editMode) R.string.done else R.string.edit)
        }
        refresh {}
    }

    override fun onStart() {
        super.onStart()
        window?.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun edit(item: FavoriteItem) {
        if (busy) return
        FavoriteEditorDialog(context, object : FavoriteEditorDialog.Callback {
            override fun onDone(item: FavoriteItem) = refresh {
                val bookmarks = AppDatabase.db.favoritesDao()
                if (item.id == 0L) bookmarks.insert(item) else bookmarks.update(item)
            }
        }, item).show()
    }

    private fun refresh(write: suspend () -> Unit) {
        if (busy) return
        busy = true
        renderBusy()
        operations.launch(Dispatchers.Main) {
            try {
                write()
                val snapshot = AppDatabase.db.favoritesDao().getAll()
                rows.submitList(snapshot) { renderRows() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                context.toast(R.string.error)
            } finally {
                busy = false
                renderBusy()
                renderRows()
            }
        }
    }

    private fun renderBusy() {
        ui.pbLoading.isVisible = busy
        ui.btnAdd.isEnabled = !busy
        ui.btnEdit.isEnabled = !busy
    }

    private fun renderRows() {
        ui.listView.isVisible = rows.itemCount > 0
        ui.tvPlaceholder.isVisible = !busy && rows.itemCount == 0
    }

    override fun dismiss() {
        operations.cancel()
        super.dismiss()
    }
}
