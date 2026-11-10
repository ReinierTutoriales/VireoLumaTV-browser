package com.reiniertutoriales.vireolumatv.activity.main.dialogs.favorites

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.platform.app.InstrumentationRegistry
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.main.compose.VireoTvTheme
import com.reiniertutoriales.vireolumatv.model.Favorite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Favorite rows: open on activation, edit and delete in edit mode, Delete reached with Right. */
@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28], qualifiers = "w960dp-h540dp-mdpi")
class FavoritesPanelTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val opened = mutableListOf<Favorite>()
    private val edited = mutableListOf<Favorite>()
    private val deleted = mutableListOf<Favorite>()
    private var editMode by mutableStateOf(false)

    @Before fun remote() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    private fun bookmark(id: Long, title: String?, url: String?) =
        Favorite().apply { this.id = id; this.title = title; this.url = url }

    private fun show(items: List<Favorite>?) = compose.setContent {
        VireoTvTheme {
            FavoritesContent(items, editMode, busy = false, onAdd = {}, onToggleEdit = { editMode = !editMode },
                onOpen = { opened += it }, onEdit = { edited += it }, onDelete = { deleted += it })
        }
    }

    @Test fun activatingARowOpensTheFavoriteAndShowsItsTitleOrAddress() {
        val page = bookmark(1, "Ejemplo", "https://example.test")
        val untitled = bookmark(2, " ", "https://untitled.test")
        show(listOf(page, untitled))
        compose.onNodeWithTag("favorites_add").assertIsFocused()
        compose.onNodeWithTag("favorite_row_1").assertTextContains("Ejemplo")
        // A blank title falls back to the address.
        compose.onNodeWithTag("favorite_row_2").assertTextContains("https://untitled.test")
        compose.onNodeWithTag("favorite_delete_1").assertDoesNotExist()
        compose.onNodeWithTag("favorite_row_1").performClick()
        assertEquals(listOf(page), opened)
        assertTrue(edited.isEmpty())
    }

    @Test fun aFavoriteWithoutAddressIsNotOpened() {
        show(listOf(bookmark(3, "Sin dirección", null)))
        compose.onNodeWithTag("favorite_row_3").performClick()
        assertTrue(opened.isEmpty())
    }

    @Test fun anEmptyListSaysSoOnceRead() {
        show(emptyList())
        compose.onNodeWithTag("favorites_empty").assertIsDisplayed()
    }

    @Test fun editModeEditsOnActivationAndDeleteIsOneStepRight() {
        val page = bookmark(1, "Ejemplo", "https://example.test")
        show(listOf(page))
        compose.onNodeWithTag("favorites_edit").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("favorite_delete_1").assertIsDisplayed()
        compose.onNodeWithTag("favorite_row_1").requestFocus().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("favorite_delete_1").assertIsFocused()
        compose.onNodeWithTag("favorite_row_1").performClick()
        compose.onNodeWithTag("favorite_delete_1").performClick()
        assertEquals(listOf(page), edited)
        assertEquals(listOf(page), deleted)
        assertTrue(opened.isEmpty())
    }
}
