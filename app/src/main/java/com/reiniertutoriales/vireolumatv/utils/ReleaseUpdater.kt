package com.reiniertutoriales.vireolumatv.utils

import android.content.Context
import android.util.Base64
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.reiniertutoriales.vireolumatv.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.security.MessageDigest

/**
 * Updates from this repository's GitHub releases. Each release publishes one APK per ABI and a
 * SHA256SUMS.txt; an APK is installed only when its SHA-256 matches. Android accepts an update only
 * when it is signed with the same certificate as the installed app.
 */
object ReleaseUpdater {
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/ReinierTutoriales/VireoLumaTV-browser/releases/latest"
    private const val DOWNLOAD_DIR = "updates"
    private const val MAX_APK_BYTES = 64L * 1024 * 1024
    private const val MAX_TEXT_BYTES = 1024 * 1024
    private val ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86_64")

    /**
     * Public half of the key that signs SHA256SUMS.txt in every release. The private key exists only
     * as the RELEASE_SIGNING_KEY secret. The public key is pinned here; a missing or invalid signature is rejected.
     */
    private const val RELEASE_PUBLIC_KEY_PEM = """-----BEGIN PUBLIC KEY-----
MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEU470N7LLJVCIVOiICWLXJA6qri5h
nsiyz3dy2TB63QNpK+ib5XGS2c3pDjtOq89+ayf+TNXRR9uFnL8QWoU9Wg==
-----END PUBLIC KEY-----"""

    class Release(val version: String, val apkName: String, val apkUrl: String, val sha256: String)

    /** The release newer than the installed version for this device's ABI, or null when up to date. */
    fun fetchNewerRelease(): Release? {
        val json = JSONObject(readText(LATEST_RELEASE_API, "application/vnd.github+json"))
        val version = json.getString("tag_name").removePrefix("v")
        if (!isNewer(version, BuildConfig.VERSION_NAME)) return null
        val abi = Build.SUPPORTED_ABIS.firstOrNull { it in ABIS }
            ?: throw IOException("No APK for this device ABI")
        val apkName = apkNameFor(version, abi)
        val assets = json.getJSONArray("assets")
        val sumsBytes = readBytes(assetUrl(assets, "SHA256SUMS.txt"), null)
        val signature = readBytes(assetUrl(assets, "SHA256SUMS.txt.sig"), null)
        if (!verifySignature(releasePublicKey(), sumsBytes, signature)) {
            throw IOException("Release checksums are not signed by the release key")
        }
        return Release(version, apkName, assetUrl(assets, apkName), checksumFor(String(sumsBytes, Charsets.UTF_8), apkName))
    }

    /** Downloads [release] into the app cache. The file is kept only when its checksum matches. */
    fun download(context: Context, release: Release): File {
        val dir = File(context.cacheDir, DOWNLOAD_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, release.apkName)
        val connection = open(release.apkUrl, null)
        return try {
            saveVerified(connection.inputStream, target, release.sha256)
        } finally { connection.disconnect() }
    }

    /** Owns the input and removes incomplete/unverified output on every failure. */
    internal fun saveVerified(input: InputStream, target: File, expectedSha256: String): File {
        var verified = false
        try {
            input.use { source -> target.outputStream().use { copyLimited(source, it) } }
            if (!sha256(target).equals(expectedSha256, ignoreCase = true)) {
                throw IOException("Checksum mismatch for ${target.name}")
            }
            verified = true
            return target
        } finally {
            if (!verified) target.delete()
        }
    }

    /** Hands [apk] to the system installer. False when this app may not install packages yet. */
    fun install(context: Context, apk: File): Boolean {
        // Before API 26 the system asks for the unknown-sources permission itself, during installation.
        val mayInstall = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()
        if (!mayInstall) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:${BuildConfig.APPLICATION_ID}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return false
        }
        val uri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    fun clearDownloads(context: Context) {
        File(context.cacheDir, DOWNLOAD_DIR).deleteRecursively()
    }

    internal fun apkNameFor(version: String, abi: String) = "VireoLumaTV-$version-$abi.apk"

    /** Compares dotted numeric versions; missing parts count as zero. */
    internal fun isNewer(candidate: String, installed: String): Boolean {
        val a = versionParts(candidate)
        val b = versionParts(installed)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Reads the `sha256sum` line for [name]; accepts both text ("  ") and binary (" *") markers. */
    internal fun checksumFor(sums: String, name: String): String {
        for (line in sums.lineSequence()) {
            val parts = line.trim().split(Regex("\\s+"), limit = 2)
            if (parts.size == 2 && parts[1].removePrefix("*") == name) return parts[0]
        }
        throw IOException("No checksum for $name")
    }

    private fun versionParts(version: String) =
        version.split('.').map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    private fun assetUrl(assets: JSONArray, name: String): String {
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.getString("name") == name) return asset.getString("browser_download_url")
        }
        throw IOException("Release has no $name")
    }

    private fun readText(url: String, accept: String?): String = String(readBytes(url, accept), Charsets.UTF_8)

    private fun readBytes(url: String, accept: String?): ByteArray {
        val out = ByteArrayOutputStream()
        val connection = open(url, accept)
        try {
            connection.inputStream.use { input ->
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > MAX_TEXT_BYTES) throw IOException("Response too large")
                }
            }
        } finally { connection.disconnect() }
        return out.toByteArray()
    }

    private fun releasePublicKey(): PublicKey {
        check(RELEASE_PUBLIC_KEY_PEM.startsWith("-----BEGIN")) { "Release public key is not configured" }
        val body = RELEASE_PUBLIC_KEY_PEM.lines().filterNot { it.startsWith("-----") }.joinToString("")
        return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.decode(body, Base64.DEFAULT)))
    }

    /** ECDSA P-256 over SHA-256, the format `openssl dgst -sha256 -sign` produces. */
    internal fun verifySignature(publicKey: PublicKey, data: ByteArray, signature: ByteArray): Boolean =
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey)
            update(data)
            verify(signature)
        }

    internal fun isSecureUpdateUrl(url: URL): Boolean = url.protocol.equals("https", ignoreCase = true)

    private fun open(url: String, accept: String?): HttpURLConnection {
        val source = URL(url)
        if (!isSecureUpdateUrl(source)) throw IOException("Release updates require HTTPS")
        val connection = source.openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.useCaches = false
        connection.setRequestProperty("User-Agent", "VireoLumaTV/${BuildConfig.VERSION_NAME}")
        accept?.let { connection.setRequestProperty("Accept", it) }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code for $url")
            if (!isSecureUpdateUrl(connection.url)) throw IOException("Release update redirected outside HTTPS")
            return connection
        } catch (error: Exception) {
            connection.disconnect()
            throw error
        }
    }

    private fun copyLimited(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > MAX_APK_BYTES) throw IOException("APK larger than expected")
            output.write(buffer, 0, n)
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
