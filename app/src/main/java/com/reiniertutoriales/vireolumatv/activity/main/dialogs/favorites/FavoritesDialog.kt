package com.reiniertutoriales.vireolumatv.activity.main.dialogs.favorites

import android.app.Dialog
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.model.FavoriteItem
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Lists bookmarks; adds the current page, and in edit mode edits or deletes a bookmark. */
class FavoritesDialog(
    context: Context,
    val scope: CoroutineScope,
    private val callback: Callback,
    private val currentPageTitle: String?,
    private val currentPageUrl: String?,
) : Dialog(context) {

    interface Callback {
        fun onFavoriteChoosen(item: FavoriteItem?)
    }

    private val items = ArrayList<FavoriteItem>()
    private val list: RecyclerView
    private val placeholder: View
    private val loading: View
    private val editButton: Button
    private val adapter = FavoritesListAdapter(
        onOpen = { item ->
            callback.onFavoriteChoosen(item)
            dismiss()
        },
        onEdit = ::edit,
        onDelete = ::delete,
    )

    init {
        setCancelable(true)
        setContentView(R.layout.dialog_favorites)
        setTitle(R.string.bookmarks)
        list = findViewById(R.id.listView)
        placeholder = findViewById(R.id.tvPlaceholder)
        loading = findViewById(R.id.pbLoading)
        editButton = findViewById(R.id.btnEdit)
        list.layoutManager = LinearLayoutManager(context)
        list.adapter = adapter
        findViewById<Button>(R.id.btnAdd).setOnClickListener {
            edit(FavoriteItem().apply {
                title = currentPageTitle
                url = currentPageUrl
            })
        }
        editButton.setOnClickListener {
            adapter.editMode = !adapter.editMode
            editButton.setText(if (adapter.editMode) R.string.done else R.string.edit)
        }
        load { items.addAll(AppDatabase.db.favoritesDao().getAll()) }
    }

    override fun onStart() {
        super.onStart()
        window?.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    /** Runs a database step with the spinner shown, then shows the list or the empty hint. */
    private fun load(step: suspend () -> Unit) {
        loading.visibility = View.VISIBLE
        scope.launch(Dispatchers.Main) {
            try { step() } finally { // Also on failure: the spinner must not stay on screen.
                loading.visibility = View.GONE
                list.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
                placeholder.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                adapter.submitList(items.toList())
            }
        }
    }

    private fun edit(item: FavoriteItem) {
        FavoriteEditorDialog(context, object : FavoriteEditorDialog.Callback {
            override fun onDone(item: FavoriteItem) = save(item)
        }, item).show()
    }

    private fun save(item: FavoriteItem) = load {
        val dao = AppDatabase.db.favoritesDao()
        if (item.id == 0L) {
            item.id = dao.insert(item)
            items.add(0, item)
        } else {
            dao.update(item)
            // Edited in place: same object, so the diff cannot see it; rebind that row.
            items.indexOf(item).takeIf { it >= 0 }?.let { adapter.notifyItemChanged(it) }
        }
    }

    private fun delete(item: FavoriteItem) = load {
        AppDatabase.db.favoritesDao().delete(item)
        items.remove(item)
    }
}
