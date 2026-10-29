package com.reiniertutoriales.vireolumatv.webengine.webview

import android.Manifest
import android.app.Application
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.PermissionRequest
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class PagePermissionsTest {
    private class Request(private val wanted: Array<String>) : PermissionRequest() {
        var granted: List<String>? = null
        var denied = false
        override fun getOrigin(): Uri = Uri.parse("https://page.test")
        override fun getResources(): Array<String> = wanted
        override fun grant(resources: Array<String>) { granted = resources.toList() }
        override fun deny() { denied = true }
    }

    private val activity = Robolectric.buildActivity(AppCompatActivity::class.java)
        .apply { get().setTheme(R.style.AppTheme) }.setup().get()
    private val asked = mutableListOf<List<String>>()
    private val permissions = PagePermissions({ activity }, { list, _ -> asked += list.toList() })

    private fun allow() {
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        ShadowLooper.idleMainLooper()
    }

    @Test fun withoutAWindowARequestIsDenied() {
        val request = Request(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
        PagePermissions({ null }, { _, _ -> }).onRequest(request)
        assertTrue(request.denied)
    }

    @Test fun approvedResourcesThatNeedAndroidPermissionWaitForIt() {
        val request = Request(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE, PermissionRequest.RESOURCE_AUDIO_CAPTURE))
        shadowOf(activity.application as Application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        permissions.onRequest(request)
        allow()
        assertEquals(listOf(listOf(Manifest.permission.CAMERA)), asked)
        assertNull("Nothing is granted before Android answers", request.granted)
        permissions.onAndroidPermissionsResult(arrayOf(Manifest.permission.CAMERA), intArrayOf(PackageManager.PERMISSION_GRANTED), false)
        assertEquals(setOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE, PermissionRequest.RESOURCE_AUDIO_CAPTURE), request.granted?.toSet())
    }

    @Test fun aNewerRequestDeniesTheOlderAndALateAnswerCannotGrantIt() {
        val first = Request(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
        permissions.onRequest(first)
        allow()
        val second = Request(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
        permissions.onRequest(second)
        assertTrue(first.denied)
        permissions.onAndroidPermissionsResult(arrayOf(Manifest.permission.CAMERA), intArrayOf(PackageManager.PERMISSION_GRANTED), false)
        assertNull(first.granted)
        assertNull("The newer request was not approved yet", second.granted)
    }
    @Test fun unknownWebViewResourcesAreDeniedInsteadOfBeingGrantedWithTheWholeRequest() {
        val unknown = Request(arrayOf("android.webkit.resource.FUTURE_CAPABILITY"))
        permissions.onRequest(unknown)
        assertTrue(unknown.denied)
        assertNull(unknown.granted)
    }

    @Test fun approvedKnownResourcesDoNotAlsoGrantUnknownCapabilities() {
        shadowOf(activity.application as Application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val mixed = Request(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE, "android.webkit.resource.FUTURE_CAPABILITY"))
        permissions.onRequest(mixed)
        allow()
        assertEquals(listOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE), mixed.granted)
    }

    @Test fun androidResultsOnlyGrantResourcesThePageActuallyRequested() {
        val request = Request(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
        permissions.onRequest(request)
        allow()
        permissions.onAndroidPermissionsResult(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
            intArrayOf(PackageManager.PERMISSION_GRANTED, PackageManager.PERMISSION_GRANTED), false)
        assertEquals(listOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE), request.granted)
    }

}
