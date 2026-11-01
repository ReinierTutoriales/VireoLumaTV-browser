package com.reiniertutoriales.vireolumatv.webengine.webview

import android.content.DialogInterface
import android.net.Uri
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class HttpAuthPromptsTest {
    private val activity = Robolectric.buildActivity(AppCompatActivity::class.java)
        .apply { get().setTheme(R.style.AppTheme) }.setup().get()
    private val prompts = HttpAuthPrompts { activity }

    private class Recorded : HttpAuthPrompts.Answer {
        var credentials: Pair<String, String>? = null
        var cancelled = false
        override fun proceed(user: String, password: String) { credentials = user to password }
        override fun cancel() { cancelled = true }
    }

    private fun dialog() = ShadowDialog.getLatestDialog() as AlertDialog?
    private fun message() = dialog()!!.findViewById<TextView>(android.R.id.message)!!.text.toString()
    private fun shown(url: String) = Uri.parse(url)

    @Test fun anEmbeddedResourceOfAnotherHostCannotAskUnderTheShownPage() {
        val answer = Recorded()
        prompts.request("evil.test", "Bank", shown("https://bank.test/account"), null, answer)
        assertTrue(answer.cancelled)
        assertNull("No prompt is shown", dialog())
    }

    @Test fun theShownSiteAsksWithItsHostRealmAndAPlainTextWarning() {
        val answer = Recorded()
        prompts.request("intranet.test", "Staff", shown("http://intranet.test/"), null, answer)
        val text = message()
        assertTrue(text.contains("intranet.test"))
        assertTrue(text.contains("Staff"))
        assertTrue(text.contains(activity.getString(R.string.http_auth_cleartext)))
        val inputs = mutableListOf<EditText>()
        fun collect(v: android.view.View) {
            if (v is EditText) inputs += v
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) collect(v.getChildAt(i))
        }
        collect(dialog()!!.window!!.decorView)
        inputs[0].setText("ana"); inputs[1].setText("secret")
        dialog()!!.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        ShadowLooper.idleMainLooper()
        assertEquals("ana" to "secret", answer.credentials)
    }

    @Test fun anEncryptedSiteHasNoPlainTextWarningAndLongRealmsAreCut() {
        prompts.request("secure.test", "x".repeat(200), shown("https://secure.test/"), null, Recorded())
        val text = message()
        assertFalse(text.contains(activity.getString(R.string.http_auth_cleartext)))
        assertTrue(text.contains("x".repeat(HttpAuthPrompts.MAX_REALM) + "…"))
        assertFalse(text.contains("x".repeat(HttpAuthPrompts.MAX_REALM + 1)))
    }

    @Test fun duringATopLevelLoadARedirectedHostIsAskedAndNamedAsAnotherSite() {
        prompts.request("login.other.test", null, shown("https://old.test/"), shown("https://start.test/"), Recorded())
        val text = message()
        assertTrue(text.contains(activity.getString(R.string.http_auth_message, "login.other.test")))
        assertTrue(text.contains(activity.getString(R.string.http_auth_other_site, "start.test")))
        assertFalse("The scheme of another host is unknown", text.contains(activity.getString(R.string.http_auth_cleartext)))
    }

    @Test fun theLoadedAddressItselfAsksWithoutTheOtherSiteNote() {
        val scope = HttpAuthPrompts.scope("start.test", shown("https://old.test/"), shown("http://start.test:8080/"))
        assertEquals(HttpAuthPrompts.Scope("start.test", cleartext = true, otherThan = null), scope)
        assertEquals(HttpAuthPrompts.Scope("start.test:8080", cleartext = true, otherThan = null),
            HttpAuthPrompts.scope("start.test:8080", null, shown("http://start.test:8080/")))
        assertNull(HttpAuthPrompts.scope("", shown("https://a.test/"), null))
        assertNull(HttpAuthPrompts.scope(null, null, null))
    }

    @Test fun oneDialogAtATimeAndTheSameRealmSharesTheAnswer() {
        val first = Recorded()
        val sameRealm = Recorded()
        val other = Recorded()
        val page = shown("https://site.test/")
        prompts.request("site.test", "R", page, null, first)
        val open = dialog()
        prompts.request("SITE.test", "R", page, null, sameRealm)
        prompts.request("site.test", "Other", page, null, other)
        assertTrue("A different realm cannot stack a second dialog", other.cancelled)
        assertTrue(open === dialog())
        open!!.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        ShadowLooper.idleMainLooper()
        assertTrue(first.cancelled)
        assertTrue(sameRealm.cancelled)
        // Answered: the next request opens a new prompt.
        val later = Recorded()
        prompts.request("site.test", "Other", page, null, later)
        assertFalse(later.cancelled)
        assertFalse(open === dialog())
    }

    @Test fun aLoadThatEndsWithoutAPageNoLongerLetsOtherHostsAsk() {
        val load = TopLevelLoad()
        load.committed(shown("https://bank.test/"))
        load.requested(shown("https://slow.test/file.zip"))
        assertEquals("While it loads, another host is asked with a warning", "slow.test",
            HttpAuthPrompts.scope("evil.test", load.shown, load.loading)?.otherThan)
        // It became a download or failed: the bank page is still the one on screen.
        load.ended()
        assertNull(HttpAuthPrompts.scope("evil.test", load.shown, load.loading))
        assertEquals(HttpAuthPrompts.Scope("bank.test", cleartext = false, otherThan = null),
            HttpAuthPrompts.scope("bank.test", load.shown, load.loading))
    }

    @Test fun aShownPageEndsTheLoadAndBecomesTheReference() {
        val load = TopLevelLoad()
        load.requested(shown("http://intranet.test/"))
        load.committed(shown("http://intranet.test/home"))
        assertNull(load.loading)
        assertNull(HttpAuthPrompts.scope("cdn.test", load.shown, load.loading))
        assertEquals(true, HttpAuthPrompts.scope("intranet.test", load.shown, load.loading)?.cleartext)
    }
}
