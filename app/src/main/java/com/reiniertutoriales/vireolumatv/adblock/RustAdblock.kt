package com.reiniertutoriales.vireolumatv.adblock

import android.util.Log

/** JNI entry points of native/adblock-jni (Brave's adblock-rust engine). */
internal object RustAdblock {
    private const val TAG = "RustAdblock"

    /** JVM unit tests point this property at a host build of the same library. */
    private const val HOST_LIBRARY_PROPERTY = "vireo.adblock.hostLib"

    val isAvailable: Boolean by lazy {
        try {
            val hostLibrary = System.getProperty(HOST_LIBRARY_PROPERTY)
            if (hostLibrary.isNullOrEmpty()) System.loadLibrary("vireoadblock") else System.load(hostLibrary)
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Native adblock engine unavailable", e)
            false
        }
    }

    @JvmStatic external fun nativeCompile(lists: Array<String>, resources: String): Long
    @JvmStatic external fun nativeDeserialize(data: ByteArray, resources: String): Long
    @JvmStatic external fun nativeSerialize(handle: Long): ByteArray
    @JvmStatic external fun nativeDestroy(handle: Long)
    @JvmStatic external fun nativeCheck(handle: Long, url: String, source: String, type: String): Int
    @JvmStatic external fun nativeRedirect(handle: Long, url: String, source: String, type: String): String
    @JvmStatic external fun nativeCosmetic(handle: Long, url: String): String
    @JvmStatic external fun nativeHiddenSelectors(handle: Long, url: String, classes: String, ids: String): String
}
