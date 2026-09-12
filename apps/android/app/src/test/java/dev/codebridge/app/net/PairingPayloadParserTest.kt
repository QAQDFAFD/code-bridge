package dev.codebridge.app.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PairingPayloadParserTest {
    @Test
    fun parsesValidPairingPayload() {
        val qr = """
            {"v":1,"service":"codebridge","name":"Mac-mini","host":"192.168.0.103",
             "port":47821,"token":"0cab1f6df558ed50"}
        """.trimIndent()

        val device = PairingPayloadParser.parse(qr)

        assertNotNull(device)
        device!!
        assertEquals("Mac-mini", device.name)
        assertEquals("192.168.0.103", device.host)
        assertEquals("47821", device.port)
        assertEquals("0cab1f6df558ed50", device.token)
        assertEquals("192.168.0.103:47821", device.id)
    }

    @Test
    fun defaultsBlankNameToMac() {
        val device = PairingPayloadParser.parse(
            """{"v":1,"service":"codebridge","name":"","host":"192.168.0.103","port":47821,"token":"t"}"""
        )
        assertEquals("Mac", device?.name)
    }

    @Test
    fun rejectsForeignQRContent() {
        assertNull(PairingPayloadParser.parse("""{"url":"https://example.com"}"""))
        assertNull(PairingPayloadParser.parse("hello world"))
        assertNull(PairingPayloadParser.parse("""{"v":2,"service":"codebridge","host":"h","port":1,"token":"t"}"""))
        assertNull(PairingPayloadParser.parse("""{"v":1,"service":"other","host":"h","port":1,"token":"t"}"""))
    }

    @Test
    fun rejectsIncompletePayloads() {
        assertNull(PairingPayloadParser.parse("""{"v":1,"service":"codebridge","port":47821,"token":"t"}"""))
        assertNull(PairingPayloadParser.parse("""{"v":1,"service":"codebridge","host":"h","token":"t"}"""))
        assertNull(PairingPayloadParser.parse("""{"v":1,"service":"codebridge","host":"h","port":47821}"""))
    }
}
