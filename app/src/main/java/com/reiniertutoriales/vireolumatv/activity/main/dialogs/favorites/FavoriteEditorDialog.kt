package com.reiniertutoriales.vireolumatv.activity.main.dialogs.favorites

import android.app.Dialog
import android.content.Context
import android.widget.Button
import android.widget.EditText
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.model.FavoriteItem
import com.reiniertutoriales.vireolumatv.utils.toast

/** Edits a bookmark's title and address; an address without "scheme://" gets https://. */
class FavoriteEditorDialog(context: Context, private val callback: Callback, item: FavoriteItem) :
    Dialog(context) {

    interface Callback {
        fun onDone(item: FavoriteItem)
    }

    private val draft = item.copyForEdit()

    init {
        setCancelable(true)
        setTitle(if (draft.id == 0L) R.string.new_bookmark else R.string.edit)
        setContentView(R.layout.dialog_new_favorite_item)
        val title = findViewById<EditText>(R.id.etTitle).apply { setText(draft.title) }
        val address = findViewById<EditText>(R.id.etUrl).apply { setText(draft.url) }
        findViewById<Button>(R.id.btnCancel).setOnClickListener { dismiss() }
        findViewById<Button>(R.id.btnDone).setOnClickListener {
            val url = address.text.toString().trim()
            if (url.isEmpty()) { // An empty address is not a bookmark: keep the dialog open.
                context.toast(R.string.error)
                return@setOnClickListener
            }
            draft.title = title.text.toString()
            draft.url = if (SCHEME.matches(url)) url else "https://$url"
            callback.onDone(draft)
            dismiss()
        }
    }

    private companion object {
        val SCHEME = Regex("^[A-Za-z]+://.*$")
    }
}
