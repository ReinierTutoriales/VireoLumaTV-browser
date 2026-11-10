package com.reiniertutoriales.vireolumatv.activity.main.dialogs.favorites

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import androidx.activity.ComponentDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvControlColors
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvFavicon
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvListRow
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvSymbol
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvSymbolIcon
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvTextButton
import com.reiniertutoriales.vireolumatv.activity.main.compose.VireoTvTheme
import com.reiniertutoriales.vireolumatv.model.Favorite
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import com.reiniertutoriales.vireolumatv.utils.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Favorites as a side panel at the end edge: add the page shown, open a favorite with [onOpen], or
 * in edit mode edit or delete one. Each write is followed by a fresh database snapshot for the rows.
 */
class FavoritesPanel(
    context: Context,
    scope: CoroutineScope,
    private val currentPageTitle: String?,
    private val currentPageUrl: String?,
    private val onOpen: (Favorite) -> Unit,
) : ComponentDialog(context, R.style.SidePanel) {
    private val operations = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
    private var items by mutableStateOf<List<Favorite>?>(null)
    private var editMode by mutableStateOf(false)
    private var busy by mutableStateOf(false)

    init {
        setContentView(ComposeView(context).apply {
            setContent {
                VireoTvTheme {
                    FavoritesContent(items, editMode, busy,
                        onAdd = { edit(Favorite().apply { title = currentPageTitle; url = currentPageUrl }) },
                        onToggleEdit = { editMode = !editMode },
                        onOpen = { dismiss(); onOpen(it) },
                        onEdit = ::edit,
                        onDelete = { item -> refresh { AppDatabase.db.favoriteDao().delete(item) } })
                }
            }
        })
        refresh {}
    }

    override fun onStart() {
        super.onStart()
        window?.apply {
            setGravity(Gravity.END)
            setLayout(context.resources.getDimensionPixelSize(R.dimen.side_panel_wide), ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    private fun edit(item: Favorite) {
        if (busy) return
        editFavorite(context, item) { edited ->
            refresh {
                val favorites = AppDatabase.db.favoriteDao()
                if (edited.id == 0L) favorites.insert(edited) else favorites.update(edited)
            }
        }
    }

    private fun refresh(write: suspend () -> Unit) {
        if (busy) return
        busy = true
        operations.launch(Dispatchers.Main) {
            try {
                write()
                items = AppDatabase.db.favoriteDao().getAll()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                context.toast(R.string.favorites_update_failed)
            } finally {
                busy = false
            }
        }
    }

    override fun dismiss() {
        operations.cancel()
        super.dismiss()
    }
}

/**
 * The panel: title, Add and Edit/Done, then one row per favorite ([items] null while the first
 * read runs). OK opens a favorite, or edits it in [editMode], where Delete is one step Right.
 */
@Composable
internal fun FavoritesContent(
    items: List<Favorite>?,
    editMode: Boolean,
    busy: Boolean,
    onAdd: () -> Unit,
    onToggleEdit: () -> Unit,
    onOpen: (Favorite) -> Unit,
    onEdit: (Favorite) -> Unit,
    onDelete: (Favorite) -> Unit,
) {
    val first = remember { FocusRequester() }
    Column(
        Modifier.semantics { testTagsAsResourceId = true }.fillMaxSize()
            .padding(start = 24.dp, end = dimensionResource(R.dimen.safe_horizontal),
                top = dimensionResource(R.dimen.safe_vertical), bottom = dimensionResource(R.dimen.safe_vertical)),
    ) {
        Text(stringResource(R.string.menu_favorites), color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp,
            modifier = Modifier.padding(start = 16.dp).semantics { heading() })
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TvTextButton(stringResource(R.string.favorites_add), onAdd, Modifier.testTag("favorites_add").focusRequester(first),
                enabled = !busy)
            TvTextButton(stringResource(if (editMode) R.string.action_done else R.string.action_edit), onToggleEdit,
                Modifier.testTag("favorites_edit"), enabled = !busy)
        }
        Box(Modifier.fillMaxWidth().weight(1f).padding(top = 16.dp)) {
            if (items != null && items.isEmpty() && !busy) {
                Text(stringResource(R.string.favorites_empty), color = colorResource(R.color.on_surface_variant), fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp).testTag("favorites_empty"))
            }
            LazyColumn(Modifier.fillMaxSize().testTag("favorites_list")) {
                items(items.orEmpty(), key = { it.id }) { item ->
                    FavoriteRow(item, editMode, onOpen, onEdit, onDelete)
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
private fun FavoriteRow(item: Favorite, editMode: Boolean, onOpen: (Favorite) -> Unit, onEdit: (Favorite) -> Unit,
                        onDelete: (Favorite) -> Unit) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TvListRow(
            // A blank title falls back to the address.
            title = item.title?.takeIf { it.isNotBlank() } ?: item.url.orEmpty(),
            supporting = item.url,
            onClick = { if (editMode) onEdit(item) else if (item.url != null) onOpen(item) },
            modifier = Modifier.weight(1f).testTag("favorite_row_${item.id}"),
            leadingContent = { TvFavicon(item.url, Modifier.size(26.dp)) },
        )
        if (editMode) DeleteButton(Modifier.testTag("favorite_delete_${item.id}")) { onDelete(item) }
    }
}

@Composable
private fun DeleteButton(modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val label = stringResource(R.string.action_delete)
    Box(
        modifier.size(48.dp)
            .background(TvControlColors.surface(focused, hovered), CircleShape)
            .border(if (focused) 2.dp else 1.dp, TvControlColors.border(focused, hovered), CircleShape)
            .semantics { contentDescription = label }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { TvSymbolIcon(TvSymbol.Delete, Modifier.size(24.dp)) }
}
