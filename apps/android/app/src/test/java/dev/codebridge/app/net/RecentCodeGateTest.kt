package dev.codebridge.app.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentCodeGateTest {
    @Test
    fun firstSightingPassesAndImmediateDuplicateIsBlocked() {
        RecentCodeGate.reset()
        assertTrue(RecentCodeGate.markSeen("123456", nowMs = 1_000))
        assertFalse(RecentCodeGate.markSeen("123456", nowMs = 1_500))
    }

    @Test
    fun differentCodesDoNotInterfere() {
        RecentCodeGate.reset()
        assertTrue(RecentCodeGate.markSeen("123456", nowMs = 1_000))
        assertTrue(RecentCodeGate.markSeen("654321", nowMs = 1_200))
    }

    @Test
    fun windowExpiryAllowsSameCodeAgain() {
        RecentCodeGate.reset()
        assertTrue(RecentCodeGate.markSeen("123456", nowMs = 1_000))
        assertFalse(RecentCodeGate.markSeen("123456", nowMs = 50_000))
        assertTrue(RecentCodeGate.markSeen("123456", nowMs = 100_000))
    }
}
