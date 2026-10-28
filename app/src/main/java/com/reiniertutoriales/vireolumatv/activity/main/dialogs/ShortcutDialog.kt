package com.reiniertutoriales.vireolumatv.activity.main.dialogs

import android.content.Context
import android.view.KeyEvent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDialog
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.singleton.shortcuts.KeyBinding
import com.reiniertutoriales.vireolumatv.singleton.shortcuts.Shortcut
import com.reiniertutoriales.vireolumatv.singleton.shortcuts.ShortcutMgr
import com.reiniertutoriales.vireolumatv.utils.BackNavigationEventsAdapter
import com.reiniertutoriales.vireolumatv.utils.RemoteInput

/**
 * Edits the key of one [Shortcut]. While recording, the next key press is stored: released before
 * the long-press timeout as a short binding, held past it as a long-press binding.
 */
class ShortcutDialog(context: Context, private val shortcut: Shortcut) : AppCompatDialog(context, R.style.ThemeOverlay_Vireo_Dialog) {
    private val shortcuts = ShortcutMgr.getInstance()
    private val keyText: TextView
    private val recordButton: Button
    private var recording = false

    init {
        setCancelable(true)
        setContentView(R.layout.dialog_shortcut)
        checkNotNull(findViewById<TextView>(R.id.tvActionTitle)).setText(shortcut.titleResId)
        keyText = checkNotNull(findViewById(R.id.tvActionKey))
        recordButton = checkNotNull(findViewById(R.id.btnSetKey))
        recordButton.setOnClickListener { setRecording(!recording) }
        checkNotNull(findViewById<Button>(R.id.btnClearKey)).setOnClickListener {
            setRecording(false)
            shortcuts.set(shortcut, null)
            showKey()
        }
        showKey()
    }

    private fun showKey() {
        keyText.text = shortcuts.binding(shortcut)?.describe(context) ?: context.getString(R.string.shortcut_unassigned)
    }

    private fun setRecording(on: Boolean) {
        recording = on
        recordButton.setText(if (on) R.string.shortcut_waiting else R.string.shortcut_assign)
    }

    /** Stores the press as the new key unless the browser needs that key for navigation. */
    private fun record(event: KeyEvent, longPress: Boolean) {
        setRecording(false)
        val code = KeyBinding.codeOf(event)
        if (code in RemoteInput.NAVIGATION_KEYS || code in BackNavigationEventsAdapter.BACK_KEYS) {
            Toast.makeText(context, R.string.shortcut_reserved, Toast.LENGTH_SHORT).show()
            return
        }
        shortcuts.set(shortcut, KeyBinding(code, event.modifiers, longPress))
        showKey()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!recording) return super.onKeyDown(keyCode, event)
        event.startTracking() // Lets the framework report onKeyLongPress for a held key.
        return true
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (!recording) return super.onKeyLongPress(keyCode, event)
        record(event, longPress = true)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // After a long press recording is already off, so its release is not recorded again.
        if (!recording) return super.onKeyUp(keyCode, event)
        record(event, longPress = false)
        return true
    }
}
