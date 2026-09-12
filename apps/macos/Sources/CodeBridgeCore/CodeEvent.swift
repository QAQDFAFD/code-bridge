import Foundation

public struct CodeEvent: Codable, Equatable, Identifiable, Sendable {
    public let id: String
    public let code: String
    public let sender: String
    public let messagePreview: String?
    public let receivedAt: Date
    public let source: String
    public let confidence: Double?

    public init(
        id: String = UUID().uuidString,
        code: String,
        sender: String = "Unknown",
        messagePreview: String? = nil,
        receivedAt: Date = Date(),
        source: String = "manual",
        confidence: Double? = nil
    ) {
        self.id = id
        self.code = code
        self.sender = sender.isEmpty ? "Unknown" : sender
        self.messagePreview = messagePreview
        self.receivedAt = receivedAt
        self.source = source
        self.confidence = confidence
    }
}

public struct IncomingCodePayload: Codable, Equatable, Sendable {
    public let id: String?
    public let code: String
    public let sender: String?
    public let messagePreview: String?
    public let receivedAt: Date?
    public let source: String?
    public let confidence: Double?

    public init(
        id: String? = nil,
        code: String,
        sender: String? = nil,
        messagePreview: String? = nil,
        receivedAt: Date? = nil,
        source: String? = nil,
        confidence: Double? = nil
    ) {
        self.id = id
        self.code = code
        self.sender = sender
        self.messagePreview = messagePreview
        self.receivedAt = receivedAt
        self.source = source
        self.confidence = confidence
    }

    public func toEvent(now: Date = Date()) throws -> CodeEvent {
        let normalized = code.trimmingCharacters(in: .whitespacesAndNewlines)
        guard Self.isValidCode(normalized) else {
            throw CodeBridgeError.invalidCode
        }

        return CodeEvent(
            id: id ?? UUID().uuidString,
            code: normalized,
            sender: sender?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "Unknown",
            messagePreview: messagePreview,
            receivedAt: receivedAt ?? now,
            source: source ?? "sms",
            confidence: confidence
        )
    }

    public static func isValidCode(_ value: String) -> Bool {
        let pattern = #"^[A-Za-z0-9]{4,10}$"#
        return value.range(of: pattern, options: .regularExpression) != nil
    }
}

public enum CodeBridgeError: Error, Equatable {
    case invalidCode
    case invalidRequest
    case invalidJSON
    case unauthorized
}

