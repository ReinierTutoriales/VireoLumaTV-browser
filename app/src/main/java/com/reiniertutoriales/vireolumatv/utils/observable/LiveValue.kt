package com.reiniertutoriales.vireolumatv.utils.observable

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.reflect.KProperty

/**
 * Something watchers can follow. An watcher is registered at most once; notifying works on a
 * copy of the list, so an watcher may forget itself or others while being notified.
 */
interface Watchable<O> {
    val watchers: ArrayList<O>

    /** Calls [watcher] once with the current state. */
    fun sendTo(watcher: O)

    fun sendAll() = watchers.toList().forEach(::sendTo)

    fun observe(watcher: O, sendCurrent: Boolean = true) {
        if (watcher in watchers) return
        watchers += watcher
        if (sendCurrent) sendTo(watcher)
    }

    fun forget(watcher: O) {
        watchers -= watcher
    }

    fun observe(lifecycleOwner: LifecycleOwner, watcher: O) = observe(lifecycleOwner.lifecycle, true, watcher)

    fun observe(lifecycleOwner: LifecycleOwner, sendCurrent: Boolean = true, watcher: O) =
        observe(lifecycleOwner.lifecycle, sendCurrent, watcher)

    fun observe(lifecycle: Lifecycle, watcher: O) = observe(lifecycle, true, watcher)

    /** Follows only while [lifecycle] is resumed, and stops for good when it is destroyed. */
    fun observe(lifecycle: Lifecycle, sendCurrent: Boolean = true, watcher: O) {
        lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                when (event) {
                    Lifecycle.Event.ON_RESUME -> observe(watcher, sendCurrent)
                    Lifecycle.Event.ON_PAUSE -> forget(watcher)
                    Lifecycle.Event.ON_DESTROY -> {
                        lifecycle.removeObserver(this)
                        forget(watcher)
                    }
                    else -> {}
                }
            }
        })
    }
}

typealias ValueWatcher<T> = (value: T) -> Unit

/** A value that tells its watchers, newest first, whenever it is set. Also usable as a delegate. */
open class LiveValue<T>(default: T) : Watchable<ValueWatcher<T>> {
    override val watchers = ArrayList<ValueWatcher<T>>()

    open var value: T = default
        set(value) {
            field = value
            sendAll()
        }

    override fun sendTo(watcher: ValueWatcher<T>) = watcher(value)

    override fun sendAll() = watchers.toList().asReversed().forEach(::sendTo)

    operator fun getValue(thisRef: Any, prop: KProperty<*>): T = value

    operator fun setValue(thisRef: Any, prop: KProperty<*>, value: T) {
        this.value = value
    }
}
