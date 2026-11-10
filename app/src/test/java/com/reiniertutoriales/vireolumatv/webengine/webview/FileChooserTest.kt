package com.reiniertutoriales.vireolumatv.webengine.webview

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.result.contract.ActivityResultContracts
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import com.reiniertutoriales.vireolumatv.webengine.PageWindow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import java.lang.reflect.Proxy
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28], shadows = [FileChooserTest.ChooserParamsShadow::class])
class FileChooserTest {
    // Framework parseResult delegates to the Chromium provider, which Robolectric does not load.
    @Implements(WebChromeClient.FileChooserParams::class)
    class ChooserParamsShadow {
        companion object {
            @JvmStatic @Implementation
            fun parseResult(resultCode: Int, data: Intent?): Array<Uri>? =
                ActivityResultContracts.GetMultipleContents().parseResult(resultCode, data)
                    .toTypedArray().takeIf { it.isNotEmpty() }
        }
    }

    private class Answer : ValueCallback<Array<Uri>> {
        val results = mutableListOf<List<Uri>?>()
        override fun onReceiveValue(value: Array<Uri>?) { results += value?.toList() }
    }

    private val params = object : WebChromeClient.FileChooserParams() {
        override fun getMode() = MODE_OPEN
        override fun getAcceptTypes() = arrayOf("*/*")
        override fun isCaptureEnabled() = false
        override fun getTitle(): CharSequence? = null
        override fun getFilenameHint(): String? = null
        override fun createIntent() = Intent(Intent.ACTION_GET_CONTENT).setType("*/*")
    }

    private fun withPage(test: (WebViewPageEngine, BrowserWebView) -> Unit) {
        val engine = WebViewPageEngine(BrowserTab())
        engine.window = Proxy.newProxyInstance(PageWindow::class.java.classLoader,
            arrayOf(PageWindow::class.java)) { _, method, _ ->
            when (method.name) {
                "pickFile" -> true
                else -> null
            }
        } as PageWindow
        try { test(engine, engine.createView(RuntimeEnvironment.getApplication()) as BrowserWebView) }
        finally { engine.detach(destroyView = true) }
    }

    @Test fun aSecondChooserCannotReplaceTheOwnerOfThePendingResult() = withPage { engine, page ->
        val first = Answer()
        val second = Answer()
        val chrome = page.webChromeClient!!
        assertTrue(chrome.onShowFileChooser(page, first, params))
        // Android still owns the first picker. Reject a second request without retargeting it.
        assertFalse(chrome.onShowFileChooser(page, second, params))
        val selected = Uri.parse("content://picker/first")
        engine.onFilePicked(Activity.RESULT_OK, Intent().setData(selected))
        assertEquals(listOf(listOf(selected)), first.results)
        assertTrue(second.results.isEmpty())
        engine.onFilePicked(Activity.RESULT_CANCELED, null)
        assertEquals(1, first.results.size)
    }

    @Test fun destroyingThePageCancelsItsPendingChooserExactlyOnce() = withPage { engine, page ->
        val answer = Answer()
        assertTrue(page.webChromeClient!!.onShowFileChooser(page, answer, params))
        engine.detach(destroyView = true)
        assertEquals(listOf<List<Uri>?>(null), answer.results)
        engine.onFilePicked(Activity.RESULT_OK, Intent().setData(Uri.parse("content://picker/late")))
        assertEquals(1, answer.results.size)
    }

    @Test fun navigatingAwayCancelsTheOldDocumentsChooser() = withPage { engine, page ->
        val answer = Answer()
        assertTrue(page.webChromeClient!!.onShowFileChooser(page, answer, params))
        page.onDocumentStarted("https://other.test/")
        assertEquals(listOf<List<Uri>?>(null), answer.results)
        engine.onFilePicked(Activity.RESULT_OK, Intent().setData(Uri.parse("content://picker/late")))
        assertEquals(1, answer.results.size)
    }

    @Test fun aPickerCannotReturnTheAppsPrivateFilesToAWebPage() = withPage { engine, page ->
        val app = RuntimeEnvironment.getApplication()
        val answer = Answer()
        assertTrue(page.webChromeClient!!.onShowFileChooser(page, answer, params))
        val privateFile = Uri.fromFile(File(app.filesDir, "../shared_prefs/settings.xml"))
        engine.onFilePicked(Activity.RESULT_OK, Intent().setData(privateFile))
        assertEquals(listOf<List<Uri>?>(null), answer.results)
    }

    @Test fun aPickerCannotUseTheAppsOwnProviderToUploadAnUpdatePackage() = withPage { engine, page ->
        val app = RuntimeEnvironment.getApplication()
        val answer = Answer()
        assertTrue(page.webChromeClient!!.onShowFileChooser(page, answer, params))
        val ownFile = Uri.parse("content://${app.packageName}.provider/verified_updates/update.apk")
        engine.onFilePicked(Activity.RESULT_OK, Intent().setData(ownFile))
        assertEquals(listOf<List<Uri>?>(null), answer.results)
    }

    @Test fun aPickerCannotBypassTheOwnProviderCheckWithAUserId() = withPage { engine, page ->
        val app = RuntimeEnvironment.getApplication()
        val answer = Answer()
        assertTrue(page.webChromeClient!!.onShowFileChooser(page, answer, params))
        val ownFile = Uri.parse("content://0@${app.packageName}.provider/verified_updates/update.apk")
        engine.onFilePicked(Activity.RESULT_OK, Intent().setData(ownFile))
        assertEquals(listOf<List<Uri>?>(null), answer.results)
    }
}
