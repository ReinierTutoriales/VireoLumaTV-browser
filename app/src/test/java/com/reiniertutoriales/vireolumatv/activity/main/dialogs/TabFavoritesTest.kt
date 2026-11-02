package com.reiniertutoriales.vireolumatv.activity.main.dialogs

import android.app.Application
import androidx.room.Room
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.activity.main.dialogs.favorites.TabFavorites
import com.reiniertutoriales.vireolumatv.model.Favorite
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config as RobolectricConfig

/** "Add to favorites" on a tab stores that tab's page once, through the existing table. */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(application = Application::class, sdk = [28])
class TabFavoritesTest {
    private fun <T> withDatabase(test: suspend (AppDatabase) -> T) = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try { test(db) } finally { db.close() }
    }

    @Test fun aWebPageIsSavedWithItsTitleAndAddressOnce() = withDatabase { db ->
        val favorites = db.favoriteDao()
        assertEquals(TabFavorites.Result.ADDED, TabFavorites.add(favorites, "  Noticias  ", "https://news.test/today"))
        assertEquals(TabFavorites.Result.ALREADY_SAVED, TabFavorites.add(favorites, "Otro título", "https://news.test/today"))
        val saved = favorites.getAll()
        assertEquals(1, saved.size)
        assertEquals("Noticias", saved[0].title)
        assertEquals("https://news.test/today", saved[0].url)
    }

    @Test fun anUntitledPageIsNamedAfterItsHost() = withDatabase { db ->
        TabFavorites.add(db.favoriteDao(), " ", "http://intranet.test/a")
        assertEquals("intranet.test", db.favoriteDao().getAll().single().title)
    }

    @Test fun onlyWebPagesAreSaved() = withDatabase { db ->
        for (url in listOf(null, "", "about:blank", "data:text/html,x", "javascript:alert(1)",
                "file:///sdcard/a.html", Config.HOME_PAGE_URL, Config.HOME_PAGE_URL + "index.html", "https:///nohost")) {
            assertEquals("url: $url", TabFavorites.Result.NOT_A_WEB_PAGE, TabFavorites.add(db.favoriteDao(), "x", url))
        }
        assertEquals(0, db.favoriteDao().getAll().size)
    }

    @Test fun anExistingBookmarkIsKeptWithItsOwnName() = withDatabase { db ->
        val favorites = db.favoriteDao()
        favorites.insert(Favorite().apply { title = "Mine"; url = "https://kept.test/" })
        assertEquals(TabFavorites.Result.ALREADY_SAVED, TabFavorites.add(favorites, "New name", "https://kept.test/"))
        assertEquals("Mine", favorites.getAll().single { it.url == "https://kept.test/" }.title)
        assertEquals(TabFavorites.Result.ADDED, TabFavorites.add(favorites, "Other", "https://other.test/"))
        assertEquals(2, favorites.getAll().size)
    }
}
