package com.reiniertutoriales.vireolumatv.utils.observable

import org.junit.Assert.assertEquals
import org.junit.Test

class ObservableValueTest {
    @Test
    fun subscribeReceivesCurrentValueUnlessDisabled() {
        val value = LiveValue(1)
        val seen = mutableListOf<Int>()
        value.subscribe({ seen += it })
        value.subscribe({ seen += it * 10 }, notifyOnSubscribe = false)
        assertEquals(listOf(1), seen)
    }

    @Test
    fun settingValueNotifiesEverySubscriberOnce() {
        val value = LiveValue("a")
        val seen = mutableListOf<String>()
        value.subscribe({ seen += "first:$it" }, notifyOnSubscribe = false)
        value.subscribe({ seen += "second:$it" }, notifyOnSubscribe = false)
        value.value = "b"
        assertEquals(listOf("second:b", "first:b"), seen)
    }

    @Test
    fun sameObserverIsRegisteredOnce() {
        val value = LiveValue(0)
        val observer: ValueObserver<Int> = {}
        value.subscribe(observer, notifyOnSubscribe = false)
        value.subscribe(observer, notifyOnSubscribe = false)
        assertEquals(1, value.observers.size)
    }

    @Test
    fun observerRemovingItselfDoesNotSkipOthers() {
        val value = LiveValue(0)
        val seen = mutableListOf<String>()
        lateinit var selfRemoving: ValueObserver<Int>
        selfRemoving = {
            seen += "self:$it"
            value.unsubscribe(selfRemoving)
        }
        value.subscribe(selfRemoving, notifyOnSubscribe = false)
        value.subscribe({ seen += "other:$it" }, notifyOnSubscribe = false)
        value.value = 5
        value.value = 6
        assertEquals(listOf("other:5", "self:5", "other:6"), seen)
    }
}

class ObservableListTest {
    @Test
    fun mutationsNotifyOncePerOperation() {
        val list = LiveList<Int>()
        var notifications = 0
        list.subscribe({ notifications++ }, notifyOnSubscribe = false)

        list.add(1)
        list.addAll(listOf(2, 3))
        list[0] = 9
        list.swap(0, 2)
        list.replaceAll(listOf(4, 5))
        list.clear()

        assertEquals(6, notifications)
    }

    @Test
    fun swapExchangesElements() {
        val list = LiveList<String>().apply { addAll(listOf("a", "b", "c")) }
        list.swap(0, 2)
        assertEquals(listOf("c", "b", "a"), list.toList())
    }

    @Test
    fun replaceAllKeepsOnlyTheNewElements() {
        val list = LiveList<Int>().apply { addAll(listOf(1, 2)) }
        list.replaceAll(listOf(7))
        assertEquals(listOf(7), list.toList())
    }

    @Test
    fun listObserverMayUnsubscribeWhileNotified() {
        val list = LiveList<Int>()
        val seen = mutableListOf<String>()
        lateinit var once: ListChangeObserver<Int>
        once = { seen += "once"; list.unsubscribe(once) }
        list.subscribe(once, notifyOnSubscribe = false)
        list.subscribe({ seen += "always" }, notifyOnSubscribe = false)
        list.add(1)
        list.add(2)
        assertEquals(listOf("once", "always", "always"), seen)
    }
}
