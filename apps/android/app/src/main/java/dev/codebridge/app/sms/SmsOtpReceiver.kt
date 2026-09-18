package dev.codebridge.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import dev.codebridge.app.data.PendingSendStore
import dev.codebridge.app.data.SettingsStore
import dev.codebridge.app.net.CodeEventPayload
import dev.codebridge.app.net.DeviceProbe
import dev.codebridge.app.net.RelayClient
import dev.codebridge.app.net.RecentCodeGate
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
                val appContext = context.applicationContext
                val settings = SettingsStore(appContext).read()
                if (!settings.forwardingEnabled) return@launch
                val payload = CodeEventPayload(
                    code = otp.code,
                    sender = sender,
                    messagePreview = body.take(160),
                    receivedAt = OffsetDateTime.now().toString(),
                    source = "sms",
                    confidence = otp.confidence
                )
                if (!RecentCodeGate.markSeen(otp.code)) return@launch
                val result = RelayClient().send(settings, payload)
                if (result.isFailure) {
                    // Keep the code for retry as soon as the Mac is reachable again.
                    PendingSendStore(appContext).enqueue(payload)
                    DeviceProbe.schedule(appContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
