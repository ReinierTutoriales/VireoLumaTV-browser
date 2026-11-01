package com.reiniertutoriales.vireolumatv.webengine.webview

import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.BrowserTab
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** A one-time certificate approval covers its own host only and cannot outlive the next page. */
@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class CertificateTrustScopeTest {
    @Test fun anApprovalCoversTheSameCertificateOnTheSameHostOnly() {
        val same = { true }
        assertTrue(CertificateTrust.mayProceed("https://self-signed.test/a", "https://SELF-SIGNED.test/b?x", same))
        assertFalse("another host with the same certificate", CertificateTrust.mayProceed("https://self-signed.test/", "https://bank.test/", same))
        assertFalse("another certificate on the same host", CertificateTrust.mayProceed("https://self-signed.test/", "https://self-signed.test/") { false })
        assertFalse(CertificateTrust.mayProceed(null, "https://self-signed.test/", same))
        assertFalse(CertificateTrust.mayProceed("https://self-signed.test/", null, same))
        assertFalse(CertificateTrust.mayProceed("data:text/html,x", "data:text/html,x", same))
    }

    @Test fun anUnusedApprovalIsDroppedByTheNextPageOrALoadOfAnotherHost() {
        val app = RuntimeEnvironment.getApplication()
        val engine = WebViewPageEngine(BrowserTab())
        try {
            val page = engine.createView(app) as BrowserWebView
            val approved = CertificateTrust.Approval("https://self-signed.test/", null)
            page.trustedCertificate = approved
            page.loadUrl("https://self-signed.test/")
            assertNotNull("Loading the approved host keeps the approval for its certificate error", page.trustedCertificate)
            page.onDocumentStarted("https://self-signed.test/")
            assertNull("The page committed without needing it", page.trustedCertificate)

            page.trustedCertificate = approved
            page.loadUrl("https://elsewhere.test/")
            assertNull(page.trustedCertificate)

            page.trustedCertificate = approved
            page.onDocumentStarted("file:///android_asset/")
            assertNotNull("The warning page itself does not spend it", page.trustedCertificate)
        } finally { engine.detach(destroyView = true) }
    }
}
