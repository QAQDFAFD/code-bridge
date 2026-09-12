import Foundation

/// Payload encoded into the pairing QR code shown in the Mac menu bar.
public struct PairingPayload: Codable, Equatable, Sendable {
    public let v: Int
    public let service: String
    public let name: String
    public let host: String
    public let port: UInt16
    public let token: String

    public init(
        v: Int = 1,
        service: String = "codebridge",
        name: String,
        host: String,
        port: UInt16,
        token: String
    ) {
        self.v = v
        self.service = service
        self.name = name
        self.host = host
        self.port = port
        self.token = token
    }

    public var jsonString: String? {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        guard let data = try? encoder.encode(self) else { return nil }
        return String(data: data, encoding: .utf8)
    }
}
