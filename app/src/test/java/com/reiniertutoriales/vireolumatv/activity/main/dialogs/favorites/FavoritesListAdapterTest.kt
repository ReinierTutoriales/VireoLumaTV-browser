package com.reiniertutoriales.vireolumatv.activity.main.dialogs.favorites

import android.view.FocusFinder
import android.view.View
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.FavoriteItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.Executor

/** Bookmark rows: open on activation, edit and delete in edit mode, Delete reached with Right. */
@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28], qualifiers = "w960dp-h540dp-mdpi")
class FavoritesListAdapterTest {
    private val opened = mutableListOf<FavoriteItem>()
    private val edited = mutableListOf<FavoriteItem>()
    private val deleted = mutableListOf<FavoriteItem>()

    private fun bookmark(id: Long, title: String?, url: String?) =
        FavoriteItem().apply { this.id = id; this.title = title; this.url = url }

    private fun withList(items: List<FavoriteItem>, test: (RecyclerView, FavoritesListAdapter) -> Unit) {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java)
        controller.get().setTheme(R.style.AppTheme)
        controller.setup()
        try {
            val adapter = FavoritesListAdapter({ opened += it }, { edited += it }, { deleted += it },
                AsyncDifferConfig.Builder(FavoritesListAdapter.DIFF).setBackgroundThreadExecutor(Executor { it.run() }).build())
            val list = RecyclerView(controller.get()).apply {
                layoutManager = LinearLayoutManager(context)
                this.adapter = adapter
            }
            controller.get().setContentView(list)
            val viewRoot = controller.get().window.decorView.parent
            viewRoot.javaClass.getDeclaredMethod("ensureTouchMode", Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(viewRoot, false)
            adapter.submitList(items)
            ShadowLooper.idleMainLooper()
            relayout(list)
            test(list, adapter)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun relayout(list: RecyclerView) {
        ShadowLooper.idleMainLooper()
        list.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY))
        list.layout(0, 0, 960, 540)
    }

    private fun RecyclerView.row(position: Int): View = getChildAt(position)

    @Test fun activatingARowOpensTheBookmarkAndShowsItsTitleOrAddress() {
        val page = bookmark(1, "Ejemplo", "https://example.test")
        val untitled = bookmark(2, " ", "https://untitled.test")
        withList(listOf(page, untitled)) { list, _ ->
            assertEquals("Ejemplo", list.row(0).findViewById<TextView>(R.id.tvTitle).text.toString())
            assertEquals("A blank title falls back to the address", "https://untitled.test",
                list.row(1).findViewById<TextView>(R.id.tvTitle).text.toString())
            assertEquals(View.GONE, list.row(0).findViewById<View>(R.id.ibDelete).visibility)
            list.row(0).findViewById<View>(R.id.llContent).performClick()
            assertEquals(listOf(page), opened)
            assertTrue(edited.isEmpty())
        }
    }

    @Test fun aFolderIsNotOpenedAsAPage() = withList(listOf(bookmark(3, "Carpeta", null))) { list, _ ->
        list.row(0).findViewById<View>(R.id.llContent).performClick()
        assertTrue(opened.isEmpty())
    }

    @Test fun editModeEditsOnActivationAndDeleteIsOneStepRight() {
        val page = bookmark(1, "Ejemplo", "https://example.test")
        withList(listOf(page)) { list, adapter ->
            adapter.editMode = true
            relayout(list)
            val content = list.row(0).findViewById<View>(R.id.llContent)
            val delete = list.row(0).findViewById<View>(R.id.ibDelete)
            assertEquals(View.VISIBLE, delete.visibility)
            assertSame("Delete must be reachable with D-pad Right", delete,
                FocusFinder.getInstance().findNextFocus(list, content, View.FOCUS_RIGHT))
            content.performClick()
            delete.performClick()
            assertEquals(listOf(page), edited)
            assertEquals(listOf(page), deleted)
            assertTrue(opened.isEmpty())
        }
    }
}
