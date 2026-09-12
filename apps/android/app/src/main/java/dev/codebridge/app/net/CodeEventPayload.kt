package dev.codebridge.app.net

data class CodeEventPayload(
    val code: String,
    val sender: String,
    val messagePreview: String,
    val receivedAt: String,
    val source: String,
    val confidence: Double
)

