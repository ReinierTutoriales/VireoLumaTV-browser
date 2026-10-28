package com.reiniertutoriales.vireolumatv.activity.main.dialogs.favorites

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.model.FavoriteItem
import com.reiniertutoriales.vireolumatv.utils.ViewFaviconLoader

/**
 * Bookmark rows. Activating a row opens the bookmark, or edits it in [editMode], where a Delete
 * button follows each row (D-pad Right). Only changed rows are rebound, via [submitList].
 */
class FavoritesListAdapter(
    private val onOpen: (FavoriteItem) -> Unit,
    private val onEdit: (FavoriteItem) -> Unit,
    private val onDelete: (FavoriteItem) -> Unit,
    config: AsyncDifferConfig<FavoriteItem> = AsyncDifferConfig.Builder(DIFF).build(),
) : ListAdapter<FavoriteItem, FavoritesListAdapter.Row>(config) {

    var editMode = false
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Row(LayoutInflater.from(parent.context).inflate(R.layout.favorite_row, parent, false))

    override fun onBindViewHolder(holder: Row, position: Int) = holder.bind(getItem(position))

    inner class Row(view: View) : RecyclerView.ViewHolder(view) {
        private val content: View = view.findViewById(R.id.row_body)
        private val title: TextView = view.findViewById(R.id.title_text)
        private val url: TextView = view.findViewById(R.id.address_text)
        private val delete: ImageButton = view.findViewById(R.id.delete_button)
        private val icon = ViewFaviconLoader(view, view.findViewById<ImageView>(R.id.icon_image))

        fun bind(item: FavoriteItem) {
            title.text = item.title?.takeIf { it.isNotBlank() } ?: item.url
            url.text = item.url
            icon.bind(item.url)
            delete.visibility = if (editMode) View.VISIBLE else View.GONE
            content.setOnClickListener { if (editMode) onEdit(item) else if (!item.isFolder) onOpen(item) }
            delete.setOnClickListener { onDelete(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<FavoriteItem>() {
            override fun areItemsTheSame(old: FavoriteItem, new: FavoriteItem) = old.id == new.id
            override fun areContentsTheSame(old: FavoriteItem, new: FavoriteItem) =
                old.title == new.title && old.url == new.url
        }
    }
}
