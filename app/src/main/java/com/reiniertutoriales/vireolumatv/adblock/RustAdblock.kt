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

    /** [trusted] (same order as [lists]) marks the lists allowed to use `trusted-*` scriptlets. */
    @JvmStatic external fun nativeCompile(lists: Array<String>, trusted: BooleanArray, resources: String): Long
    /** Reads the cache file natively, so it never passes through the Java heap; 0 if unusable. */
    @JvmStatic external fun nativeDeserialize(path: String, resources: String): Long
    /** Writes the rules to [path] natively; false if they could not be written. */
    @JvmStatic external fun nativeSerialize(handle: Long, path: String): Boolean
    @JvmStatic external fun nativeDestroy(handle: Long)
    @JvmStatic external fun nativeCheck(handle: Long, url: String, source: String, type: String): Int
    /** "" allows the request, "!" blocks it, anything else is the `data:` URL of its `$redirect` resource. */
    @JvmStatic external fun nativeVerdict(handle: Long, url: String, source: String, type: String, method: String): String
    @JvmStatic external fun nativeCosmetic(handle: Long, url: String): String
    @JvmStatic external fun nativeHiddenSelectors(handle: Long, url: String, classes: String, ids: String): String
}
