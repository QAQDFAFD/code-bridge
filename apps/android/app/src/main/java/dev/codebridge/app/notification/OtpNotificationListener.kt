package dev.codebridge.app.notification

import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.codebridge.app.data.PendingSendStore
import dev.codebridge.app.data.SettingsStore
import dev.codebridge.app.net.CodeEventPayload
import dev.codebridge.app.net.DeviceProbe
import dev.codebridge.app.net.RecentCodeGate
import dev.codebridge.app.net.RelayClient
import dev.codebridge.app.sms.OtpExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.OffsetDateTime

/**
 * Secondary code source: many services (chat apps, mail, banking) deliver
 * OTPs as notifications instead of SMS. Requires the user to enable
 * notification access for CodeBridge in system settings.
 */
class OtpNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        if (sbn.isOngoing) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (
            extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(android.app.Notification.EXTRA_TEXT)
            )?.toString().orEmpty()
        val message = listOf(title, text).filter { it.isNotBlank() }.joinToString(" ")
        if (message.isBlank()) return

        val otp = OtpExtractor.extract(message) ?: return
        if (!RecentCodeGate.markSeen(otp.code)) return

        val appContext = applicationContext
        val sender = senderLabel(appContext, sbn.packageName)
        val payload = CodeEventPayload(
            code = otp.code,
            sender = sender,
            messagePreview = message.take(160),
            receivedAt = OffsetDateTime.now().toString(),
            source = "notification",
            confidence = otp.confidence
        )

        scope.launch {
            val settings = SettingsStore(appContext).read()
            if (!settings.forwardingEnabled) return@launch
            val result = RelayClient().send(settings, payload)
            if (result.isFailure) {
                PendingSendStore(appContext).enqueue(payload)
                DeviceProbe.schedule(appContext)
            }
        }
    }

    private fun senderLabel(context: Context, packageName: String): String {
        if (packageName == "android") return "System"
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }
}
