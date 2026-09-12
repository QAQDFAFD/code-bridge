package dev.codebridge.app.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpExtractorTest {
    @Test
    fun extractsChineseVerificationCode() {
        val otp = OtpExtractor.extract("【支付宝】您的验证码是 482913，5分钟内有效。")
        assertEquals("482913", otp?.code)
    }

    @Test
    fun extractsEnglishVerificationCode() {
        val otp = OtpExtractor.extract("Your GitHub verification code is 106284.")
        assertEquals("106284", otp?.code)
    }

    @Test
    fun ignoresMessagesWithoutCodes() {
        val otp = OtpExtractor.extract("Your package has been delivered.")
        assertNull(otp)
    }

    @Test
    fun ignoresLongNumbersSuchAsPhoneNumbers() {
        val otp = OtpExtractor.extract("Contact us at 13800138000 for support.")
        assertNull(otp)
    }

    @Test
    fun prefersCodeClosestToKeyword() {
        val otp = OtpExtractor.extract("Order 20240712 confirmed. Your verification code is 582031.")
        assertEquals("582031", otp?.code)
    }

    @Test
    fun extractsAlphanumericCode() {
        val otp = OtpExtractor.extract("Your code is A1B2C3. Do not share it.")
        assertEquals("A1B2C3", otp?.code)
    }

    @Test
    fun extractsShortChineseCodeWithPunctuation() {
        val otp = OtpExtractor.extract("验证码：5821，请勿泄露给他人。")
        assertEquals("5821", otp?.code)
    }

    @Test
    fun assignsHighConfidenceWhenCodeIsNextToKeyword() {
        val otp = OtpExtractor.extract("【支付宝】您的验证码是 482913，5分钟内有效。")
        assertEquals(0.98, otp!!.confidence, 1e-9)
    }

    @Test
    fun assignsLowerConfidenceWithoutAnyKeyword() {
        val otp = OtpExtractor.extract("Your flight BA1234 departs soon.")
        assertEquals("BA1234", otp?.code)
        assertEquals(0.55, otp!!.confidence, 1e-9)
    }

    @Test
    fun returnsNullForBlankMessage() {
        assertNull(OtpExtractor.extract("   "))
    }
}

