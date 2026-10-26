package com.reiniertutoriales.vireolumatv.utils

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class ReleaseUpdaterTest {
    @Test fun newerVersionsCompareNumerically() {
        assertTrue(ReleaseUpdater.isNewer("1.10.0", "1.9.9"))
        assertTrue(ReleaseUpdater.isNewer("1.2.1", "1.2.0"))
        assertFalse(ReleaseUpdater.isNewer("1.2.0", "1.2.0"))
        assertFalse(ReleaseUpdater.isNewer("1.1.1", "1.2.0"))
        assertTrue(ReleaseUpdater.isNewer("1.2", "1.1.9"))
    }

    @Test fun apkNameMatchesTheReleaseWorkflow() {
        assertEquals("VireoLumaTV-1.2.0-arm64-v8a.apk", ReleaseUpdater.apkNameFor("1.2.0", "arm64-v8a"))
    }

    @Test fun checksumIsReadFromTextAndBinaryLines() {
        val sums = """
            aaa111  VireoLumaTV-1.2.0-x86_64.apk
            bbb222 *VireoLumaTV-1.2.0-arm64-v8a.apk
        """.trimIndent()
        assertEquals("bbb222", ReleaseUpdater.checksumFor(sums, "VireoLumaTV-1.2.0-arm64-v8a.apk"))
        assertEquals("aaa111", ReleaseUpdater.checksumFor(sums, "VireoLumaTV-1.2.0-x86_64.apk"))
    }

    @Test(expected = IOException::class)
    fun missingChecksumFails() {
        ReleaseUpdater.checksumFor("aaa111  other.apk", "VireoLumaTV-1.2.0-arm64-v8a.apk")
    }

    private fun keyPair() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private fun sign(privateKey: java.security.PrivateKey, data: ByteArray) =
        Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(data)
            sign()
        }

    @Test fun checksumsSignedByTheReleaseKeyVerify() {
        val key = keyPair()
        val sums = "aaa111  VireoLumaTV-1.2.1-arm64-v8a.apk\n".toByteArray()
        assertTrue(ReleaseUpdater.verifySignature(key.public, sums, sign(key.private, sums)))
    }

    @Test fun alteredChecksumsAreRejected() {
        val key = keyPair()
        val sums = "aaa111  VireoLumaTV-1.2.1-arm64-v8a.apk\n".toByteArray()
        val signature = sign(key.private, sums)
        val altered = "bbb222  VireoLumaTV-1.2.1-arm64-v8a.apk\n".toByteArray()
        assertFalse(ReleaseUpdater.verifySignature(key.public, altered, signature))
    }

    @Test fun checksumsSignedByAnotherKeyAreRejected() {
        val sums = "aaa111  VireoLumaTV-1.2.1-arm64-v8a.apk\n".toByteArray()
        val signature = sign(keyPair().private, sums)
        assertFalse(ReleaseUpdater.verifySignature(keyPair().public, sums, signature))
    }
}
