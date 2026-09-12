import CodeBridgeCore
import Foundation
import Testing

@Test func payloadNormalizesCodeAndFillsDefaults() throws {
    let event = try IncomingCodePayload(code: "  106284 \n", sender: "   ", receivedAt: nil).toEvent(
        now: Date(timeIntervalSince1970: 100)
    )

    #expect(event.code == "106284")
    #expect(event.sender == "Unknown")
    #expect(event.source == "sms")
    #expect(event.receivedAt == Date(timeIntervalSince1970: 100))
    #expect(event.id.isEmpty == false)
}

@Test func payloadKeepsExplicitValues() throws {
    let event = try IncomingCodePayload(
        id: "client-1",
        code: "A1B2C3",
        sender: "GitHub",
        messagePreview: "Your code is A1B2C3.",
        receivedAt: Date(timeIntervalSince1970: 200),
        source: "manual",
        confidence: 0.9
    ).toEvent()

    #expect(event.id == "client-1")
    #expect(event.code == "A1B2C3")
    #expect(event.sender == "GitHub")
    #expect(event.messagePreview == "Your code is A1B2C3.")
    #expect(event.receivedAt == Date(timeIntervalSince1970: 200))
    #expect(event.source == "manual")
    #expect(event.confidence == 0.9)
}

@Test func payloadRejectsInvalidCodes() {
    let invalidCodes = [
        "123",            // too short
        "12345678901",    // too long
        "12 34",          // inner whitespace
        "abc!",           // special character
        "验证码",           // non-ASCII
        ""                // empty
    ]

    for code in invalidCodes {
        #expect(throws: CodeBridgeError.invalidCode) {
            _ = try IncomingCodePayload(code: code).toEvent()
        }
    }
}

@Test func payloadDecodesISO8601Timestamp() throws {
    let json = #"{"code":"106284","receivedAt":"2026-07-04T12:00:00+08:00"}"#
    let decoder = JSONDecoder()
    decoder.dateDecodingStrategy = .iso8601

    let payload = try decoder.decode(IncomingCodePayload.self, from: Data(json.utf8))
    let event = try payload.toEvent()

    #expect(payload.receivedAt != nil)
    #expect(event.receivedAt == payload.receivedAt)
}
