import CodeBridgeCore
import Foundation
import Network

final class CodeHTTPServer: @unchecked Sendable {
    enum ServerState: Sendable {
        case listening(port: UInt16)
        case failed(String)
        case stopped
    }

    /// Requests above this size are rejected instead of buffering forever.
    static let maximumRequestSize = 1_048_576

    var onReceive: (@Sendable (CodeEvent) -> Void)?
    var onStateChange: (@Sendable (ServerState) -> Void)?

    private let port: UInt16
    private let tokenProvider: @Sendable () -> String
    private let nameProvider: @Sendable () -> String
    private let queue = DispatchQueue(label: "dev.codebridge.http-server", qos: .userInitiated)
    private var listener: NWListener?

    init(
        port: UInt16,
        tokenProvider: @escaping @Sendable () -> String,
        nameProvider: @escaping @Sendable () -> String = { "Mac" }
    ) {
        self.port = port
        self.tokenProvider = tokenProvider
        self.nameProvider = nameProvider
    }

    func start() {
        let port = self.port
        guard let endpointPort = NWEndpoint.Port(rawValue: port) else {
            onStateChange?(.failed("Invalid port \(port)."))
            return
        }

        do {
            let listener = try NWListener(using: .tcp, on: endpointPort)
            listener.newConnectionHandler = { [weak self] connection in
                self?.handle(connection)
            }
            listener.stateUpdateHandler = { [weak self] state in
                switch state {
                case .ready:
                    self?.onStateChange?(.listening(port: port))
                case .failed(let error):
                    self?.onStateChange?(.failed(error.localizedDescription))
                case .cancelled:
                    self?.onStateChange?(.stopped)
                default:
                    break
                }
            }
            listener.start(queue: queue)
            self.listener = listener
        } catch {
            onStateChange?(.failed(error.localizedDescription))
        }
    }

    func stop() {
        listener?.cancel()
        listener = nil
    }

    private func handle(_ connection: NWConnection) {
        connection.start(queue: queue)
        receive(connection, buffer: Data())
    }

    /// Keeps reading until the parser sees a complete request (TCP may deliver
    /// a request in several chunks), then answers and closes.
    private func receive(_ connection: NWConnection, buffer: Data) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 65_536) { [weak self] data, _, isComplete, error in
            guard let self else {
                connection.cancel()
                return
            }

            var buffer = buffer
            if let data {
                buffer.append(data)
            }

            guard !buffer.isEmpty else {
                connection.cancel()
                return
            }

            if buffer.count > Self.maximumRequestSize {
                self.send(.badRequest, on: connection)
                return
            }

            if HTTPCodeRequestParser.isRequestComplete(buffer) {
                self.send(self.process(buffer), on: connection)
                return
            }

            if isComplete || error != nil {
                self.send(.badRequest, on: connection)
                return
            }

            self.receive(connection, buffer: buffer)
        }
    }

    private func send(_ response: HTTPResponse, on connection: NWConnection) {
        connection.send(content: response.data, completion: .contentProcessed { _ in
            connection.cancel()
        })
    }

    private func process(_ data: Data) -> HTTPResponse {
        do {
            let parsed = try HTTPCodeRequestParser.parse(data, expectedToken: tokenProvider())
            switch parsed {
            case .ping:
                return .pong(name: nameProvider())
            case .code(let request):
                let event = try request.payload.toEvent()
                onReceive?(event)
                return .accepted
            }
        } catch CodeBridgeError.unauthorized {
            return .unauthorized
        } catch {
            return .badRequest
        }
    }
}

private enum HTTPResponse {
    case accepted
    case badRequest
    case unauthorized
    case pong(name: String)

    var data: Data {
        let status: String
        let body: Data

        switch self {
        case .accepted:
            status = "202 Accepted"
            body = Data(#"{"ok":true}"#.utf8)
        case .badRequest:
            status = "400 Bad Request"
            body = Data(#"{"error":"bad_request"}"#.utf8)
        case .unauthorized:
            status = "401 Unauthorized"
            body = Data(#"{"error":"unauthorized"}"#.utf8)
        case .pong(let name):
            status = "200 OK"
            let payload = (try? JSONSerialization.data(withJSONObject: ["ok": true, "name": name]))
                ?? Data(#"{"ok":true}"#.utf8)
            body = payload
        }

        let raw = """
        HTTP/1.1 \(status)\r
        Content-Type: application/json\r
        Content-Length: \(body.count)\r
        Connection: close\r
        \r

        """
        var response = Data(raw.utf8)
        response.append(body)
        return response
    }
}
