package com.reiniertutoriales.vireolumatv.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CookiePersistenceTest {
    private val queued = mutableListOf<Runnable>()
    private var flushes = 0
    private var flushThread: Thread? = null

    @Test fun leavingTheScreenWritesTheCookiesOffTheCallingThread() {
        val cookies = CookiePersistence({ queued += it }, { flushes++; flushThread = Thread.currentThread() })
        cookies.onLeftScreen(privateMode = false)
        assertEquals("Nothing is written on the caller's thread", 0, flushes)
        assertEquals(1, queued.size)
        val worker = Thread { queued.single().run() }.apply { start(); join() }
        assertEquals(1, flushes)
        assertTrue(flushThread === worker)
    }

    @Test fun privateCookiesAreNeverWrittenToKeep() {
        CookiePersistence({ queued += it }, { flushes++ }).onLeftScreen(privateMode = true)
        assertTrue(queued.isEmpty())
        assertEquals(0, flushes)
    }

    @Test fun aWebViewThatCannotWriteDoesNotCrashTheBrowser() {
        CookiePersistence({ it.run() }, { error("no WebView provider") }).onLeftScreen(privateMode = false)
    }

    @Test fun theDefaultWorkerRunsInTheBackground() {
        val done = java.util.concurrent.CountDownLatch(1)
        var thread: Thread? = null
        CookiePersistence(flush = { thread = Thread.currentThread(); done.countDown() }).onLeftScreen(false)
        assertTrue(done.await(5, java.util.concurrent.TimeUnit.SECONDS))
        assertTrue(thread !== Thread.currentThread())
        assertTrue(thread!!.isDaemon)
    }
}
