package com.reiniertutoriales.vireolumatv.utils.observable

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.reflect.KProperty

/**
 * Something observers can follow. An observer is registered at most once; notifying works on a
 * copy of the list, so an observer may unsubscribe itself or others while being notified.
 */
interface Subscribable<O> {
    val observers: ArrayList<O>

    /** Calls [observer] once with the current state. */
    fun notifyObserver(observer: O)

    fun notifyObservers() = observers.toList().forEach(::notifyObserver)

    fun subscribe(observer: O, notifyOnSubscribe: Boolean = true) {
        if (observer in observers) return
        observers += observer
        if (notifyOnSubscribe) notifyObserver(observer)
    }

    fun unsubscribe(observer: O) {
        observers -= observer
    }

    fun subscribe(lifecycleOwner: LifecycleOwner, observer: O) = subscribe(lifecycleOwner.lifecycle, true, observer)

    fun subscribe(lifecycleOwner: LifecycleOwner, notifyOnSubscribe: Boolean = true, observer: O) =
        subscribe(lifecycleOwner.lifecycle, notifyOnSubscribe, observer)

    fun subscribe(lifecycle: Lifecycle, observer: O) = subscribe(lifecycle, true, observer)

    /** Follows only while [lifecycle] is resumed, and stops for good when it is destroyed. */
    fun subscribe(lifecycle: Lifecycle, notifyOnSubscribe: Boolean = true, observer: O) {
        lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                when (event) {
                    Lifecycle.Event.ON_RESUME -> subscribe(observer, notifyOnSubscribe)
                    Lifecycle.Event.ON_PAUSE -> unsubscribe(observer)
                    Lifecycle.Event.ON_DESTROY -> {
                        lifecycle.removeObserver(this)
                        unsubscribe(observer)
                    }
                    else -> {}
                }
            }
        })
    }
}

typealias ValueObserver<T> = (value: T) -> Unit

/** A value that tells its observers, newest first, whenever it is set. Also usable as a delegate. */
open class ObservableValue<T>(default: T) : Subscribable<ValueObserver<T>> {
    override val observers = ArrayList<ValueObserver<T>>()

    open var value: T = default
        set(value) {
            field = value
            notifyObservers()
        }

    override fun notifyObserver(observer: ValueObserver<T>) = observer(value)

    override fun notifyObservers() = observers.toList().asReversed().forEach(::notifyObserver)

    operator fun getValue(thisRef: Any, prop: KProperty<*>): T = value

    operator fun setValue(thisRef: Any, prop: KProperty<*>, value: T) {
        this.value = value
    }
}
