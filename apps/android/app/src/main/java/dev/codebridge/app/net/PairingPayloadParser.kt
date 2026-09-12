package dev.codebridge.app.net

import dev.codebridge.app.data.PairedMac
import org.json.JSONException
import org.json.JSONObject

/** Parses the JSON payload encoded in the Mac's pairing QR code. */
object PairingPayloadParser {

    fun parse(text: String): PairedMac? {
        return try {
            val payload = JSONObject(text)
            if (payload.optString("service") != "codebridge") return null
            if (payload.optInt("v") != 1) return null

            val host = payload.optString("host").trim()
            val port = payload.optInt("port").takeIf { it in 1..65_535 }?.toString() ?: return null
            val token = payload.optString("token").trim()
            if (host.isEmpty() || token.isEmpty()) return null

            PairedMac(
                id = PairedMac.makeId(host, port),
                name = payload.optString("name").ifBlank { "Mac" },
                host = host,
                port = port,
                token = token
            )
        } catch (_: JSONException) {
            null
        }
    }
}
