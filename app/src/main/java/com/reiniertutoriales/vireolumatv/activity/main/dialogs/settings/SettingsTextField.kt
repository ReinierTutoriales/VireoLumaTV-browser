package com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText

/**
 * Text field that D-pad navigation measures by its whole box. A plain EditText reports the
 * cursor as its focused rect, so FocusFinder can skip it in favour of a farther full-width row.
 */
class SettingsTextField @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = android.R.attr.editTextStyle,
) : AppCompatEditText(context, attrs, defStyleAttr) {
    override fun getFocusedRect(r: Rect) = getDrawingRect(r)
}
