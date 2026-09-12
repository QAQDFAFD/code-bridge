package dev.codebridge.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeBridgeSettingsTest {
    @Test
    fun isReadyWithHostPortAndToken() {
        assertTrue(CodeBridgeSettings("192.168.1.8", "47821", "abc123").isReady)
    }

    @Test
    fun notReadyWithoutHost() {
        assertFalse(CodeBridgeSettings(host = "", port = "47821", token = "abc123").isReady)
    }

    @Test
    fun notReadyWithNonNumericPort() {
        assertFalse(CodeBridgeSettings("192.168.1.8", "http", "abc123").isReady)
    }

    @Test
    fun notReadyWithBlankToken() {
        assertFalse(CodeBridgeSettings("192.168.1.8", "47821", "   ").isReady)
    }
}
