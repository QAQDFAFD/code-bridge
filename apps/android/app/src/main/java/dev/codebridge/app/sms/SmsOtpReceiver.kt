package dev.codebridge.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import dev.codebridge.app.data.SettingsStore
import dev.codebridge.app.net.CodeEventPayload
import dev.codebridge.app.net.RelayClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.OffsetDateTime

class SmsOtpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        val sender = messages.firstOrNull()?.originatingAddress ?: "Unknown"
        val otp = OtpExtractor.extract(body) ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = SettingsStore(context.applicationContext).read()
                if (!settings.forwardingEnabled) return@launch
                RelayClient().send(
                    settings,
                    CodeEventPayload(
                        code = otp.code,
                        sender = sender,
                        messagePreview = body.take(160),
                        receivedAt = OffsetDateTime.now().toString(),
                        source = "sms",
                        confidence = otp.confidence
                    )
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
