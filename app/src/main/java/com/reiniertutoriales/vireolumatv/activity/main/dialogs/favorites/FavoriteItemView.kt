package com.reiniertutoriales.vireolumatv.activity.main.dialogs.favorites

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.reiniertutoriales.vireolumatv.databinding.ViewFavoriteItemBinding
import com.reiniertutoriales.vireolumatv.model.FavoriteItem
import com.reiniertutoriales.vireolumatv.utils.ViewFaviconLoader

/**
 * Created by PDT on 13.09.2016.
 */
class FavoriteItemView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        FrameLayout(context, attrs, defStyleAttr) {
    private lateinit var vb: ViewFavoriteItemBinding
    private lateinit var faviconLoader: ViewFaviconLoader
    var favorite: FavoriteItem? = null
        private set
    var listener: Listener? = null

    interface Listener {
        fun onDeleteClick(favorite: FavoriteItem)
        fun onEditClick(favorite: FavoriteItem)
    }

    init {
        init()
    }

    private fun init() {
        vb = ViewFavoriteItemBinding.inflate(LayoutInflater.from(context), this, true)

        faviconLoader = ViewFaviconLoader(this, vb.ivIcon)

        vb.ibDelete.setOnClickListener { favorite?.let { listener?.onDeleteClick(it)} }

        vb.llContent.setOnClickListener {  favorite?.let {listener?.onEditClick(it)} }
    }

    fun bind(favorite: FavoriteItem, editMode: Boolean) {
        this.favorite = favorite
        vb.ibDelete.visibility = if (editMode) View.VISIBLE else View.GONE
        vb.llContent.isClickable = editMode
        vb.llContent.isFocusable = editMode
        vb.tvTitle.text = favorite.title
        vb.tvUrl.text = favorite.url
        faviconLoader.bind(favorite.url)
    }
}
