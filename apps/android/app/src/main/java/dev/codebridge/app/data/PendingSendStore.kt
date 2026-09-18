package dev.codebridge.app.data

import android.content.Context
import android.content.SharedPreferences
import dev.codebridge.app.net.CodeEventPayload
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Local queue for code payloads that failed to reach the Mac (offline,
 * network flap). Drained by [dev.codebridge.app.net.DeviceProbeWorker]
 * whenever a paired Mac becomes reachable again — so codes are retried
 * instead of silently dropped.
 */
class PendingSendStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("codebridge_pending", Context.MODE_PRIVATE)

    fun all(): List<CodeEventPayload> = PendingSendCodec.decode(prefs.getString(KEY, null))

    fun enqueue(payload: CodeEventPayload) {
        val updated = (all() + payload).takeLast(MAX_PENDING)
        prefs.edit().putString(KEY, PendingSendCodec.encode(updated)).apply()
    }

    fun remove(payload: CodeEventPayload) {
        val remaining = all().filterNot { it == payload }
        prefs.edit().putString(KEY, PendingSendCodec.encode(remaining)).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "pending"
        const val MAX_PENDING = 50
    }
}

/** Pure JSON (de)serialization so the format stays unit-testable on the JVM. */
object PendingSendCodec {
    fun encode(payloads: List<CodeEventPayload>): String {
        val array = JSONArray()
        payloads.forEach { payload ->
            array.put(
                JSONObject()
                    .put("code", payload.code)
                    .put("sender", payload.sender)
                    .put("messagePreview", payload.messagePreview)
                    .put("receivedAt", payload.receivedAt)
                    .put("source", payload.source)
                    .put("confidence", payload.confidence)
            )
        }
        return array.toString()
    }

    fun decode(raw: String?): List<CodeEventPayload> {
        if (raw.isNullOrEmpty()) return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val entry = array.optJSONObject(index) ?: return@mapNotNull null
                CodeEventPayload(
                    code = entry.optString("code"),
                    sender = entry.optString("sender", "Unknown"),
                    messagePreview = entry.optString("messagePreview"),
                    receivedAt = entry.optString("receivedAt"),
                    source = entry.optString("source", "sms"),
                    confidence = entry.optDouble("confidence", 0.5)
                )
            }
        } catch (_: JSONException) {
            emptyList()
        }
    }
}
