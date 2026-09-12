import CodeBridgeCore
import Foundation
import Network
import Testing

@testable import CodeBridgeMac

/// End-to-end tests that drive the real CodeHTTPServer over TCP on the
/// loopback interface: connection → auth → parse → validation → event →
/// response, mirroring what the Android app does on the LAN.
@Suite("CodeHTTPServer end-to-end over TCP")
struct CodeHTTPServerEndToEndTests {
    private let token = "e2e-token"

    @Test func acceptsAuthorizedRequestAndDeliversEvent() async throws {
        let events = EventBox()
        let (server, port) = try await startServer { events.append($0) }
        defer { server.stop() }

        let response = try await performRequest(
            port: port,
            authorization: "Bearer \(token)",
            body: #"{"code":"106284","sender":"GitHub","source":"manual"}"#
        )

        #expect(response.contains("202 Accepted"))
        #expect(response.contains(#"{"ok":true}"#))
        #expect(events.events.map(\.code) == ["106284"])
        #expect(events.events.first?.sender == "GitHub")
        #expect(events.events.first?.source == "manual")
    }

    @Test func rejectsWrongTokenWith401() async throws {
        let events = EventBox()
        let (server, port) = try await startServer { events.append($0) }
        defer { server.stop() }

        let response = try await performRequest(
            port: port,
            authorization: "Bearer wrong-token",
            body: #"{"code":"106284"}"#
        )

        #expect(response.contains("401 Unauthorized"))
        #expect(response.contains(#"{"error":"unauthorized"}"#))
        #expect(events.events.isEmpty)
    }

    @Test func rejectsInvalidCodeWith400() async throws {
        let events = EventBox()
        let (server, port) = try await startServer { events.append($0) }
        defer { server.stop() }

        let response = try await performRequest(
            port: port,
            authorization: "Bearer \(token)",
            body: #"{"code":"12"}"#
        )

        #expect(response.contains("400 Bad Request"))
        #expect(events.events.isEmpty)
    }

    /// Regression test for split TCP delivery: the request arrives as two
    /// separate sends with a delay between them.
    @Test func acceptsRequestSplitAcrossTCPSegments() async throws {
        let events = EventBox()
        let (server, port) = try await startServer { events.append($0) }
        defer { server.stop() }

        let response = try await performRequest(
            port: port,
            authorization: "Bearer \(token)",
            body: #"{"code":"582031","sender":"Alipay","source":"sms"}"#,
            splitDelay: 0.2
        )

        #expect(response.contains("202 Accepted"))
        #expect(events.events.map(\.code) == ["582031"])
        #expect(events.events.first?.sender == "Alipay")
    }

    // MARK: - Helpers

    private func startServer(
        onEvent: @escaping @Sendable (CodeEvent) -> Void
    ) async throws -> (server: CodeHTTPServer, port: UInt16) {
        let port = UInt16.random(in: 48_000...48_999)
        let server = CodeHTTPServer(port: port, tokenProvider: { token })
        server.onReceive = onEvent

        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            let once = Once<Void>(continuation)
            DispatchQueue.global().asyncAfter(deadline: .now() + 10) {
                once.resumeThrowing(TimeoutError())
            }
            server.onStateChange = { state in
                switch state {
                case .listening:
                    once.resumeReturning(())
                case .failed(let reason):
                    once.resumeThrowing(ServerStartError(reason: reason))
                case .stopped:
                    break
                }
            }
            server.start()
        }

        return (server, port)
    }

    private func performRequest(
        port: UInt16,
        authorization: String,
        body: String,
        splitDelay: TimeInterval? = nil
    ) async throws -> String {
        try await withCheckedThrowingContinuation { continuation in
            SingleRequestClient(
                continuation: continuation,
                port: port,
                authorization: authorization,
                body: body,
                splitDelay: splitDelay
            )
        }
    }
}

