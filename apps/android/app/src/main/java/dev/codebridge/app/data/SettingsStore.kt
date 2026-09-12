package dev.codebridge.app.data

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("codebridge", Context.MODE_PRIVATE)

    /**
     * User-confirmed OEM auto-start state. There is no public API to query it,
     * so we store what the user reported after visiting the system settings.
     * null = never asked.
     */
    var autostartConfirmed: Boolean?
        get() = if (prefs.contains(KEY_AUTOSTART)) prefs.getBoolean(KEY_AUTOSTART, false) else null
        set(value) {
            val editor = prefs.edit()
            if (value == null) {
                editor.remove(KEY_AUTOSTART)
            } else {
                editor.putBoolean(KEY_AUTOSTART, value)
            }
            editor.apply()
        }

    fun read(): CodeBridgeSettings {
        return CodeBridgeSettings(
            host = prefs.getString("host", "") ?: "",
            port = prefs.getString("port", "47821") ?: "47821",
            token = prefs.getString("token", "change-me") ?: "change-me",
            forwardingEnabled = prefs.getBoolean("forwardingEnabled", true)
        )
    }

    fun save(settings: CodeBridgeSettings) {
        prefs.edit()
            .putString("host", settings.host.trim())
            .putString("port", settings.port.trim())
            .putString("token", settings.token.trim())
            .putBoolean("forwardingEnabled", settings.forwardingEnabled)
            .apply()
    }

    private companion object {
        const val KEY_AUTOSTART = "autostartConfirmed"
    }
}
