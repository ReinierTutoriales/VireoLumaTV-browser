package com.reiniertutoriales.vireolumatv.activity.main.home

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.main.MainActivity
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvControlColors
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvDialog
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvSymbol
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvSymbolIcon
import com.reiniertutoriales.vireolumatv.activity.main.compose.VireoTvTheme
import com.reiniertutoriales.vireolumatv.activity.main.dialogs.favorites.TabFavorites
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import com.reiniertutoriales.vireolumatv.model.Favorite
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import com.reiniertutoriales.vireolumatv.singleton.Favicons
import com.reiniertutoriales.vireolumatv.utils.toast
import com.reiniertutoriales.vireolumatv.webengine.webview.HomePage
import com.reiniertutoriales.vireolumatv.webengine.webview.safeHomeAssetPath
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Native Home in Compose over the internal history document; only that document's existing,
 * isolated localStorage is used. Focus never activates anything: OK on the search field opens the
 * keyboard, Up from it opens the capsule, and tiles keep their focus by key across updates.
 */
internal class NativeHome(private val browser: MainActivity, private val host: FrameLayout) {
    private var tab: BrowserTab? = null
    private var web: WebView? = null
    private var root: ComposeView? = null
    private var favoritesJob: Job? = null
    private var dialog: Dialog? = null
    private var generation = 0
    private var loaded = false
    private var busy = false
    private var readRetries = 0
    /**
     * The first read can be sent while a restored document is still replacing the previous one;
     * WebView then never answers it. Until a read succeeds, retry so Add and Edit cannot stay disabled.
     */
    private val retryRead = Runnable {
        val shown = tab ?: return@Runnable
        if (loaded || root == null) return@Runnable
        readRetries++
        if (busy) {
            // Ignore the unanswered read if it ever arrives.
            generation++
            busy = false
        }
        documentReady(shown)
    }
    private var shortcuts = HomeShortcuts()
    private var favorites: List<Tile> = emptyList()
    private var searchFocus = FocusRequester()
    private var submitFocus = FocusRequester()
    /** A focus request made before the first composition; applied once the field exists. */
    private var pendingFocus = false

    private var editing by mutableStateOf(false)
    private var tiles by mutableStateOf<List<Tile>>(emptyList())
    private var query by mutableStateOf(TextFieldValue(""))
    private var searchError by mutableStateOf<String?>(null)

    val isVisible get() = root != null

    fun update(shown: BrowserTab?) {
        val view = shown?.engine?.view as? WebView
        if (shown == null || view == null || safeHomeAssetPath(shown.url) != HomePage.FILE || browser.isFullscreen) {
            hide()
            return
        }
        if (shown === tab && view === web) return
        hide()
        tab = shown
        web = view
        view.visibility = View.INVISIBLE
        browser.vb.pageContainer.cursorSuppressed = true
        searchFocus = FocusRequester()
        submitFocus = FocusRequester()
        val content = ComposeView(host.context).apply {
            setContent { VireoTvTheme { HomeContent() } }
        }
        root = content
        host.addView(content)
        host.visibility = browser.vb.pageContainer.visibility
        followCapsule(animate = false)
        render()
        // Favorites are shared in both modes, as before; the observer ends when Home is hidden.
        favoritesJob = browser.lifecycleScope.launch {
            AppDatabase.db.favoriteDao().observeAll().catch { error ->
                Log.w("NativeHome", "Home favorites unavailable", error)
                emit(emptyList())
            }.collect { values ->
                favorites = values.asReversed().asSequence().mapNotNull { value ->
                    TabFavorites.webAddress(value.url)?.let { HomeShortcuts.address(it) }?.let { url ->
                        Tile("favorite:$url", value.title.orEmpty().trim().take(200).ifEmpty { label(url) }, url, TvSymbol.Bookmark,
                            favorite = value)
                    }
                }.distinctBy { it.url }.take(48).toList()
                render()
            }
        }
        documentReady(shown)
    }

    /** Re-attempt a storage read after a new internal document has actually committed. */
    fun documentReady(shown: BrowserTab) {
        if (shown !== tab || loaded) return
        if (!busy && (web?.progress ?: 0) >= 100) storage("") { state -> shortcuts = state; loaded = true; render() }
        host.removeCallbacks(retryRead)
        if (!loaded && readRetries < READ_RETRIES) host.postDelayed(retryRead, READ_RETRY_MS)
    }