/// Minimal one-shot HTTP client used by the tests above. Kept alive by the
/// connection's state handler until the response arrives or the timeout fires.
private final class SingleRequestClient: @unchecked Sendable {
    private let once: Once<String>
    private let queue = DispatchQueue(label: "dev.codebridge.e2e-client")
    private let connection: NWConnection
    private let authorization: String
    private let body: String
    private let splitDelay: TimeInterval?
    private var buffer = Data()

    init(
        continuation: CheckedContinuation<String, Error>,
        port: UInt16,
        authorization: String,
        body: String,
        splitDelay: TimeInterval?,
        timeout: TimeInterval = 10
    ) {
        once = Once(continuation)
        connection = NWConnection(
            host: "127.0.0.1",
            port: NWEndpoint.Port(rawValue: port)!,
            using: .tcp
        )
        self.authorization = authorization
        self.body = body
        self.splitDelay = splitDelay

        // The client keeps itself alive through the connection's handler and
        // these closures; teardown() breaks the cycle once a response (or a
        // timeout) has been delivered.
        queue.asyncAfter(deadline: .now() + timeout) {
            self.finish(throwing: TimeoutError())
        }
        connection.start(queue: queue)
        connection.stateUpdateHandler = { state in
            switch state {
            case .ready:
                self.sendRequest()
                self.receiveNext()
            case .failed(let error):
                self.finish(throwing: error)
            default:
                break
            }
        }
    }

    private func sendRequest() {
        let head = """
        POST /v1/codes HTTP/1.1\r
        Host: 127.0.0.1\r
        Authorization: \(authorization)\r
        Content-Type: application/json\r
        Content-Length: \(body.utf8.count)\r
        Connection: close\r
        \r

        """

        if let splitDelay {
            connection.send(content: Data(head.utf8), completion: .contentProcessed { _ in })
            queue.asyncAfter(deadline: .now() + splitDelay) {
                self.connection.send(content: Data(self.body.utf8), completion: .contentProcessed { _ in })
            }
        } else {
            connection.send(content: Data((head + body).utf8), completion: .contentProcessed { _ in })
        }
    }

    private func receiveNext() {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 65_536) { data, _, isComplete, error in
            if let data {
                self.buffer.append(data)
            }
            if isComplete || error != nil {
                self.finish(returning: String(decoding: self.buffer, as: UTF8.self))
                return
            }
            self.receiveNext()
        }
    }

    private func finish(returning text: String) {
        teardown()
        once.resumeReturning(text)
    }

    private func finish(throwing error: Error) {
        teardown()
        once.resumeThrowing(error)
    }

    private func teardown() {
        connection.stateUpdateHandler = nil
        connection.cancel()
    }
}

/// Resumes a continuation exactly once; late callbacks are ignored.
private final class Once<T: Sendable>: @unchecked Sendable {
    private let lock = NSLock()
    private var continuation: CheckedContinuation<T, Error>?

    init(_ continuation: CheckedContinuation<T, Error>) {
        self.continuation = continuation
    }

    func resumeReturning(_ value: T) {
        let continuation = take()
        continuation?.resume(returning: value)
    }

    func resumeThrowing(_ error: Error) {
        let continuation = take()
        continuation?.resume(throwing: error)
    }

    private func take() -> CheckedContinuation<T, Error>? {
        lock.lock()
        defer { lock.unlock() }
        let continuation = self.continuation
        self.continuation = nil
        return continuation
    }
}

/// Thread-safe collector for events handed to `onReceive`.
private final class EventBox: @unchecked Sendable {
    private let lock = NSLock()
    private var values: [CodeEvent] = []

    func append(_ event: CodeEvent) {
        lock.lock()
        values.append(event)
        lock.unlock()
    }

    var events: [CodeEvent] {
        lock.lock()
        defer { lock.unlock() }
        return values
    }
}

private struct TimeoutError: Error {}

private struct ServerStartError: Error {
    let reason: String
}
