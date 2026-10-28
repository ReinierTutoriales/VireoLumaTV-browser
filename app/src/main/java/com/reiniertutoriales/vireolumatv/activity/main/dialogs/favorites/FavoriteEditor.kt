package com.reiniertutoriales.vireolumatv.activity.main.dialogs.favorites

import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatDialog
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.model.Favorite
import com.reiniertutoriales.vireolumatv.utils.toast

/**
 * Edits a copy of a favorite and hands it to [onSave]; the original is untouched until saved. An
 * address is required, and one without "scheme://" gets https://.
 */
class FavoriteEditor(context: Context, item: Favorite, private val onSave: (Favorite) -> Unit) :
    AppCompatDialog(context, R.style.ThemeOverlay_Vireo_Dialog) {
    private val draft = item.copyForEdit()

    init {
        setContentView(R.layout.dialog_favorite_editor)
        findViewById<TextView>(R.id.dialog_title)?.setText(if (draft.id == 0L) R.string.favorites_new else R.string.action_edit)
        val title = checkNotNull(findViewById<EditText>(R.id.title_field)).apply { setText(draft.title) }
        val address = checkNotNull(findViewById<EditText>(R.id.address_field)).apply { setText(draft.url) }
        findViewById<Button>(R.id.cancel_button)?.setOnClickListener { dismiss() }
        findViewById<Button>(R.id.done_button)?.setOnClickListener {
            val url = address.text.toString().trim()
            if (url.isEmpty()) { // An empty address is not a bookmark: keep the dialog open.
                context.toast(R.string.error)
                return@setOnClickListener
            }
            draft.title = title.text.toString()
            draft.url = if (WITH_SCHEME.matches(url)) url else "https://$url"
            dismiss()
            onSave(draft)
        }
    }

    private companion object {
        val WITH_SCHEME = Regex("^[A-Za-z]+://.*$")
    }
}
