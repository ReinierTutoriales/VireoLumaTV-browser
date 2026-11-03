package com.reiniertutoriales.vireolumatv.activity.main.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.util.Log
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.main.MainActivity
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import com.reiniertutoriales.vireolumatv.singleton.AppDatabase
import com.reiniertutoriales.vireolumatv.singleton.Favicons
import com.reiniertutoriales.vireolumatv.utils.toast
import com.reiniertutoriales.vireolumatv.webengine.webview.HomePage
import com.reiniertutoriales.vireolumatv.webengine.webview.safeHomeAssetPath
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Native UI over the internal history document. Only its existing, isolated localStorage is used. */
internal class NativeHome(private val browser: MainActivity, private val host: FrameLayout) {
    private var tab: BrowserTab? = null
    private var web: WebView? = null
    private var root: View? = null
    private var grid: RecyclerView? = null
    private var search: AppCompatEditText? = null
    private var adapter: Tiles? = null
    private var favoritesJob: Job? = null
    private var dialog: AlertDialog? = null
    private var generation = 0
    private var loaded = false
    private var busy = false
    private var editing = false
    private var shortcuts = HomeShortcuts()
    private var favorites: List<Tile> = emptyList()
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
        val content = LayoutInflater.from(browser).inflate(R.layout.native_home, host, false)
        root = content
        host.addView(content)
        host.isVisible = true
        search = content.findViewById<AppCompatEditText>(R.id.home_search).apply {
            showSoftInputOnFocus = false
            setOnClickListener { showKeyboard(this) }
            setOnKeyListener { _, code, event ->
                when {
                    code == KeyEvent.KEYCODE_DPAD_UP -> {
                        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) browser.openCapsule()
                        true
                    }
                    code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER -> {
                        if (event.action == KeyEvent.ACTION_UP) showKeyboard(this)
                        true
                    }
                    else -> false
                }
            }
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEARCH) { submitSearch(); true } else false
            }
        }
        content.findViewById<View>(R.id.home_submit).setOnClickListener { submitSearch() }
        grid = content.findViewById<RecyclerView>(R.id.home_tiles).apply {
            layoutManager = GridLayoutManager(browser, 4)
            itemAnimator = null
            adapter = Tiles().also { this@NativeHome.adapter = it }
            addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
                val manager = layoutManager as GridLayoutManager
                val columns = ((right - left) / dp(180)).coerceIn(2, 6)
                if (manager.spanCount != columns) manager.spanCount = columns
            }
        }
        render()
        // No normal bookmarks are queried by the private process, and no work survives hiding Home.
        if (!browser.config.privateBrowsing) favoritesJob = browser.lifecycleScope.launch {
            AppDatabase.db.favoriteDao().observeAll().catch { error ->
                Log.w("NativeHome", "Home favorites unavailable", error)
                emit(emptyList())
            }.collect { values ->
                favorites = values.asReversed().mapNotNull { value ->
                    value.url?.let { HomeShortcuts.address(it) }?.let { url ->
                        Tile("favorite:$url", value.title.orEmpty().trim().take(200).ifEmpty { label(url) }, url, R.drawable.ic_bookmark)
                    }
                }.distinctBy { it.url }.take(48)
                render()
            }
        }
        documentReady(shown)
    }

    /** Re-attempt a storage read after a new internal document has actually committed. */
    fun documentReady(shown: BrowserTab) {
        if (shown !== tab || loaded || busy || (web?.progress ?: 0) < 100) return
        storage("") { state -> shortcuts = state; loaded = true; render() }
    }

    fun hide() {
        generation++
        dialog?.dismiss()
        dialog = null
        favoritesJob?.cancel()
        favoritesJob = null
        web?.visibility = View.VISIBLE
        if (root != null) browser.vb.pageContainer.cursorSuppressed = browser.vb.controlsOverlay.isVisible
        host.removeAllViews()
        host.isVisible = false
        root = null
        grid = null
        search = null
        adapter = null
        tab = null
        web = null
        favorites = emptyList()
        shortcuts = HomeShortcuts()
        loaded = false
        busy = false
        editing = false
    }

    fun focusContent(): Boolean = search?.requestFocus() == true

    /** The same 480 px budget as web previews, without capturing a blank underlying document. */
    fun preview(reuse: Bitmap?): Bitmap? {
        val view = root ?: return null
        if (view.width <= 0 || view.height <= 0) return null
        val scale = minOf(1f, 480f / maxOf(view.width, view.height))
        val width = maxOf(1, (view.width * scale).toInt())
        val height = maxOf(1, (view.height * scale).toInt())
        val bitmap = reuse?.takeIf { !it.isRecycled && it.width == width && it.height == height }
            ?: try { Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565) } catch (_: OutOfMemoryError) { return null }
        view.draw(Canvas(bitmap).apply { scale(scale, scale) })
        return bitmap
    }

    private fun showKeyboard(field: AppCompatEditText) {
        field.requestFocus()
        browser.getSystemService<InputMethodManager>()?.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun submitSearch() {
        val field = search ?: return
        val url = HomeShortcuts.search(field.text.toString())
        if (url == null) field.error = browser.getString(R.string.home_empty_search) else open(url)
    }

    private fun open(url: String) {
        if (browser.tabStore.currentTab.value !== tab) return
        search?.let { browser.getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(it.windowToken, 0) }
        // Remove the overlay synchronously; the external document is rendered by the same engine.
        hide()
        browser.navigate(url)
        browser.closeCapsule()
    }

    private data class Tile(val key: String, val title: String, val url: String? = null, val icon: Int,
        val enabled: Boolean = true, val selected: Boolean = false, val hidden: Boolean = false)

    private fun render() {
        val active = loaded && !busy
        val items = mutableListOf<Tile>()
        if (!shortcuts.youtubeHidden || editing) items += Tile("youtube", browser.getString(R.string.home_video_brand), HomeShortcuts.YOUTUBE,
            R.drawable.ic_video, !editing || active, hidden = shortcuts.youtubeHidden)
        items += favorites
        val used = favorites.mapNotNull { it.url }.toSet()
        shortcuts.urls.filter { it !in used }.forEach { url ->
            items += Tile("shortcut:$url", label(url), url, R.drawable.ic_globe, !editing || active)
        }
        items += Tile("add", browser.getString(R.string.home_add), icon = R.drawable.ic_add, enabled = active)
        items += Tile("edit", browser.getString(if (editing) R.string.home_done else R.string.home_edit),
            icon = if (editing) R.drawable.ic_done else R.drawable.ic_edit, enabled = active, selected = editing)
        val focused = grid?.findFocus()
        val key = focused?.tag as? String
        val oldIndex = adapter?.currentList?.indexOfFirst { it.key == key } ?: -1
        adapter?.submitList(items) {
            if (key == null || browser.vb.controlsOverlay.isVisible || dialog?.isShowing == true) return@submitList
            val current = root?.findFocus()
            if (current != null && current !== focused) return@submitList
            val index = items.indexOfFirst { it.key == key }.takeIf { it >= 0 } ?: oldIndex.coerceIn(0, items.lastIndex)
            grid?.findViewHolderForAdapterPosition(index)?.itemView?.requestFocus()
        }
    }

    private fun click(tile: Tile) {
        when (tile.key) {
            "add" -> if (shortcuts.urls.size >= HomeShortcuts.MAX) notice(browser.getString(R.string.home_limit, HomeShortcuts.MAX)) else edit(null)
            "edit" -> { editing = !editing; render() }
            "youtube" -> if (!editing) open(HomeShortcuts.YOUTUBE) else {
                dialog = AlertDialog.Builder(browser)
                    .setMessage(if (shortcuts.youtubeHidden) R.string.home_youtube_restore else R.string.home_youtube_remove)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        val value = if (shortcuts.youtubeHidden) "localStorage.removeItem(${JSONObject.quote(HomeShortcuts.HIDDEN_KEY)});" else
                            "localStorage.setItem(${JSONObject.quote(HomeShortcuts.HIDDEN_KEY)},'1');"
                        storage(value) { shortcuts = it; render() }
                    }.show()
            }
            else -> if (!editing) tile.url?.let(::open) else if (tile.key.startsWith("favorite:")) {
                notice(browser.getString(R.string.home_favorite_managed, browser.getString(R.string.menu_favorites)))
            } else edit(tile.url)
        }
    }

    private fun edit(old: String?) {
        val input = AppCompatEditText(browser).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setText(old.orEmpty())
            setSelection(text?.length ?: 0)
        }
        val popup = AlertDialog.Builder(browser).setMessage(if (old == null) R.string.home_add_prompt else R.string.home_edit_prompt)
            .setView(input).setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok, null).create()
        dialog = popup
        popup.setOnShowListener {
            popup.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = input.text.toString().trim()
                if (text.isEmpty() && old != null) {
                    popup.dismiss()
                    dialog = AlertDialog.Builder(browser).setMessage(R.string.home_remove_confirm)
                        .setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok) { _, _ ->
                            save(shortcuts.urls - old)
                        }.show()
                    return@setOnClickListener
                }
                val url = HomeShortcuts.address(text)
                when {
                    url == null -> input.error = browser.getString(R.string.home_invalid)
                    url != old && (url in shortcuts.urls || favorites.any { it.url == url }) -> input.error = browser.getString(R.string.home_duplicate)
                    else -> {
                        popup.dismiss()
                        if (HomeShortcuts.isYoutube(url)) {
                            storage("localStorage.removeItem(${JSONObject.quote(HomeShortcuts.HIDDEN_KEY)});") { shortcuts = it; render() }
                        } else save(if (old == null) shortcuts.urls + url else shortcuts.urls.map { if (it == old) url else it })
                    }
                }
            }
        }
        popup.show()
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
            return {raw:localStorage.getItem(${JSONObject.quote(HomeShortcuts.KEY)}),hidden:localStorage.getItem(${JSONObject.quote(HomeShortcuts.HIDDEN_KEY)})==='1'};
            }catch(e){return null;}})()""".trimIndent()
        view.evaluateJavascript(script) { result ->
            if (visit != generation || view !== web || view.url != address || browser.tabStore.currentTab.value !== tab) return@evaluateJavascript
            busy = false
            val data = runCatching { JSONObject(result) }.getOrNull()
            if (data != null) accept(HomeShortcuts.read(data.opt("raw") as? String, data.optBoolean("hidden")))
            else {
                render()
                if (write.isNotEmpty()) notice(browser.getString(R.string.home_save_failed))
            }
        }
    }

    private fun notice(text: String) = browser.toast(text)
    private fun label(url: String) = android.net.Uri.parse(url).host ?: url
    private fun dp(value: Int) = (value * browser.resources.displayMetrics.density).toInt()

    private inner class Tiles : ListAdapter<Tile, Holder>(object : DiffUtil.ItemCallback<Tile>() {
        override fun areItemsTheSame(oldItem: Tile, newItem: Tile) = oldItem.key == newItem.key
        override fun areContentsTheSame(oldItem: Tile, newItem: Tile) = oldItem == newItem
    }) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val button = LayoutInflater.from(parent.context).inflate(R.layout.native_home_tile, parent, false) as AppCompatButton
            return Holder(button)
        }
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val tile = getItem(position)
            val button = holder.itemView as AppCompatButton
            button.text = tile.title
            button.tag = tile.key
            button.isEnabled = tile.enabled
            button.isSelected = tile.selected
            button.alpha = if (tile.hidden) .5f else 1f
            button.contentDescription = if (tile.hidden) browser.getString(R.string.home_youtube_removed)
                else if (editing && tile.key.startsWith("shortcut:"))
                browser.getString(R.string.home_edit_item).replace("%s", tile.title) else tile.title
            val cached = tile.url?.let { android.net.Uri.parse(it).host }?.let(Favicons::cached)
            val icon = cached?.let { BitmapDrawable(browser.resources, it) }
                ?: ContextCompat.getDrawable(browser, tile.icon)?.mutate()?.apply {
                    setTintList(ContextCompat.getColorStateList(browser, R.color.on_control))
                }
            icon?.setBounds(0, 0, dp(28), dp(28))
            button.setCompoundDrawablesRelative(null, icon, null, null)
            button.setOnClickListener { click(tile) }
        }
    }
    private class Holder(view: View) : RecyclerView.ViewHolder(view)
}
