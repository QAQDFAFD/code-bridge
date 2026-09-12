package dev.codebridge.app.data

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("codebridge", Context.MODE_PRIVATE)

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
}
