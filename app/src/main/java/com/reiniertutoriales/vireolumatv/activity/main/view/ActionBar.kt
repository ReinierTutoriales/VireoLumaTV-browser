package com.reiniertutoriales.vireolumatv.activity.main.view

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnFocusChangeListener
import android.view.View.OnKeyListener
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.reiniertutoriales.vireolumatv.AppContext
import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.downloads.ActiveDownloadsModel
import com.reiniertutoriales.vireolumatv.databinding.ViewActionbarBinding
import com.reiniertutoriales.vireolumatv.utils.Utils
import com.reiniertutoriales.vireolumatv.utils.activemodel.ActiveModelsRepository

class ActionBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val vb = ViewActionbarBinding.inflate( LayoutInflater.from(context),this)
    var callback: Callback? = null
    private var downloadAnimation: Animation? = null
    private var downloadsModel = ActiveModelsRepository.get(ActiveDownloadsModel::class, context)
    private var addressBoxText = ""
    private var extendedAddressBarMode = false
    val isEditingAddress: Boolean get() = extendedAddressBarMode
    private val selectAddressRunnable = Runnable {
        if (vb.etUrl.hasFocus()) vb.etUrl.selectAll()
    }

    interface Callback {
        fun closeWindow()
        fun showDownloads()
        fun showFavorites()
        fun showHistory()
        fun showSettings()
        fun initiateVoiceSearch()
        fun search(text: String)
        fun onExtendedAddressBarMode()
        fun onUrlInputDone()
        fun onAddressInputCancelled() {}
        fun toggleIncognitoMode()
    }

    private val etUrlFocusChangeListener = OnFocusChangeListener { _, focused ->
        if (!focused) {
            removeCallbacks(selectAddressRunnable)
            if (extendedAddressBarMode) {
                dismissExtendedAddressBarMode()
                vb.etUrl.setText(addressBoxText)
                callback?.onAddressInputCancelled()
            }
        }
    }

    private fun startAddressInput() {
        if (extendedAddressBarMode) return
        vb.etUrl.requestFocus()
        enterExtendedAddressBarMode()
        vb.etUrl.showSoftInputOnFocus = true
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(vb.etUrl, InputMethodManager.SHOW_IMPLICIT)
        removeCallbacks(selectAddressRunnable)
        postDelayed(selectAddressRunnable, 500) // Let the TV keyboard finish opening.
    }

    private fun submitAddress() {
        val text = vb.etUrl.text.toString().trim()
        if (text.isEmpty()) return
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(vb.etUrl.windowToken, 0)
        dismissExtendedAddressBarMode()
        vb.etUrl.clearFocus()
        // Commit a previewed tab before loading the search result into it.
        callback?.onUrlInputDone()
        callback?.search(text)
    }

    private val etUrlKeyListener = OnKeyListener { _, _, event ->
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (!extendedAddressBarMode) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    vb.etUrl.focusSearch(View.FOCUS_RIGHT)?.requestFocus()
                }
                true
            } else false
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                    if (extendedAddressBarMode) submitAddress() else startAddressInput()
                }
                true
            }
            else -> false
        }
    }

    init {
        init()
    }

    fun init() {
        orientation = HORIZONTAL

        if (isInEditMode) return

        val incognitoMode = AppContext.provideConfig().incognitoMode

        vb.ibMenu.setOnClickListener { callback?.closeWindow() }
        vb.ibDownloads.setOnClickListener { callback?.showDownloads() }
        vb.ibFavorites.setOnClickListener { callback?.showFavorites() }
        vb.ibHistory.setOnClickListener { callback?.showHistory() }
        vb.ibIncognito.setOnClickListener { callback?.toggleIncognitoMode() }
        vb.ibSettings.setOnClickListener { callback?.showSettings() }

        if (Utils.isFireTV(context)) {
            vb.etUrl.nextFocusRightId = R.id.ibHistory
            removeView(vb.ibVoiceSearch)
        } else {
            vb.ibVoiceSearch.setOnClickListener { callback?.initiateVoiceSearch() }
        }

        vb.ibIncognito.isChecked = incognitoMode

        vb.etUrl.onFocusChangeListener = etUrlFocusChangeListener
        vb.etUrl.showSoftInputOnFocus = false
        vb.etUrl.isCursorVisible = false
        vb.etUrl.setOnClickListener { startAddressInput() }

        vb.etUrl.setOnKeyListener(etUrlKeyListener)
        vb.etUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_DONE) {
                submitAddress()
                true
            } else false
        }


        downloadsModel.activeDownloads.subscribe(context as AppCompatActivity) {
            if (it.isNotEmpty()) {
                if (downloadAnimation == null) {
                    downloadAnimation = AnimationUtils.loadAnimation(context, R.anim.infinite_fadeinout_anim)
                    vb.ibDownloads.startAnimation(downloadAnimation)
                }
            } else {
                downloadAnimation?.apply {
                    this.reset()
                    vb.ibDownloads.clearAnimation()
                    downloadAnimation = null
                }
            }
        }
    }

    fun setAddressBoxText(text: String) {
        addressBoxText = if (text == Config.HOME_PAGE_URL) "" else text
        // A page finishing or redirecting must not overwrite a search being typed.
        if (!extendedAddressBarMode) vb.etUrl.setText(addressBoxText)
    }

    fun setAddressBoxTextColor(color: Int) {
        vb.etUrl.setTextColor(color)
    }

    private fun enterExtendedAddressBarMode() {
        if (extendedAddressBarMode) return
        extendedAddressBarMode = true
        vb.etUrl.isCursorVisible = true
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child is ImageButton) {
                child.visibility = GONE
            }
        }
        callback?.onExtendedAddressBarMode()
    }

    fun dismissExtendedAddressBarMode() {
        if (!extendedAddressBarMode) return
        extendedAddressBarMode = false
        vb.etUrl.showSoftInputOnFocus = false
        vb.etUrl.isCursorVisible = false
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child is ImageButton) {
                child.visibility = VISIBLE
            }
        }
    }

    fun cancelAddressInput() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(vb.etUrl.windowToken, 0)
        dismissExtendedAddressBarMode()
        vb.etUrl.clearFocus()
        vb.etUrl.setText(addressBoxText)
        callback?.onAddressInputCancelled()
        catchFocus()
    }

    fun catchFocus() {
        vb.ibHistory.requestFocus()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(selectAddressRunnable)
        vb.ibDownloads.clearAnimation()
        downloadAnimation = null
        super.onDetachedFromWindow()
    }
}