    fun hide() {
        if (root == null) return
        generation++
        host.removeCallbacks(retryRead)
        readRetries = 0
        dialog?.dismiss()
        dialog = null
        favoritesJob?.cancel()
        favoritesJob = null
        web?.visibility = View.VISIBLE
        browser.vb.pageContainer.cursorSuppressed = browser.vb.controlsOverlay.isVisible
        host.removeAllViews()
        host.isVisible = false
        root = null
        tab = null
        web = null
        favorites = emptyList()
        shortcuts = HomeShortcuts()
        loaded = false
        busy = false
        editing = false
        pendingFocus = false
        tiles = emptyList()
        query = TextFieldValue("")
        searchError = null
    }

    /** Back inside Home: leaves tile editing; false when Home has nothing of its own to close. */
    fun consumeBack(): Boolean {
        if (root == null || !editing) return false
        editing = false
        render()
        return true
    }

    /**
     * Moves Home below the open capsule, so the address row and tabs never cover its title and
     * search field; back in place when the capsule closes.
     */
    fun followCapsule(animate: Boolean) {
        val view = root ?: return
        val overlay = browser.vb.controlsOverlay
        val top = browser.resources.getDimensionPixelSize(R.dimen.safe_vertical)
        val offset = if (overlay.isVisible && overlay.height > 0) {
            (overlay.height - overlay.paddingBottom - top).coerceAtLeast(0).toFloat()
        } else 0f
        // A repeated focus/capsule event must not restart the same transition.
        if (kotlin.math.abs(view.translationY - offset) < 0.5f) return
        view.animate().cancel()
        if (animate && (android.os.Build.VERSION.SDK_INT < 26 || android.animation.ValueAnimator.areAnimatorsEnabled())) {
            view.animate().translationY(offset).setDuration(CAPSULE_MS)
                .setInterpolator(AnimationUtils.loadInterpolator(browser, android.R.interpolator.fast_out_slow_in))
                .start()
        } else view.translationY = offset
    }

