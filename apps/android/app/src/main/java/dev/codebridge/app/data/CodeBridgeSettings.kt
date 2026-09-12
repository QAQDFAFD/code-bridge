package dev.codebridge.app.data

data class CodeBridgeSettings(
    val host: String = "",
    val port: String = "47821",
    val token: String = "change-me",
    val forwardingEnabled: Boolean = true
) {
    val isReady: Boolean
        get() = host.isNotBlank() && port.toIntOrNull() != null && token.isNotBlank()
}
