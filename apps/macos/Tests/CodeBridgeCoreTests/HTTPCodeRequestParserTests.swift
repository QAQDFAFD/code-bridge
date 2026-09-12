import CodeBridgeCore
import Foundation
import Testing

@Test func parserAcceptsValidAuthorizedCodeRequest() throws {
    let request = """
    POST /v1/codes HTTP/1.1\r
    Host: 127.0.0.1\r
    Authorization: Bearer secret\r
    Content-Type: application/json\r
    \r
    {"code":"106284","sender":"GitHub","source":"manual","confidence":1.0}
    """

    let parsed = try HTTPCodeRequestParser.parse(Data(request.utf8), expectedToken: "secret")
    #expect(parsed.payload.code == "106284")
    #expect(parsed.payload.sender == "GitHub")
}

@Test func parserRejectsInvalidToken() {
    let request = """
    POST /v1/codes HTTP/1.1\r
    Host: 127.0.0.1\r
    Authorization: Bearer wrong\r
    Content-Type: application/json\r
    \r
    {"code":"106284"}
    """

    #expect(throws: CodeBridgeError.unauthorized) {
        try HTTPCodeRequestParser.parse(Data(request.utf8), expectedToken: "secret")
    }
}

@Test func parserDetectsSplitRequests() throws {
    let request = """
    POST /v1/codes HTTP/1.1\r
    Host: 127.0.0.1\r
    Authorization: Bearer secret\r
    Content-Length: 23\r
    \r
    {"code":"106284","x":1}
    """

    let full = Data(request.utf8)
    let head = full.prefix(full.count - 8)
    #expect(!HTTPCodeRequestParser.isRequestComplete(head))
    #expect(HTTPCodeRequestParser.isRequestComplete(full))

    let parsed = try HTTPCodeRequestParser.parse(full, expectedToken: "secret")
    #expect(parsed.payload.code == "106284")
}

@Test func parserRejectsCodeThatFailsValidation() {
    let request = """
    POST /v1/codes HTTP/1.1\r
    Host: 127.0.0.1\r
    Authorization: Bearer secret\r
    Content-Type: application/json\r
    \r
    {"code":"abc!"}
    """

    #expect(throws: CodeBridgeError.invalidCode) {
        try HTTPCodeRequestParser.parse(Data(request.utf8), expectedToken: "secret")
    }
}

