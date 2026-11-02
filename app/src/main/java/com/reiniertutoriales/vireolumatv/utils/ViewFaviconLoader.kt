package com.reiniertutoriales.vireolumatv.utils

import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.singleton.Favicons
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Only visible, still-bound rows may request or display a site icon. */
class ViewFaviconLoader(private val view: View, private val image: ImageView) : View.OnAttachStateChangeListener {
    private var url: String? = null
    private var job: Job? = null
    private var generation = 0

    init { view.addOnAttachStateChangeListener(this) }

    fun bind(url: String?) {
        job?.cancel()
        generation++
        this.url = url
        showPlaceholder()
        if (view.isAttachedToWindow) load()
    }

    fun clear() {
        bind(null)
    }

    private fun load() {
        val requestedUrl = url ?: return
        if (isHome(requestedUrl)) return
        val activity = view.activity as? AppCompatActivity ?: return
        val request = generation
        job?.cancel()
        job = activity.lifecycleScope.launch {
            val bitmap = Favicons.get(requestedUrl)
            if (request == generation && view.isAttachedToWindow && bitmap != null) {
                image.imageTintList = null
                image.setImageBitmap(bitmap)
            }
        }
    }

    override fun onViewAttachedToWindow(v: View) { load() }
    override fun onViewDetachedFromWindow(v: View) {
        job?.cancel()
        generation++
        showPlaceholder()
    }

    /** The app icon for the home page, else a line globe tinted like the row's text (it follows focus). */
    private fun showPlaceholder() {
        if (isHome(url)) {
            image.imageTintList = null
            image.setImageResource(R.drawable.ic_launcher)
        } else {
            image.imageTintList = image.context.getColorStateList(R.color.on_control)
            image.setImageResource(R.drawable.ic_globe)
        }
    }

    private fun isHome(url: String?) = url == Config.HOME_PAGE_URL || url == Config.HOME_URL_ALIAS
}
