package com.reiniertutoriales.vireolumatv.webengine.webview

import android.net.http.SslError
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class CertificateWarningPageTest {

    @Test fun certificatesWithOnlyMatchingDisplayNamesAreNotTrusted() {
        val now = java.util.Date()
        val first = android.net.http.SslCertificate("CN=Example", "CN=Issuer", now, now)
        val second = android.net.http.SslCertificate("CN=Example", "CN=Issuer", now, now)
        assertFalse(CertificateTrust.isSameCertificate(first, second))
    }

    @Test fun valuesAreEscapedAndUnknownPlaceholdersEmpty() {
        assertEquals("<p>&lt;b&gt;x&lt;/b&gt; &amp; </p>",
            CertificateWarningPage.fill("<p>{{a}} &amp; {{missing}}</p>", mapOf("a" to "<b>x</b>")))
    }

    @Test fun everyErrorHasItsOwnOrAGenericDescription() {
        assertEquals(R.string.cert_expired, CertificateWarningPage.describe(SslError.SSL_EXPIRED))
        assertEquals(R.string.cert_wrong_site, CertificateWarningPage.describe(SslError.SSL_IDMISMATCH))
        assertEquals(R.string.cert_bad_date, CertificateWarningPage.describe(SslError.SSL_NOTYETVALID))
        assertEquals(R.string.cert_bad_date, CertificateWarningPage.describe(SslError.SSL_DATE_INVALID))
        assertEquals(R.string.cert_untrusted, CertificateWarningPage.describe(SslError.SSL_UNTRUSTED))
        assertEquals(R.string.cert_invalid, CertificateWarningPage.describe(SslError.SSL_INVALID))
    }

    @Test fun thePackagedTemplateIsFilledCompletely() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val template = context.assets.open("pages/warning-certificate.html").bufferedReader().use { it.readText() }
        val page = CertificateWarningPage.render(context, template, "https://bad.example/<x>", SslError.SSL_UNTRUSTED,
            "Issued by: <Evil CA>")
        assertFalse("Every placeholder is filled", page.contains("{{"))
        assertTrue(page.contains(">bad.example<"))
        assertTrue(page.contains(context.getString(R.string.cert_untrusted)))
        assertTrue("Certificate details are escaped", page.contains("&lt;Evil CA&gt;") && !page.contains("<Evil CA>"))
        assertTrue("Proceeding is the only bridge call", page.contains("VireoLumaTVApp.trustCertificateOnce()"))
    }
}
