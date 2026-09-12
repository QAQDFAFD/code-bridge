package dev.codebridge.app.net

import dev.codebridge.app.data.CodeBridgeSettings
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end tests for [RelayClient] against a real local HTTP server
 * (MockWebServer), covering the full client path the macOS receiver sees:
 * request line, auth header, JSON body, and response handling.
 */
class RelayClientTest {
    private val server = MockWebServer()
    private val client = RelayClient()

    private val payload = CodeEventPayload(
        code = "106284",
        sender = "GitHub",
        messagePreview = "Your GitHub verification code is 106284.",
        receivedAt = "2026-09-12T10:00:00+08:00",
        source = "manual",
        confidence = 1.0
    )

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun settingsFor(token: String = "secret") = CodeBridgeSettings(
        host = "127.0.0.1",
        port = server.port.toString(),
        token = token,
        forwardingEnabled = true
    )

    @Test
    fun sendsAuthorizedPostWithExpectedPathAndBody() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(202).setBody("""{"ok":true}"""))
        server.start()

        val result = client.send(settingsFor(), payload)

        assertTrue("expected success, got $result", result.isSuccess)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/v1/codes", recorded.path)
        assertEquals("Bearer secret", recorded.getHeader("Authorization"))
        assertEquals("application/json; charset=utf-8", recorded.getHeader("Content-Type"))

        val body = JSONObject(recorded.body.readUtf8())
        assertEquals("106284", body.getString("code"))
        assertEquals("GitHub", body.getString("sender"))
        assertEquals("Your GitHub verification code is 106284.", body.getString("messagePreview"))
        assertEquals("2026-09-12T10:00:00+08:00", body.getString("receivedAt"))
        assertEquals("manual", body.getString("source"))
        assertEquals(1.0, body.getDouble("confidence"), 1e-9)
    }

    @Test
    fun failsWithServerStatusCodeOnUnauthorized() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"unauthorized"}"""))
        server.start()

        val result = client.send(settingsFor(token = "wrong"), payload)

        assertTrue(result.isFailure)
        assertEquals("CodeBridge Mac returned HTTP 401.", result.exceptionOrNull()?.message)
    }

    @Test
    fun failsWithServerStatusCodeOnBadRequest() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"bad_request"}"""))
        server.start()

        val result = client.send(settingsFor(), payload)

        assertTrue(result.isFailure)
        assertEquals("CodeBridge Mac returned HTTP 400.", result.exceptionOrNull()?.message)
    }

    @Test
    fun failsWithoutNetworkWhenSettingsIncomplete() = runBlocking {
        val incomplete = CodeBridgeSettings(host = "", port = "47821", token = "secret")

        val result = client.send(incomplete, payload)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun pingReturnsDeviceName() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true,"name":"Mac-mini"}"""))
        server.start()

        val result = client.ping(settingsFor())

        assertTrue("expected success, got $result", result.isSuccess)
        assertEquals("Mac-mini", result.getOrThrow())

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/v1/ping", recorded.path)
        assertEquals("Bearer secret", recorded.getHeader("Authorization"))
    }

    @Test
    fun pingFailsOnUnauthorized() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"unauthorized"}"""))
        server.start()

        val result = client.ping(settingsFor(token = "wrong"))

        assertTrue(result.isFailure)
        assertEquals("Token rejected (HTTP 401).", result.exceptionOrNull()?.message)
    }

    @Test
    fun pingFailsWithoutNetworkWhenSettingsIncomplete() {
        val incomplete = CodeBridgeSettings(host = "", port = "47821", token = "secret")

        val result = client.ping(incomplete)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }
}
