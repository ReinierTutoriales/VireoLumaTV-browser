package com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.SwitchCompat
import com.reiniertutoriales.vireolumatv.R

/**
 * Appends settings rows to [parent] in the order they are called, which is also the
 * D-pad order: each row is a single column stop, so no explicit focus links are needed.
 */
internal class SettingsRows(private val parent: ViewGroup) {
    private val inflater = LayoutInflater.from(parent.context)

    private fun row(@LayoutRes layout: Int): View =
        inflater.inflate(layout, parent, false).also(parent::addView)

    private fun View.title(@StringRes title: Int?) {
        val label = findViewById<TextView>(R.id.settingTitle)
        if (title == null) label.visibility = View.GONE else label.setText(title)
    }

    /** A list choice. [onSelected] also runs for the selection the Spinner reports after layout. */
    fun choice(@StringRes title: Int, @IdRes id: Int, items: Array<String>, selected: Int,
               onSelected: (Int) -> Unit): Spinner {
        val spinner = row(R.layout.view_setting_choice).apply { title(title) }
            .findViewById<Spinner>(R.id.settingControl)
        spinner.id = id
        spinner.adapter = ArrayAdapter(parent.context, android.R.layout.simple_spinner_item, items).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinner.setSelection(selected, false)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(list: AdapterView<*>, view: View?, position: Int, rowId: Long) =
                onSelected(position)
            override fun onNothingSelected(list: AdapterView<*>) {}
        }
        return spinner
    }

    /** A text field; its row is returned through [group] so it can be shown and hidden. */
    fun field(@StringRes title: Int?, @StringRes hint: Int, @IdRes id: Int = View.NO_ID,
              @IdRes groupId: Int = View.NO_ID, visible: Boolean = true): EditText {
        val group = row(R.layout.view_setting_field).apply {
            this.id = groupId
            title(title)
            visibility = if (visible) View.VISIBLE else View.GONE
        }
        return group.findViewById<EditText>(R.id.settingControl).apply {
            this.id = id
            setHint(hint)
        }
    }

    /** An on/off row. Activating the row flips the switch; [onChanged] sees every change. */
    fun toggle(@StringRes title: Int, @IdRes id: Int, checked: Boolean,
               onChanged: (SwitchCompat, Boolean) -> Unit): SwitchCompat {
        val rowView = row(R.layout.view_setting_toggle).apply { this.id = id; title(title) }
        val switch = rowView.findViewById<SwitchCompat>(R.id.settingControl)
        switch.id = View.NO_ID
        switch.isChecked = checked
        switch.setOnCheckedChangeListener { _, isChecked -> onChanged(switch, isChecked) }
        rowView.setOnClickListener { switch.toggle() }
        return switch
    }

    /** A percentage-style slider over [min]..[max] with its current value printed beside it. */
    fun slider(@StringRes title: Int, @IdRes id: Int, min: Int, max: Int, value: Int,
               label: (Int) -> String, onChanged: (Int) -> Unit) {
        val rowView = row(R.layout.view_setting_slider).apply { title(title) }
        val valueLabel = rowView.findViewById<TextView>(R.id.settingValue)
        val bar = rowView.findViewById<SeekBar>(R.id.settingControl)
        bar.id = id
        bar.max = max - min
        bar.progress = value - min
        valueLabel.text = label(value)
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                onChanged(min + progress)
                valueLabel.text = label(min + progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    class Status(val text: TextView, val action: View, val progress: ProgressBar)

    /** Multi-line status text with its action on the full-width row below it. */
    fun status(@IdRes textId: Int, @IdRes actionId: Int, @DrawableRes icon: Int, @StringRes action: Int,
               onAction: () -> Unit): Status {
        val rowView = row(R.layout.view_setting_status).apply { title(action) }
        rowView.findViewById<ImageView>(R.id.settingIcon).setImageResource(icon)
        val status = Status(rowView.findViewById(R.id.settingValue), rowView.findViewById(R.id.settingControl),
            rowView.findViewById(R.id.settingProgress))
        status.text.id = textId
        status.action.id = actionId
        status.action.setOnClickListener { onAction() }
        return status
    }

    /** Read-only text between rows; never a focus stop. */
    fun info(text: CharSequence, sizeSp: Float = 16f) {
        (row(R.layout.view_setting_info) as TextView).apply {
            this.text = text
            textSize = sizeSp
        }
    }

    /** A row that opens something on activation; returns its value text so callers can update it. */
    fun value(title: CharSequence, value: CharSequence?, @IdRes id: Int = View.NO_ID, onClick: () -> Unit): TextView {
        val rowView = row(R.layout.view_setting_value)
        rowView.id = id
        rowView.findViewById<TextView>(R.id.settingTitle).text = title
        rowView.setOnClickListener { onClick() }
        return rowView.findViewById<TextView>(R.id.settingValue).apply {
            text = value
            visibility = if (value.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
    }

    fun button(@StringRes text: Int, @IdRes id: Int, onClick: () -> Unit): Button =
        (row(R.layout.view_setting_button) as Button).apply {
            this.id = id
            setText(text)
            setOnClickListener { onClick() }
        }

    /** Rows built inside [build] go into one container that can be hidden as a unit. */
    fun group(visible: Boolean, build: SettingsRows.() -> Unit): LinearLayout {
        val container = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (visible) View.VISIBLE else View.GONE
        }
        parent.addView(container, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT))
        SettingsRows(container).build()
        return container
    }

    companion object {
        /** Shows a hidden row with a short fade; already visible rows stay as they are. */
        fun reveal(view: View) {
            if (view.visibility == View.VISIBLE) return
            view.visibility = View.VISIBLE
            view.startAnimation(AnimationUtils.loadAnimation(view.context, android.R.anim.fade_in))
        }
    }
}
