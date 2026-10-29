package com.reiniertutoriales.vireolumatv.webengine.webview

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.MediaDrm
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.reiniertutoriales.vireolumatv.R
import java.util.UUID

/**
 * A page's requests for camera, microphone, location and protected media. The user is asked
 * first; Android permissions the app lacks are then requested through [requestAndroid], whose
 * answer comes back in [onAndroidPermissionsResult]. Only the newest request can be granted: a
 * new one denies and forgets the previous one, so a late answer never reaches the wrong page.
 */
internal class PagePermissions(
    private val activity: () -> Activity?,
    private val requestAndroid: (permissions: Array<String>, location: Boolean) -> Unit,
) {
    internal var generation = 0L
        private set
    private var dialog: AlertDialog? = null
    private var request: PermissionRequest? = null
    /** Resources of [request] the app may grant without asking Android, waiting with it. */
    private var grantableWithoutAndroid: List<String>? = null
    /** The approved request that waits for Android's answer. */
    private var waitingForAndroid: PermissionRequest? = null
    private var locationOrigin: String? = null
    private var locationCallback: GeolocationPermissions.Callback? = null

    fun onRequest(newRequest: PermissionRequest) {
        invalidatePending()
        if (newRequest.resources.toList() == listOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)) {
            // Protected media (DRM video) needs no question: it works when the device has Widevine.
            if (MediaDrm.isCryptoSchemeSupported(WIDEVINE)) newRequest.grant(arrayOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)) else newRequest.deny()
            return
        }
        if (newRequest.resources.none { it in ANDROID_PERMISSION || it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }) {
            newRequest.deny()
            return
        }
        val activity = activity() ?: return newRequest.deny()
        request = newRequest
        dialog = AlertDialog.Builder(activity)
            .setMessage(activity.getString(R.string.permission_request, newRequest.resources.joinToString("\n")))
            .setCancelable(false)
            .setNegativeButton(R.string.action_deny) { _, _ ->
                request?.deny()
                request = null
                dialog = null
            }
            .setPositiveButton(R.string.action_allow) { _, _ ->
                dialog = null
                val approved = request ?: return@setPositiveButton
                approve(activity, approved)
            }
            .create()
            .also { it.show() }
    }

    private fun approve(activity: Activity, approved: PermissionRequest) {
        val missing = ArrayList<String>()
        val grantable = ArrayList<String>()
        for (resource in approved.resources) {
            val android = ANDROID_PERMISSION[resource]
            if (android != null) {
                if (ContextCompat.checkSelfPermission(activity, android) != PackageManager.PERMISSION_GRANTED) missing += android
                else grantable += resource
            } else if (resource == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID && MediaDrm.isCryptoSchemeSupported(WIDEVINE)) {
                grantable += resource
            }
        }
        if (missing.isEmpty()) {
            if (grantable.isEmpty()) approved.deny() else approved.grant(grantable.toTypedArray())
            request = null
            return
        }
        grantableWithoutAndroid = grantable
        waitingForAndroid = approved
        requestAndroid(missing.toTypedArray(), false)
    }

    fun onCanceled(canceled: PermissionRequest) {
        if (request !== canceled) return
        generation++
        forgetPageRequest()
        request = null
    }

    fun onLocationPrompt(origin: String, callback: GeolocationPermissions.Callback) {
        invalidatePending()
        // The page waits for an answer, so one is always given.
        val activity = activity() ?: return callback.invoke(origin, false, false)
        locationOrigin = origin
        locationCallback = callback
        dialog = AlertDialog.Builder(activity)
            .setMessage(activity.getString(R.string.permission_request, activity.getString(R.string.permission_location)))
            .setCancelable(false)
            .setNegativeButton(R.string.action_deny) { _, _ ->
                answerLocation(false)
                dialog = null
            }
            .setPositiveButton(R.string.action_allow) { _, _ ->
                dialog = null
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    answerLocation(true)
                } else {
                    requestAndroid(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), true)
                }
            }
            .create()
            .also { it.show() }
    }

    fun onLocationPromptHidden() {
        generation++
        dialog?.dismiss()
        dialog = null
        locationCallback = null
    }

    /** Android's answer to [requestAndroid]: grants what the user and the system allowed. */
    fun onAndroidPermissionsResult(permissions: Array<String>, results: IntArray, location: Boolean, ownerGeneration: Long) {
        if (ownerGeneration != generation) return
        if (location) return answerLocation(results.firstOrNull() == PackageManager.PERMISSION_GRANTED)
        val pending = waitingForAndroid ?: return
        waitingForAndroid = null
        if (pending !== request) return // Replaced or cancelled while the system dialog was open.
        val granted = permissions.indices
            .filter { results.getOrNull(it) == PackageManager.PERMISSION_GRANTED }
            .mapNotNull { index -> ANDROID_PERMISSION.entries.firstOrNull { it.value == permissions[index] }?.key }
            .plus(grantableWithoutAndroid.orEmpty())
            .filter { it in pending.resources }
        grantableWithoutAndroid = null
        if (granted.isEmpty()) pending.deny() else pending.grant(granted.toTypedArray())
        request = null
    }

    private fun answerLocation(allow: Boolean) {
        locationCallback?.invoke(locationOrigin, allow, allow)
        locationCallback = null
        locationOrigin = null
    }

    fun destroy() = invalidatePending()

    private fun invalidatePending() {
        generation++
        forgetPageRequest()
        request?.deny()
        request = null
        answerLocation(false)
    }

    private fun forgetPageRequest() {
        dialog?.dismiss()
        dialog = null
        grantableWithoutAndroid = null
        waitingForAndroid = null
    }

    private companion object {
        val WIDEVINE = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)
        val ANDROID_PERMISSION = mapOf(
            PermissionRequest.RESOURCE_VIDEO_CAPTURE to Manifest.permission.CAMERA,
            PermissionRequest.RESOURCE_AUDIO_CAPTURE to Manifest.permission.RECORD_AUDIO,
        )
    }
}
