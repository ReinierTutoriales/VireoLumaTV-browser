package com.reiniertutoriales.vireolumatv.activity.main

import android.app.Application
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import com.reiniertutoriales.vireolumatv.model.SiteSettings
import com.reiniertutoriales.vireolumatv.model.dao.SiteSettingsDao
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class SiteSettingsCacheTest {
    /** An in-memory table that counts reads; [gate] holds the next read's answer until released. */
    private class Hosts : SiteSettingsDao() {
        val rows = HashMap<String, SiteSettings>()
        var reads = 0
        var gate: CompletableDeferred<Unit>? = null
        override fun findByHostName(name: String): SiteSettings? {
            reads++
            // A query sees the table as it is when it runs; a slow one only answers late.
            val answer = rows[name]?.let { copy(it) }
            val hold = gate
            if (hold != null) { gate = null; runBlocking { hold.await() } }
            return answer
        }
        override suspend fun load(name: String): SiteSettings = copy(rows.getValue(name))
        override suspend fun addIfMissing(item: SiteSettings): Long {
            if (item.hostName in rows) return -1
            rows[item.hostName] = copy(item)
            return 1
        }
        override suspend fun writeFavicon(host: String, favicon: String?) { rows[host]?.favicon = favicon }
        override suspend fun writePopupLevel(host: String, level: Int) { rows[host]?.popupBlockLevel = level }
        override suspend fun ensureHostRow(host: String): SiteSettings { addIfMissing(SiteSettings(host)); return load(host) }
        override suspend fun storeFaviconRow(host: String, favicon: String?) { addIfMissing(SiteSettings(host)); writeFavicon(host, favicon) }
        override suspend fun storePopupLevelRow(host: String, level: Int) { addIfMissing(SiteSettings(host)); writePopupLevel(host, level) }
        private fun copy(row: SiteSettings) = SiteSettings(row.hostName).also {
            it.popupBlockLevel = row.popupBlockLevel
            it.favicon = row.favicon
        }
    }

    private fun store(hosts: Hosts) = TabStore().apply { siteSettingsDao = { hosts } }

    @Test fun navigatingWithinASiteWithoutSettingsAsksTheDatabaseOnce() = runBlocking {
        val hosts = Hosts()
        val model = store(hosts)
        try {
            val tab = BrowserTab(url = "https://plain.test/a")
            repeat(20) {
                tab.url = "https://plain.test/page$it"
                assertNull(model.siteSettingsFor(tab, false))
            }
            assertEquals(1, hosts.reads)
            assertEquals(SiteSettings.DEFAULT_POPUP_POLICY, model.popupBlockingLevel(tab))
            tab.url = "https://other.test/"
            model.siteSettingsFor(tab, false)
            assertEquals("another site is another question", 2, hosts.reads)
        } finally { model.clear() }
    }

    @Test fun aPopupChangeAfterANegativeAnswerCreatesTheRowPersistsAndAppliesAtOnce() = runBlocking {
        val hosts = Hosts()
        val model = store(hosts)
        try {
            val tab = BrowserTab(url = "https://plain.test/")
            assertNull(model.siteSettingsFor(tab, false))
            model.setPopupPolicy(SiteSettings.POPUPS_BLOCK_ALL, tab)
            assertEquals(SiteSettings.POPUPS_BLOCK_ALL, hosts.rows.getValue("plain.test").popupBlockLevel)
            assertEquals(SiteSettings.POPUPS_BLOCK_ALL, model.popupBlockingLevel(tab))
            val reads = hosts.reads
            assertEquals(SiteSettings.POPUPS_BLOCK_ALL, model.siteSettingsFor(tab, false)!!.popupBlockLevel)
            assertEquals("the write's own answer is current", reads, hosts.reads)
        } finally { model.clear() }
    }

    @Test fun anyWriteMakesEarlierAnswersStale() = runBlocking {
        val hosts = Hosts()
        val model = store(hosts)
        try {
            val tab = BrowserTab(url = "https://icon.test/")
            assertNull(model.siteSettingsFor(tab, false))
            hosts.storeFavicon("icon.test", "icon.png")
            assertEquals("icon.png", model.siteSettingsFor(tab, false)!!.favicon)
            assertEquals(2, hosts.reads)
        } finally { model.clear() }
    }

    @Test fun aSlowReadBegunBeforeAWriteDoesNotReplaceItsResult() = runBlocking {
        val hosts = Hosts()
        val model = store(hosts)
        try {
            val tab = BrowserTab(url = "https://race.test/")
            val release = CompletableDeferred<Unit>()
            hosts.gate = release
            val slow = async { model.siteSettingsFor(tab, false) }
            withContext(Dispatchers.IO) { while (hosts.reads == 0) Thread.sleep(1) }
            model.setPopupPolicy(SiteSettings.POPUPS_ALLOWED, tab)
            release.complete(Unit)
            assertNull("the slow read saw no row", slow.await())
            assertEquals(SiteSettings.POPUPS_ALLOWED, model.popupBlockingLevel(tab))
        } finally { model.clear() }
    }
}
