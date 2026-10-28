package com.reiniertutoriales.vireolumatv.utils

import android.os.Bundle
import android.os.Parcel
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.security.MessageDigest

/**
 * Stored WebView tab state. A [Bundle] is kept as its parcel bytes, which only the same platform
 * can read back reliably, so [decode] returns null instead of throwing for anything unreadable.
 */
object BundleBytes {
    fun encode(bundle: Bundle): ByteArray = withParcel { it.writeBundle(bundle); it.marshall() }

    fun decode(bytes: ByteArray): Bundle? = try {
        withParcel {
            it.unmarshall(bytes, 0, bytes.size)
            it.setDataPosition(0)
            it.readBundle(BundleBytes::class.java.classLoader)
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Reads the JSON form older versions stored tab state in. Number arrays become byte arrays,
     * as WebView state stores its data there; values of any other kind are skipped.
     */
    fun fromLegacyJson(json: JSONObject): Bundle = Bundle().also { bundle ->
        for (key in json.keys()) {
            when (val value = json.get(key)) {
                is String -> bundle.putString(key, value)
                is Int -> bundle.putInt(key, value)
                is Long -> bundle.putLong(key, value)
                is Boolean -> bundle.putBoolean(key, value)
                is Double -> bundle.putDouble(key, value)
                is JSONObject -> bundle.putBundle(key, fromLegacyJson(value))
                is JSONArray -> putArray(bundle, key, value)
            }
        }
    }

    private fun putArray(bundle: Bundle, key: String, array: JSONArray) {
        val size = array.length()
        when (if (size == 0) null else array.get(0)) {
            is String -> bundle.putStringArray(key, Array(size) { array.getString(it) })
            is Int -> bundle.putByteArray(key, ByteArray(size) { array.getInt(it).toByte() })
            is Long -> bundle.putLongArray(key, LongArray(size) { array.getLong(it) })
            is Boolean -> bundle.putBooleanArray(key, BooleanArray(size) { array.getBoolean(it) })
            is Double -> bundle.putDoubleArray(key, DoubleArray(size) { array.getDouble(it) })
        }
    }

    private inline fun <T> withParcel(block: (Parcel) -> T): T {
        val parcel = Parcel.obtain()
        try {
            return block(parcel)
        } finally {
            parcel.recycle()
        }
    }
}

/**
 * MD5 of [bytes] as lowercase hex without leading zeros. Stored thumbnail names were built this
 * way, so the format must stay as it is to find them again; it is a name, not a security check.
 */
fun md5Name(bytes: ByteArray): String = BigInteger(1, MessageDigest.getInstance("MD5").digest(bytes)).toString(16)
