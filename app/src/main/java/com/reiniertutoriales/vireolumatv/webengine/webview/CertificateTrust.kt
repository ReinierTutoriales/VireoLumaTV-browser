package com.reiniertutoriales.vireolumatv.webengine.webview

import android.net.http.SslCertificate
import android.os.Build
import android.util.Log

/** Decides whether a certificate the user accepted on the warning page is the one now presented. */
internal object CertificateTrust {
    /**
     * Compares the certificates' DER bytes; their names alone would let another certificate with
     * the same subject and issuer through. Without access to the bytes, the names are compared.
     */
    fun isSameCertificate(a: SslCertificate?, b: SslCertificate?): Boolean {
        if (a == null || b == null) return false
        val bytesA = encoded(a)
        val bytesB = encoded(b)
        return if (bytesA != null && bytesB != null) bytesA.contentEquals(bytesB) else a.toString() == b.toString()
    }

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
