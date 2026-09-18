package dev.codebridge.app.data

import dev.codebridge.app.net.CodeEventPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingSendCodecTest {
    private val payload = CodeEventPayload(
        code = "106284",
        sender = "GitHub",
        messagePreview = "Your code is 106284.",
        receivedAt = "2026-09-18T10:00:00+08:00",
        source = "sms",
        confidence = 0.98
    )

    @Test
    fun roundTripsPayloads() {
        val encoded = PendingSendCodec.encode(listOf(payload))
        val decoded = PendingSendCodec.decode(encoded)

        assertEquals(listOf(payload), decoded)
    }

    @Test
    fun decodesEmptyAndCorruptInputToEmptyList() {
        assertEquals(emptyList<CodeEventPayload>(), PendingSendCodec.decode(null))
        assertEquals(emptyList<CodeEventPayload>(), PendingSendCodec.decode(""))
        assertEquals(emptyList<CodeEventPayload>(), PendingSendCodec.decode("not-json"))
    }

    @Test
    fun decodesLegacyEntriesWithDefaults() {
        val decoded = PendingSendCodec.decode("""[{"code":"123456"}]""")

        assertEquals(1, decoded.size)
        assertEquals("123456", decoded.first().code)
        assertEquals("Unknown", decoded.first().sender)
        assertEquals("sms", decoded.first().source)
    }

    @Test
    fun malformedEntriesAreSkipped() {
        val decoded = PendingSendCodec.decode("""[{"code":"123456"}, 42]""")

        assertEquals(1, decoded.size)
        assertTrue(decoded.first().code == "123456")
    }
}
