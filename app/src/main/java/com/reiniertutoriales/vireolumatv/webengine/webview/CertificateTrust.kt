package com.reiniertutoriales.vireolumatv.webengine.webview

import android.net.http.SslCertificate
import android.net.http.SslError
import android.os.Build
import android.util.Log
import androidx.core.net.toUri

/** Decides whether a certificate the user accepted on the warning page is the one now presented. */
internal object CertificateTrust {
    /** A certificate the user accepted once on the warning page, for the address it was shown for. */
    class Approval(val url: String?, val certificate: SslCertificate?)

    /** The approval covers [error]: the same host and the same certificate. */
    fun mayProceed(approved: Approval, error: SslError): Boolean =
        mayProceed(approved.url, error.url) { isSameCertificate(approved.certificate, error.certificate) }

    internal inline fun mayProceed(approvedUrl: String?, url: String?, sameCertificate: () -> Boolean): Boolean =
        sameHost(approvedUrl, url) && sameCertificate()

    /** Both addresses name the same, non-empty host. */
    fun sameHost(a: String?, b: String?): Boolean {
        val hostA = a?.let { runCatching { it.toUri().host }.getOrNull() }
        val hostB = b?.let { runCatching { it.toUri().host }.getOrNull() }
        return !hostA.isNullOrEmpty() && hostA.equals(hostB, ignoreCase = true)
    }

    /**
     * Compares the certificates' DER bytes; their names alone would let another certificate with
     * the same subject and issuer through. Without access to the bytes, the names are compared.
     */
    fun isSameCertificate(a: SslCertificate?, b: SslCertificate?): Boolean {
        if (a == null || b == null) return false
        val bytesA = encoded(a)
        val bytesB = encoded(b)
        return sameEncoded(bytesA, bytesB)
    }

    internal fun sameEncoded(a: ByteArray?, b: ByteArray?): Boolean =
        a != null && b != null && a.contentEquals(b)

    private fun encoded(certificate: SslCertificate): ByteArray? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            certificate.x509Certificate?.encoded
        } else {
            // Before Android 10 the bytes are only in saveState(), under this stable AOSP key.
            SslCertificate.saveState(certificate)?.getByteArray("x509-certificate")
        }
    } catch (error: Exception) {
        Log.w("CertificateTrust", "No certificate bytes: $error")
        null
    }
}
