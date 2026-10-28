package com.reiniertutoriales.vireolumatv.utils.observable

import org.junit.Assert.assertEquals
import org.junit.Test

class ObservableValueTest {
    @Test
    fun subscribeReceivesCurrentValueUnlessDisabled() {
        val value = LiveValue(1)
        val seen = mutableListOf<Int>()
        value.observe({ seen += it })
        value.observe({ seen += it * 10 }, sendCurrent = false)
        assertEquals(listOf(1), seen)
    }

    @Test
    fun settingValueNotifiesEverySubscriberOnce() {
        val value = LiveValue("a")
        val seen = mutableListOf<String>()
        value.observe({ seen += "first:$it" }, sendCurrent = false)
        value.observe({ seen += "second:$it" }, sendCurrent = false)
        value.value = "b"
        assertEquals(listOf("second:b", "first:b"), seen)
    }

    @Test
    fun sameObserverIsRegisteredOnce() {
        val value = LiveValue(0)
        val observer: ValueWatcher<Int> = {}
        value.observe(observer, sendCurrent = false)
        value.observe(observer, sendCurrent = false)
        assertEquals(1, value.watchers.size)
    }

    @Test
    fun observerRemovingItselfDoesNotSkipOthers() {
        val value = LiveValue(0)
        val seen = mutableListOf<String>()
        lateinit var selfRemoving: ValueWatcher<Int>
        selfRemoving = {
            seen += "self:$it"
            value.forget(selfRemoving)
        }
        value.observe(selfRemoving, sendCurrent = false)
        value.observe({ seen += "other:$it" }, sendCurrent = false)
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
        list.observe({ notifications++ }, sendCurrent = false)

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
        lateinit var once: ListWatcher<Int>
        once = { seen += "once"; list.forget(once) }
        list.observe(once, sendCurrent = false)
        list.observe({ seen += "always" }, sendCurrent = false)
        list.add(1)
        list.add(2)
        assertEquals(listOf("once", "always", "always"), seen)
    }
}
