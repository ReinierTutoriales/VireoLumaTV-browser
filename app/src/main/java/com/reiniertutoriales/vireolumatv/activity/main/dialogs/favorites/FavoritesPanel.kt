package com.reiniertutoriales.vireolumatv.activity.main.dialogs.favorites

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDialog
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.databinding.PanelFavoritesBinding
import com.reiniertutoriales.vireolumatv.model.Favorite
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import com.reiniertutoriales.vireolumatv.utils.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Favorites as a side panel at the end edge: add the page shown, open a favorite with [onOpen], or
 * in edit mode edit or delete one. Each write is followed by a fresh database snapshot for the rows.
 */
class FavoritesPanel(
    context: Context,
    scope: CoroutineScope,
    private val currentPageTitle: String?,
    private val currentPageUrl: String?,
    private val onOpen: (Favorite) -> Unit,
) : AppCompatDialog(context, R.style.SidePanel) {
    private val ui = PanelFavoritesBinding.inflate(layoutInflater)
    private val operations = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
    private var busy = false
    private val rows = FavoriteRows(
        onOpen = { dismiss(); onOpen(it) },
        onEdit = ::edit,
        onDelete = { item -> refresh { AppDatabase.db.favoriteDao().delete(item) } },
    )

    init {
        setContentView(ui.root)
        ui.itemList.layoutManager = LinearLayoutManager(context)
        ui.itemList.adapter = rows
        ui.addButton.setOnClickListener {
            edit(Favorite().apply { title = currentPageTitle; url = currentPageUrl })
        }
        ui.editButton.setOnClickListener {
            rows.editMode = !rows.editMode
            ui.editButton.setText(if (rows.editMode) R.string.action_done else R.string.action_edit)
        }
        refresh {}
    }

    override fun onStart() {
        super.onStart()
        window?.apply {
            setGravity(Gravity.END)
            setLayout(context.resources.getDimensionPixelSize(R.dimen.side_panel_wide), ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    private fun edit(item: Favorite) {
        if (busy) return
        FavoriteEditor(context, item) { edited ->
            refresh {
                val favorites = AppDatabase.db.favoriteDao()
                if (edited.id == 0L) favorites.insert(edited) else favorites.update(edited)
            }
        }.show()
    }

    private fun refresh(write: suspend () -> Unit) {
        if (busy) return
        busy = true
        renderBusy()
        operations.launch(Dispatchers.Main) {
            try {
                write()
                val snapshot = AppDatabase.db.favoriteDao().getAll()
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
        ui.loadingSpinner.isVisible = busy
        ui.addButton.isEnabled = !busy
        ui.editButton.isEnabled = !busy
    }

    private fun renderRows() {
        ui.itemList.isVisible = rows.itemCount > 0
        ui.emptyText.isVisible = !busy && rows.itemCount == 0
    }

    override fun dismiss() {
        operations.cancel()
        super.dismiss()
    }
}
