package com.reiniertutoriales.vireolumatv.utils.observable

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.reflect.KProperty

/**
 * Just simple and more convenient replacement of java.util.Observable/Observer
 * to implement observer pattern
 */

interface Subscribable<O> {
    val observers: ArrayList<O>
    fun subscribe(observer: O, notifyOnSubscribe: Boolean = true) {
        if (!observers.contains(observer)) {
            observers.add(observer)
            if (notifyOnSubscribe) {
                notifyObserver(observer)
            }
        }
    }

    fun subscribe(lifecycleOwner: LifecycleOwner, observer: O) {
        subscribe(lifecycleOwner.lifecycle, true, observer)
    }

    fun subscribe(lifecycleOwner: LifecycleOwner, notifyOnSubscribe: Boolean = true, observer: O) {
        subscribe(lifecycleOwner.lifecycle, notifyOnSubscribe, observer)
    }

    fun subscribe(lifecycle: Lifecycle, observer: O) {
        subscribe(lifecycle, true, observer)
    }

    fun subscribe(lifecycle: Lifecycle, notifyOnSubscribe: Boolean = true, observer: O) {
        lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                when (source.lifecycle.currentState) {
                    Lifecycle.State.INITIALIZED, Lifecycle.State.CREATED -> {
                        unsubscribe(observer)
                    }
                    Lifecycle.State.RESUMED -> {
                        subscribe(observer, notifyOnSubscribe)
                    }
                    Lifecycle.State.STARTED -> {
                        unsubscribe(observer)
                    }
                    Lifecycle.State.DESTROYED -> {
                        lifecycle.removeObserver(this)
                        unsubscribe(observer)
                    }
                }
            }
        })
    }

    fun unsubscribe(observer: O) {
        observers.remove(observer)
    }

    fun notifyObservers() {
        for (observer in observers) {
            notifyObserver(observer)
        }
    }

    fun notifyObserver(observer: O)
}

typealias ValueObserver<T> = (value: T) -> Unit

open class ObservableValue<T>(default: T) : Subscribable<ValueObserver<T>> {
    open var value: T = default
        set(value) {
            field = value
            notifyObservers()
        }

    override val observers = ArrayList<ValueObserver<T>>()

    override fun notifyObservers() {
        //observers can be removed during iteration
        for (i in observers.size - 1 downTo 0) {
            observers[i](value)
        }
    }

    override fun notifyObserver(observer: ValueObserver<T>) {
        observer(value)
    }

    operator fun getValue(thisRef: Any, prop: KProperty<*>): T = this.value
    operator fun setValue(thisRef: Any, prop: KProperty<*>, value: T) {this.value = value}
}
