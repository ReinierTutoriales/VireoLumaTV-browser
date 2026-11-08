package com.reiniertutoriales.vireolumatv.activity.main.dialogs.favorites

import android.content.Context
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvDialog
import com.reiniertutoriales.vireolumatv.model.Favorite

/**
 * Edits a copy of [item] and hands it to [onSave]; the original is untouched until saved. An address
 * is required (the dialog stays open and says so), and one without "scheme://" gets https://.
 */
fun editFavorite(context: Context, item: Favorite, onSave: (Favorite) -> Unit): TvDialog {
    val draft = item.copyForEdit()
    return TvDialog.Builder(context)
        .setTitle(if (draft.id == 0L) R.string.favorites_new else R.string.action_edit)
        .setFields(TvDialog.Field(R.string.field_title), TvDialog.Field(R.string.field_address, uri = true))
        .setValidator { dialog ->
            if (dialog.fieldValue(1).isBlank()) context.getString(R.string.favorite_address_required) else null
        }
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.action_done) { dialog, _ ->
            val fields = dialog as TvDialog
            val url = fields.fieldValue(1).trim()
            draft.title = fields.fieldValue(0)
            draft.url = if (WITH_SCHEME.matches(url)) url else "https://$url"
            onSave(draft)
        }
        .create()
        .apply {
            setFieldValue(0, draft.title.orEmpty())
            setFieldValue(1, draft.url.orEmpty())
        }
        .also { it.show() }
}

private val WITH_SCHEME = Regex("^[A-Za-z]+://.*$")
