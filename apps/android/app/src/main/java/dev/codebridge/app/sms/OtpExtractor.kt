package dev.codebridge.app.sms

data class ExtractedOtp(
    val code: String,
    val confidence: Double
)

object OtpExtractor {
    private val keywords = listOf(
        "验证码",
        "校验码",
        "动态码",
        "verification",
        "verify",
        "code",
        "otp"
    )

    private val codePattern = Regex("""(?<!\d)[A-Za-z0-9]{4,8}(?!\d)""")

    fun extract(message: String): ExtractedOtp? {
        val normalized = message.trim()
        if (normalized.isEmpty()) return null

        val keywordIndex = keywords
            .mapNotNull { keyword ->
                val index = normalized.indexOf(keyword, ignoreCase = true)
                if (index >= 0) index else null
            }
            .minOrNull()

        val candidates = codePattern.findAll(normalized)
            .map { match ->
                val value = match.value
                val distance = keywordIndex?.let { kotlin.math.abs(match.range.first - it) } ?: 999
                value to distance
            }
            .filter { (value, _) -> value.any(Char::isDigit) }
            .filterNot { (value, _) -> looksLikePhoneNumber(value, normalized) }
            .toList()

        if (candidates.isEmpty()) return null

        val best = candidates.minBy { it.second }
        val confidence = when {
            keywordIndex == null -> 0.55
            best.second <= 24 -> 0.98
            best.second <= 60 -> 0.85
            else -> 0.68
        }

        return ExtractedOtp(code = best.first, confidence = confidence)
    }

    private fun looksLikePhoneNumber(value: String, message: String): Boolean {
        if (value.length < 8) return false
        val index = message.indexOf(value)
        val before = message.getOrNull(index - 1)
        val after = message.getOrNull(index + value.length)
        return before?.isDigit() == true || after?.isDigit() == true
    }
}

