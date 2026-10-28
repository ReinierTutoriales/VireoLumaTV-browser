package com.reiniertutoriales.vireolumatv.utils

import java.util.Calendar

/** Whether two instants fall on the same local calendar day; [NO_DAY] matches nothing. */
fun sameLocalDay(first: Long, second: Long): Boolean {
    if (first == NO_DAY || second == NO_DAY) return false
    val a = Calendar.getInstance().apply { timeInMillis = first }
    val b = Calendar.getInstance().apply { timeInMillis = second }
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

/** "No day yet", for the first row of a list. */
const val NO_DAY = -1L
