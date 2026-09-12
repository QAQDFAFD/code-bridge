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
    guard case .code(let codeRequest) = parsed else {
        Issue.record("expected .code, got \(parsed)")
        return
    }
    #expect(codeRequest.payload.code == "106284")
    #expect(codeRequest.payload.sender == "GitHub")
}

@Test func parserAcceptsAuthorizedPingRequest() throws {
    let request = """
    GET /v1/ping HTTP/1.1\r
    Host: 127.0.0.1\r
    Authorization: Bearer secret\r
    \r

    """

    let parsed = try HTTPCodeRequestParser.parse(Data(request.utf8), expectedToken: "secret")
    #expect(parsed == .ping)
}

@Test func parserRejectsPingWithWrongToken() {
    let request = """
    GET /v1/ping HTTP/1.1\r
    Host: 127.0.0.1\r
    Authorization: Bearer wrong\r
    \r

    """

    #expect(throws: CodeBridgeError.unauthorized) {
        try HTTPCodeRequestParser.parse(Data(request.utf8), expectedToken: "secret")
    }
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
    guard case .code(let codeRequest) = parsed else {
        Issue.record("expected .code, got \(parsed)")
        return
    }
    #expect(codeRequest.payload.code == "106284")
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

@Test func parserRejectsMissingAuthorizationHeader() {
    let request = """
    POST /v1/codes HTTP/1.1\r
    Host: 127.0.0.1\r
    Content-Type: application/json\r
    \r
    {"code":"106284"}
    """

    #expect(throws: CodeBridgeError.unauthorized) {
        try HTTPCodeRequestParser.parse(Data(request.utf8), expectedToken: "secret")
    }
}

@Test func parserRejectsWrongPath() {
    let request = """
    POST /v1/other HTTP/1.1\r
    Host: 127.0.0.1\r
    Authorization: Bearer secret\r
    \r
    {"code":"106284"}
    """

    #expect(throws: CodeBridgeError.invalidRequest) {
        try HTTPCodeRequestParser.parse(Data(request.utf8), expectedToken: "secret")
    }
}

@Test func parserRejectsNonPostMethod() {
    let request = """
    GET /v1/codes HTTP/1.1\r
    Host: 127.0.0.1\r
    Authorization: Bearer secret\r
    \r
    {"code":"106284"}
    """

    #expect(throws: CodeBridgeError.invalidRequest) {
        try HTTPCodeRequestParser.parse(Data(request.utf8), expectedToken: "secret")
    }
}

@Test func parserRejectsMalformedJSONBody() {
    let request = """
    POST /v1/codes HTTP/1.1\r
    Host: 127.0.0.1\r
    Authorization: Bearer secret\r
    Content-Type: application/json\r
    \r
    not-json
    """

    #expect(throws: CodeBridgeError.invalidJSON) {
        try HTTPCodeRequestParser.parse(Data(request.utf8), expectedToken: "secret")
    }
}

@Test func requestWithoutBodyIsCompleteOnceHeadersEnd() {
    let headersOnly = """
    POST /v1/codes HTTP/1.1\r
    Host: 127.0.0.1\r
    Authorization: Bearer secret\r
    \r

    """
    let partialHeader = String(headersOnly.prefix(20))

    #expect(HTTPCodeRequestParser.isRequestComplete(Data(headersOnly.utf8)))
    #expect(!HTTPCodeRequestParser.isRequestComplete(Data(partialHeader.utf8)))
    #expect(!HTTPCodeRequestParser.isRequestComplete(Data()))
}

