package com.reiniertutoriales.vireolumatv.activity.main.cast

import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID
import kotlin.random.Random

/**
 * Query parameters of a lounge screen session. Ported from yt-cast-receiver's `BindParams`
 * (MIT, Patrick Kan; see NOTICE.md).
 */
internal class LoungeBindParams(
    val id: String,
    val name: String,
    val app: String,
    val theme: String,
    brand: String,
    model: String,
) {
    var loungeIdToken: String? = null
    var sid: String? = null
    var gsessionId: String? = null
    var rid: Int = generateRid()
    var aid: Int = INITIAL_AID

    private val deviceInfo = JSONObject()
        .put("year", 0)
        .put("os", "Windows")
        .put("osVersion", "10.0")
        .put("chipset", "")
        .put("clientName", "TVHTML5")
        .put("dialAdditionalDataSupportLevel", "unsupported")
        .put("mdxDialServerType", "MDX_DIAL_SERVER_TYPE_UNKNOWN")
        .put("brand", brand)
        .put("model", model)
        .toString()

    fun reset() {
        sid = null
        loungeIdToken = null
        gsessionId = null
        rid = generateRid()
        aid = INITIAL_AID
    }

    /** Keeps SID, gsessionid and the highest AID seen, as the reference does for every incoming message. */
    fun updateWith(message: LoungeMessage) {
        when (message.name) {
            "c" -> (message.payload as? org.json.JSONArray)?.optString(0)?.takeIf { it.isNotEmpty() }?.let { sid = it }
            "S" -> (message.payload as? String)?.takeIf { it.isNotEmpty() }?.let { gsessionId = it }
        }
        val messageAid = message.aid
        if (messageAid != null && messageAid != 0) aid = maxOf(aid, messageAid)
    }

    fun canPoll(): Boolean = loungeIdToken != null && sid != null && gsessionId != null

    fun initSessionQuery(): String {
        val params = common() + listOf("deviceInfo" to deviceInfo, "RID" to rid.toString(), "CVER" to "1")
        rid++
        return encode(params)
    }

    fun sendMessageQuery(messageAid: Int?): String {
        aid = if (messageAid != null && messageAid != 0) maxOf(messageAid, aid) else aid
        val params = common() + listOf(
            "deviceInfo" to deviceInfo,
            "SID" to sid.orEmpty(),
            "RID" to rid.toString(),
            "AID" to aid.toString(),
            "gsessionid" to gsessionId.orEmpty(),
        )
        if (messageAid == null || messageAid == 0) aid++
        rid++
        return encode(params)
    }

    fun rpcQuery(): String = encode(
        common() + listOf(
            "RID" to "rpc",
            "SID" to sid.orEmpty(),
            "CI" to "0",
            "AID" to aid.toString(),
            "gsessionid" to gsessionId.orEmpty(),
            "TYPE" to "xmlhttp",
        ),
    )

    private fun common(): List<Pair<String, String>> = listOfNotNull(
        "device" to "LOUNGE_SCREEN",
        "id" to id,
        "obfuscatedGaiaId" to "",
        "name" to name,
        "app" to app,
        "theme" to theme,
        "capabilities" to "dsp,mic,dpa,ntb",
        "cst" to "m",
        "mdxVersion" to "2",
        loungeIdToken?.let { "loungeIdToken" to it },
        "VER" to "8",
        "v" to "2",
        "zx" to UUID.randomUUID().toString().replace("-", "").take(12),
        "t" to "1",
    )

    internal companion object {
        private const val INITIAL_AID = 3

        private fun generateRid(): Int = Random.nextInt(41_000, 50_000)

        fun encode(params: List<Pair<String, String>>): String =
            params.joinToString("&") { (k, v) -> "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}" }
    }
}
