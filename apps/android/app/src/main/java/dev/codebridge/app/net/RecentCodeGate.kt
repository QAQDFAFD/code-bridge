package dev.codebridge.app.net

import java.util.concurrent.ConcurrentHashMap

/**
 * Suppresses duplicate forwarding of the same code within a short window —
 * e.g. a bank that triggers both an SMS and a push notification for the
 * same OTP, or a chatty notification that re-posts itself.
 */
object RecentCodeGate {
    private const val WINDOW_MS = 90_000L

    private val seenAt = ConcurrentHashMap<String, Long>()

    /**
     * Returns true if [code] had not been seen recently (and should be
     * forwarded), false when it is a duplicate within the window.
     */
    @Synchronized
    fun markSeen(code: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        seenAt.entries.removeAll { nowMs - it.value > WINDOW_MS }
        val last = seenAt[code]
        if (last != null && nowMs - last <= WINDOW_MS) {
            return false
        }
        seenAt[code] = nowMs
        return true
    }

    @Synchronized
    fun reset() {
        seenAt.clear()
    }
}
