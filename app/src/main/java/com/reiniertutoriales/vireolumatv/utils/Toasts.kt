package com.reiniertutoriales.vireolumatv.utils

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes

// Long toasts: a TV is read from across the room, so a message stays up long enough to read.

fun Context.toast(@StringRes text: Int) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

fun Context.toast(text: CharSequence) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
