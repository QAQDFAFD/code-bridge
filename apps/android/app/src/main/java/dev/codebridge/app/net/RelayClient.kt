package dev.codebridge.app.net

import dev.codebridge.app.data.CodeBridgeSettings
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

class RelayClient(
    private val httpClient: OkHttpClient = defaultHttpClient()
) {
    fun send(settings: CodeBridgeSettings, payload: CodeEventPayload): Result<Unit> {
        if (!settings.isReady) {
            return Result.failure(IllegalStateException("Mac connection is not configured."))
        }

        val json = JSONObject()
            .put("code", payload.code)
            .put("sender", payload.sender)
            .put("messagePreview", payload.messagePreview)
            .put("receivedAt", payload.receivedAt)
            .put("source", payload.source)
            .put("confidence", payload.confidence)
            .toString()

        val body = json.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("http://${settings.host}:${settings.port}/v1/codes")
            .header("Authorization", "Bearer ${settings.token}")
            .post(body)
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("CodeBridge Mac returned HTTP ${response.code}."))
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    fun sendTest(settings: CodeBridgeSettings, code: String = "106284"): Result<Unit> {
        return send(
            settings,
            CodeEventPayload(
                code = code,
                sender = "Android Test",
                messagePreview = "Your verification code is $code.",
                receivedAt = OffsetDateTime.now().toString(),
                source = "manual",
                confidence = 1.0
            )
        )
    }

    companion object {
        // The SMS broadcast receiver has a short goAsync() window, so keep
        // timeouts well below 10 seconds instead of OkHttp's 10s defaults.
        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
    }
}
