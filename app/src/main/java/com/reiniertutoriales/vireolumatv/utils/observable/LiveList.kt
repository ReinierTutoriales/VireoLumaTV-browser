package com.reiniertutoriales.vireolumatv.utils.observable

import java.util.function.Predicate
import java.util.function.UnaryOperator

typealias ListChangeObserver<T> = (LiveList<T>) -> Unit

/** A list that tells its observers after every mutating call, once per call. */
class LiveList<T> : ArrayList<T>(), Watchable<ListChangeObserver<T>> {
    override val observers = ArrayList<ListChangeObserver<T>>()

    override fun notifyObserver(observer: ListChangeObserver<T>) = observer(this)

    private inline fun <R> changing(change: () -> R): R = change().also { notifyObservers() }

    override fun add(element: T) = changing { super.add(element) }
    override fun add(index: Int, element: T) = changing { super.add(index, element) }
    override fun addAll(elements: Collection<T>) = changing { super.addAll(elements) }
    override fun addAll(index: Int, elements: Collection<T>) = changing { super.addAll(index, elements) }
    override fun set(index: Int, element: T) = changing { super.set(index, element) }
    override fun remove(element: T) = changing { super.remove(element) }
    override fun removeAt(index: Int) = changing { super.removeAt(index) }
    override fun removeAll(elements: Collection<T>) = changing { super.removeAll(elements) }
    override fun retainAll(elements: Collection<T>) = changing { super.retainAll(elements) }
    override fun removeIf(filter: Predicate<in T>) = changing { super.removeIf(filter) }
    override fun replaceAll(operator: UnaryOperator<T>) = changing { super.replaceAll(operator) }
    override fun removeRange(fromIndex: Int, toIndex: Int) = changing { super.removeRange(fromIndex, toIndex) }
    override fun clear() = changing { super.clear() }

    /** Exchanges two elements with a single notification. */
    fun swap(i: Int, j: Int) = changing { super.set(i, super.set(j, super.get(i))) }

    /** Replaces the whole content with a single notification. */
    fun replaceAll(elements: Collection<T>): Boolean = changing {
        super.clear()
        super.addAll(elements)
    }
}
