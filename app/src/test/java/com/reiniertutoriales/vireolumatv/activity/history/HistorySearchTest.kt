package com.reiniertutoriales.vireolumatv.activity.history

import android.app.Application
import androidx.room.Room
import com.reiniertutoriales.vireolumatv.model.HistoryItem
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class HistorySearchTest {
    @Test fun searchFindsSubstringsAndMatchesWildcardCharactersLiterally() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
        try {
            val dao = db.historyDao()
            dao.insert(HistoryItem().apply { time = 1; title = "YouTube - cat videos"; url = "https://www.youtube.com/watch?v=1" })
            dao.insert(HistoryItem().apply { time = 2; title = "100% pure"; url = "https://shop.test/a_b" })
            dao.insert(HistoryItem().apply { time = 3; title = "Other"; url = "https://other.test" })

            suspend fun find(query: String): List<String> {
                val pattern = HistoryModel.likePattern(query)
                return dao.search(pattern).map { it.title }
            }

            // Contains-match, case-insensitive for ASCII (LIKE).
            assertEquals(listOf("YouTube - cat videos"), find("youtube"))
            // "_" and "%" are literal characters, not wildcards.
            assertEquals(listOf("100% pure"), find("%"))
            assertEquals(listOf("100% pure"), find("_"))
            assertEquals(emptyList<String>(), find("a%b"))
        } finally {
            db.close()
        }
    }
}
