package com.reiniertutoriales.vireolumatv.activity.main.dialogs.settings

import android.view.View
import android.view.animation.AnimationUtils
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RadioGroup

/** Keeps each settings page alive while the dialog is open, including unsaved form values. */
internal class SettingsPages(
    private val tabs: RadioGroup,
    private val content: FrameLayout,
    private val pageIds: Set<Int>,
    private val createPage: (Int) -> View,
) {
    private val pages = mutableMapOf<Int, View>()

    init {
        tabs.setOnCheckedChangeListener { _, id -> show(id) }
        show(tabs.checkedRadioButtonId)
    }

    private fun show(id: Int) {
        if (id !in pageIds) return
        for (index in 0 until tabs.childCount) {
            val tab = tabs.getChildAt(index)
            tab.isActivated = tab.id == id
        }
        val page = pages.getOrPut(id) { createPage(id) }
        if (content.getChildAt(0) === page) return
        content.removeAllViews()
        (page.parent as? ViewGroup)?.removeView(page)
        content.addView(page, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        // Animate only the new page's opacity; never move focus or re-create saved fields.
        if (android.os.Build.VERSION.SDK_INT < 26 || android.animation.ValueAnimator.areAnimatorsEnabled()) {
            page.alpha = 0f
            page.animate().alpha(1f).setDuration(180L)
                .setInterpolator(AnimationUtils.loadInterpolator(page.context, android.R.interpolator.fast_out_slow_in))
                .start()
        }
    }
}
