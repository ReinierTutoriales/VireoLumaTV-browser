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

/** [items] with a [header] for its day before the first item of each day, in the items' order. */
inline fun <T> withDayHeaders(items: List<T>, time: (T) -> Long, header: (Long) -> T): List<T> {
    val rows = ArrayList<T>(items.size + 8)
    var lastHeaderTime = NO_DAY
    for (item in items) {
        val at = time(item)
        if (!sameLocalDay(at, lastHeaderTime)) {
            lastHeaderTime = at
            rows.add(header(at))
        }
        rows.add(item)
    }
    return rows
}
