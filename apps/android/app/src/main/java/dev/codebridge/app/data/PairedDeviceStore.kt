package dev.codebridge.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** A Mac this phone has paired with, stored after scanning its QR code. */
data class PairedMac(
    val id: String,
    val name: String,
    val host: String,
    val port: String,
    val token: String
) {
    companion object {
        fun makeId(host: String, port: String) = "$host:$port"
    }
}

/**
 * Persists the list of paired Macs. The currently active one is also written
 * through to [SettingsStore], which is what the SMS receiver reads when a code
 * arrives — so auto-connection switching works without touching that path.
 */
class PairedDeviceStore(context: Context) {
    private val prefs = context.getSharedPreferences("codebridge_devices", Context.MODE_PRIVATE)
    private val activeSettings = SettingsStore(context)

    fun devices(): List<PairedMac> {
        val raw = prefs.getString(KEY_DEVICES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val entry = array.optJSONObject(index) ?: return@mapNotNull null
                val host = entry.optString("host")
                val port = entry.optString("port")
                PairedMac(
                    id = entry.optString("id").ifEmpty { PairedMac.makeId(host, port) },
                    name = entry.optString("name").ifEmpty { "Mac" },
                    host = host,
                    port = port,
                    token = entry.optString("token")
                )
            }
        } catch (_: JSONException) {
            emptyList()
        }
    }

    fun upsert(device: PairedMac) {
        val others = devices().filterNot { it.id == device.id }
        save(others + device)
    }

    fun remove(id: String) {
        save(devices().filterNot { it.id == id })
        if (prefs.getString(KEY_ACTIVE, null) == id) {
            prefs.edit().remove(KEY_ACTIVE).apply()
        }
    }

    fun activeId(): String? = prefs.getString(KEY_ACTIVE, null)

    /**
     * Updates addresses of paired Macs that were found over mDNS under the
     * same device name but at a new host/port (IP changed), keeping the
     * active selection intact.
     */
    fun healAddresses(discovered: List<DiscoveredMac>) {
        val oldDevices = devices()
        val oldActive = activeId()
        val healed = oldDevices.map { device ->
            val match = discovered.firstOrNull { it.name == device.name } ?: return@map device
            val newPort = match.port.toString()
            if (match.host == device.host && newPort == device.port) {
                device
            } else {
                device.copy(
                    id = PairedMac.makeId(match.host, newPort),
                    host = match.host,
                    port = newPort
                )
            }
        }
        if (healed != oldDevices) {
            save(healed)
            val moved = oldDevices.zip(healed).firstOrNull { (old, new) -> old.id != new.id }
            if (moved != null && oldActive == moved.first.id) {
                prefs.edit().putString(KEY_ACTIVE, moved.second.id).apply()
            }
        }
    }

    data class DiscoveredMac(val name: String, val host: String, val port: Int)

    /** Makes [device] the active receiver for the SMS path. */
    fun activate(device: PairedMac) {
        activeSettings.save(
            CodeBridgeSettings(
                host = device.host,
                port = device.port,
                token = device.token,
                forwardingEnabled = activeSettings.read().forwardingEnabled
            )
        )
        prefs.edit().putString(KEY_ACTIVE, device.id).apply()
    }

    private fun save(devices: List<PairedMac>) {
        val array = JSONArray()
        devices.forEach { device ->
            array.put(
                JSONObject()
                    .put("id", device.id)
                    .put("name", device.name)
                    .put("host", device.host)
                    .put("port", device.port)
                    .put("token", device.token)
            )
        }
        prefs.edit().putString(KEY_DEVICES, array.toString()).apply()
    }

    private companion object {
        const val KEY_DEVICES = "devices"
        const val KEY_ACTIVE = "activeId"
    }
}
