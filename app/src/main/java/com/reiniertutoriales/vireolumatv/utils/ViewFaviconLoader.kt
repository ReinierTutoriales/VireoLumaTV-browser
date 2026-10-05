package com.reiniertutoriales.vireolumatv.utils

import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.singleton.FaviconsPool
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
        image.setImageResource(if (isHome(url)) R.drawable.ic_launcher else R.drawable.ic_not_available)
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
            val bitmap = FaviconsPool.get(requestedUrl)
            if (request == generation && view.isAttachedToWindow && bitmap != null) {
                image.setImageBitmap(bitmap)
            }
        }
    }

    override fun onViewAttachedToWindow(v: View) { load() }
    override fun onViewDetachedFromWindow(v: View) {
        job?.cancel()
        generation++
        image.setImageResource(if (isHome(url)) R.drawable.ic_launcher else R.drawable.ic_not_available)
    }

    private fun isHome(url: String?) = url == Config.HOME_PAGE_URL || url == Config.HOME_URL_ALIAS
}