    fun setPageVisible(visible: Boolean) {
        if (root != null) host.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    private fun closeCapsuleIfVisible() {
        if (browser.vb.controlsOverlay.isVisible) browser.closeCapsule()
    }

    /**
     * Focuses the search field. Before the first composition the request waits for it; while Home
     * is being shown again in the same frame (after the capsule closes) it is retried once laid out.
     */
    fun focusContent(): Boolean {
        val view = root ?: return false
        // Take the window focus from the capsule's host first; Compose then places it on the field.
        view.requestFocus()
        val done = runCatching { searchFocus.requestFocus(FocusDirection.Enter) }
        if (done.isFailure) pendingFocus = true
        else if (done.getOrNull() == false) view.post { if (root === view) runCatching { searchFocus.requestFocus(FocusDirection.Enter) } }
        return true
    }

    /** The same 480 px budget as web previews, without capturing a blank underlying document. */
    fun preview(reuse: Bitmap?): Bitmap? {
        val view = root ?: return null
        if (view.width <= 0 || view.height <= 0) return null
        val scale = minOf(1f, 480f / maxOf(view.width, view.height))
        val width = maxOf(1, (view.width * scale).toInt())
        val height = maxOf(1, (view.height * scale).toInt())
        val bitmap = reuse?.takeIf { !it.isRecycled && it.width == width && it.height == height }
            ?: try { createBitmap(width, height, Bitmap.Config.RGB_565) } catch (_: OutOfMemoryError) { return null }
        synchronized(bitmap) {
            view.draw(Canvas(bitmap).apply { scale(scale, scale) })
        }
        return bitmap
    }

    private fun submitSearch() {
        closeCapsuleIfVisible()
        val url = HomeShortcuts.search(query.text)
        if (url == null) searchError = browser.getString(R.string.home_empty_search) else open(url)
    }

    private fun open(url: String) {
        if (browser.tabStore.currentTab.value !== tab) return
        // Remove the overlay synchronously; the external document is rendered by the same engine.
        hide()
        browser.navigate(url)
        browser.closeCapsule()
    }

    internal data class Tile(val key: String, val title: String, val url: String? = null, val icon: TvSymbol,
        val enabled: Boolean = true, val selected: Boolean = false, val hidden: Boolean = false,
        val favorite: Favorite? = null) {
        /** YouTube, favorites and shortcuts can be arranged and removed; Add and Edit stay last. */
        val movable get() = key != "add" && key != "edit"
    }

    private fun render() {
        val active = loaded && !busy
        val movable = mutableListOf<Tile>()
        if (!shortcuts.youtubeHidden || editing) movable += Tile("youtube", browser.getString(R.string.home_video_brand), HomeShortcuts.YOUTUBE,
            TvSymbol.Video, !editing || active, hidden = shortcuts.youtubeHidden)
        movable += favorites.map { it.copy(enabled = !editing || active) }
        val used = favorites.mapNotNull { it.url }.toSet()
        shortcuts.urls.filter { it !in used }.forEach { url ->
            movable += Tile("shortcut:$url", label(url), url, TvSymbol.Globe, !editing || active)
        }
        val items = shortcuts.arrange(movable) { it.key }.toMutableList()
        items += Tile("add", browser.getString(R.string.home_add), icon = TvSymbol.Add, enabled = active)
        items += Tile("edit", browser.getString(if (editing) R.string.home_done else R.string.home_edit),
            icon = if (editing) TvSymbol.Done else TvSymbol.Edit, enabled = active, selected = editing)
        tiles = items
    }

    /** The keys of the tiles shown, in order; for tests and diagnostics. */
    internal val tileKeys: List<String> get() = tiles.map { it.key }

    private fun click(tile: Tile) {
        closeCapsuleIfVisible()
        // A tile waiting for storage keeps its focus but ignores OK, so the D-pad never jumps.
        if (!tile.enabled) return
        when (tile.key) {
            "add" -> if (shortcuts.urls.size >= HomeShortcuts.MAX) notice(browser.getString(R.string.home_limit, HomeShortcuts.MAX)) else edit(null)
            "edit" -> { editing = !editing; render() }
            "youtube" -> if (!editing) open(HomeShortcuts.YOUTUBE) else if (shortcuts.youtubeHidden) toggleYoutube() else actions(tile)
            else -> if (!editing) tile.url?.let(::open) else actions(tile)
        }
    }

    /** What OK on a tile offers while editing: move it, change its address, or take it off Home. */
    private fun actions(tile: Tile) {
        val list = tiles.filter { it.movable }
        val index = list.indexOfFirst { it.key == tile.key }
        if (index < 0) return
        // In right-to-left layouts the first tile is drawn on the right.
        val rtl = host.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val choices = buildList<Pair<Int, () -> Unit>> {
            val left = if (rtl) 1 else -1
            if (index + left in list.indices) add(R.string.home_move_left to { move(list, index, index + left) })
            if (index - left in list.indices) add(R.string.home_move_right to { move(list, index, index - left) })
            if (tile.key.startsWith("shortcut:")) add(R.string.home_edit_address to { edit(tile.url) })
            add(R.string.home_remove to { removeTile(tile) })
        }
        dialog = TvDialog.Builder(browser).setTitle(tile.title)
            .setItems(choices.map { browser.getString(it.first) }.toTypedArray()) { _, which -> choices[which].second() }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    /** Saves the whole arrangement, so favorites and shortcuts keep their places across visits. */
    private fun move(list: List<Tile>, from: Int, to: Int) {
        val keys = list.map { it.key }.toMutableList()
        keys.add(to, keys.removeAt(from))
        val value = JSONArray(keys).toString()
        storage("localStorage.setItem(${JSONObject.quote(HomeShortcuts.ORDER_KEY)},${JSONObject.quote(value)});") {
            shortcuts = it
            render()
        }
    }

    private fun confirm(message: Int, onConfirmed: () -> Unit) {
        dialog = TvDialog.Builder(browser).setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ -> onConfirmed() }.show()
    }

    private fun removeTile(tile: Tile) {
        val favorite = tile.favorite
        when {
            tile.key == "youtube" -> toggleYoutube()
            // The list follows the database observer; Favorites shows the same change.
            favorite != null -> confirm(R.string.home_favorite_remove_confirm) {
                browser.lifecycleScope.launch { AppDatabase.db.favoriteDao().delete(favorite) }
            }
            else -> confirm(R.string.home_remove_confirm) { tile.url?.let { save(shortcuts.urls - it) } }
        }
    }

    private fun toggleYoutube() {
        confirm(if (shortcuts.youtubeHidden) R.string.home_youtube_restore else R.string.home_youtube_remove) {
            val value = if (shortcuts.youtubeHidden) "localStorage.removeItem(${JSONObject.quote(HomeShortcuts.HIDDEN_KEY)});" else
                "localStorage.setItem(${JSONObject.quote(HomeShortcuts.HIDDEN_KEY)},'1');"
            storage(value) { shortcuts = it; render() }
        }
    }

    /** Adds a shortcut ([old] null) or changes one; an empty address offers to remove it. */
    private fun edit(old: String?) {
        dialog = TvDialog.Builder(browser)
            .setMessage(if (old == null) R.string.home_add_prompt else R.string.home_edit_prompt)
            .setFields(TvDialog.Field(R.string.field_address, uri = true))
            .setValidator { editor ->
                val text = editor.fieldValue(0).trim()
                if (text.isEmpty() && old != null) return@setValidator null
                val url = HomeShortcuts.address(text)
                when {
                    url == null -> browser.getString(R.string.home_invalid)
                    url != old && url in shortcuts.urls -> browser.getString(R.string.home_duplicate)
                    else -> null
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { editor, _ ->
                val text = (editor as TvDialog).fieldValue(0).trim()
                if (text.isEmpty() && old != null) {
                    // Shown after this editor closes, so the confirmation is the dialog kept.
                    host.post { confirm(R.string.home_remove_confirm) { save(shortcuts.urls - old) } }
                    return@setPositiveButton
                }
                val url = HomeShortcuts.address(text) ?: return@setPositiveButton
                if (old == null && HomeShortcuts.isYoutube(url)) {
                    storage("localStorage.removeItem(${JSONObject.quote(HomeShortcuts.HIDDEN_KEY)});") { shortcuts = it; render() }
                } else save(if (old == null) shortcuts.urls + url else shortcuts.urls.map { if (it == old) url else it })
            }
            .create()
            .apply { setFieldValue(0, old.orEmpty()) }
            .also { it.show() }
    }

    private fun save(urls: List<String>) {
        val value = JSONArray(urls).toString()
        storage("localStorage.setItem(${JSONObject.quote(HomeShortcuts.KEY)},${JSONObject.quote(value)});") { shortcuts = it; render() }
    }

    /** No bridge is exposed to sites. Both execution and the callback are bound to this exact Home visit. */
    private fun storage(write: String, accept: (HomeShortcuts) -> Unit) {
        val view = web ?: return
        val address = view.url ?: return
        if (busy || safeHomeAssetPath(address) != HomePage.FILE) return
        val visit = generation
        busy = true
        render()
        val script = """(function(){if(location.href!==${JSONObject.quote(address)})return null;try{$write
            return {raw:localStorage.getItem(${JSONObject.quote(HomeShortcuts.KEY)}),hidden:localStorage.getItem(${JSONObject.quote(HomeShortcuts.HIDDEN_KEY)})==='1',
            order:localStorage.getItem(${JSONObject.quote(HomeShortcuts.ORDER_KEY)})};
            }catch(e){return null;}})()""".trimIndent()
        view.evaluateJavascript(script) { result ->
            if (visit != generation || view !== web || view.url != address || browser.tabStore.currentTab.value !== tab) return@evaluateJavascript
            busy = false
            val data = runCatching { JSONObject(result) }.getOrNull()
            if (data != null) accept(HomeShortcuts.read(data.opt("raw") as? String, data.optBoolean("hidden"), data.opt("order") as? String))
            else {
                render()
                if (write.isNotEmpty()) notice(browser.getString(R.string.home_save_failed))
            }
        }
    }

    private fun notice(text: String) = browser.toast(text)
    private fun label(url: String) = url.toUri().host ?: url

    @Composable
    private fun HomeContent() {
        // The background closes the capsule on a click but is never a keyboard focus target.
        Column(
            Modifier.semantics { testTagsAsResourceId = true }.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) { detectTapGestures { closeCapsuleIfVisible() } }
                .padding(horizontal = dimensionResource(R.dimen.safe_horizontal), vertical = dimensionResource(R.dimen.safe_vertical)),
        ) {
            Text(stringResource(R.string.home_search_brand), color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SearchField(Modifier.weight(1f))
                SubmitButton()
            }
            searchError?.let {
                Text(it, color = ErrorColor, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp).testTag("home_search_error"))
            }
            Text(stringResource(R.string.home_shortcuts), color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
            TileGrid(Modifier.weight(1f))
        }
        LaunchedEffect(Unit) {
            if (pendingFocus && !browser.vb.controlsOverlay.isVisible) runCatching { searchFocus.requestFocus() }
            pendingFocus = false
        }
    }

    /**
     * Shows the query until OK; then edits it with the keyboard. Up opens the capsule; at the end of
     * the text the key toward Submit moves to it; the keyboard's Search runs the search.
     */
    @Composable
    private fun SearchField(modifier: Modifier) {
        var typing by remember { mutableStateOf(false) }
        val keyboard = LocalSoftwareKeyboardController.current
        val towardSubmit = if (LocalLayoutDirection.current == LayoutDirection.Rtl) Key.DirectionLeft else Key.DirectionRight
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        val hovered by interaction.collectIsHoveredAsState()
        val shape = RoundedCornerShape(12.dp)
        val hint = stringResource(R.string.home_search)
        val keys = Modifier.onPreviewKeyEvent { event ->
            when (event.key) {
                Key.DirectionUp -> {
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) browser.openCapsule()
                    true
                }
                // Some Android versions consume it at the end of a single-line field.
                towardSubmit -> (typing && query.selection.collapsed && query.selection.end == query.text.length).also { atEnd ->
                    if (atEnd && event.type == KeyEventType.KeyDown) {
                        typing = false
                        runCatching { submitFocus.requestFocus() }
                    }
                }
                else -> false
            }
        }
        val frame = modifier.testTag("home_page_search").heightIn(min = 56.dp)
            .background(TvControlColors.surface(focused || typing, hovered), shape)
            .border(if (focused || typing) 2.dp else 1.dp, TvControlColors.border(focused || typing, hovered), shape)
        if (typing) {
            var hadFocus by remember { mutableStateOf(false) }
            BasicTextField(
                value = query,
                onValueChange = { query = it; searchError = null },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                interactionSource = interaction,
                modifier = frame.focusRequester(searchFocus).then(keys)
                    .onFocusChanged { if (it.isFocused) hadFocus = true else if (hadFocus) typing = false }
                    .semantics { contentDescription = hint }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            )
            LaunchedEffect(Unit) {
                runCatching { searchFocus.requestFocus() }
                keyboard?.show()
            }
        } else {
            Box(
                frame.focusRequester(searchFocus).then(keys)
                    .semantics { contentDescription = hint }
                    .clickable(interactionSource = interaction, indication = null) {
                        closeCapsuleIfVisible()
                        query = query.copy(selection = TextRange(query.text.length))
                        typing = true
                    }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(query.text.ifEmpty { hint }, color = if (query.text.isEmpty()) Color.Gray else Color.White,
                    fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    @Composable
    private fun SubmitButton() {
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        val hovered by interaction.collectIsHoveredAsState()
        val shape = RoundedCornerShape(28.dp)
        val label = stringResource(R.string.home_search)
        Box(
            Modifier.testTag("home_submit").focusRequester(submitFocus).size(56.dp)
                .background(TvControlColors.surface(focused, hovered), shape)
                .border(if (focused) 2.dp else 1.dp, TvControlColors.border(focused, hovered), shape)
                .semantics { contentDescription = label }
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = ::submitSearch),
            contentAlignment = Alignment.Center,
        ) { TvSymbolIcon(TvSymbol.Search, Modifier.size(28.dp)) }
    }

    /**
     * Columns keep one tile width whatever the count; a row that does not fill the width is
     * centred instead of hanging from the start edge.
     */
    @Composable
    private fun TileGrid(modifier: Modifier) {
        val minTile = dimensionResource(R.dimen.home_tile_min_width)
        BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            val fit = (maxWidth / minTile).toInt().coerceIn(2, 6)
            val columns = tiles.size.coerceIn(1, fit)
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.width(maxWidth / fit * columns).testTag("home_tiles"),
                contentPadding = PaddingValues(8.dp),
            ) {
                items(tiles, key = { it.key }) { tile -> HomeTile(tile) }
            }
        }
    }

    @Composable
    private fun HomeTile(tile: Tile) {
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        val hovered by interaction.collectIsHoveredAsState()
        val shape = RoundedCornerShape(16.dp)
        val description = when {
            tile.hidden -> stringResource(R.string.home_youtube_removed)
            editing && tile.movable -> stringResource(R.string.home_edit_item).replace("%s", tile.title)
            else -> tile.title
        }
        val favicon = tile.url?.let { it.toUri().host }?.let(Favicons::cached)
        Column(
            Modifier.testTag("home_tile_${tile.key}").padding(6.dp).fillMaxWidth()
                .alpha(if (tile.hidden || !tile.enabled) .5f else 1f)
                .background(if (tile.selected && !focused) TvControlColors.Selected else TvControlColors.surface(focused, hovered), shape)
                .border(if (focused) 2.dp else 1.dp, TvControlColors.border(focused, hovered), shape)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button) { click(tile) }
                // Reported as disabled while waiting for storage, but still focusable (see click).
                .semantics { contentDescription = description; if (!tile.enabled) disabled() }
                .padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (favicon != null) Image(favicon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(28.dp))
            else TvSymbolIcon(tile.icon, Modifier.size(28.dp))
            Text(tile.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    private companion object {
        /** The capsule's own opening time, so Home moves with it. */
        const val CAPSULE_MS = 220L
        /** Longer than a normal read on slow TV chips, so a slow answer is not discarded; 20 s in all. */
        const val READ_RETRY_MS = 2_000L
        const val READ_RETRIES = 10
        /** Readable on black (contrast above 4.5:1). */
        val ErrorColor = Color(0xFFFF8A80)
    }
}
