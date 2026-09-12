import Foundation

final class SettingsStore {
    private let defaults = UserDefaults.standard

    var port: UInt16 {
        let stored = defaults.integer(forKey: "receiverPort")
        return stored > 0 ? UInt16(stored) : 47821
    }

    var token: String {
        if let value = defaults.string(forKey: "receiverToken"), !value.isEmpty {
            return value
        }
        let token = Self.generateToken()
        defaults.set(token, forKey: "receiverToken")
        return token
    }

    /// Random 64-bit token shown in the menu bar and typed once into the
    /// Android app during pairing.
    static func generateToken() -> String {
        var generator = SystemRandomNumberGenerator()
        return (0..<8)
            .map { _ in UInt8.random(in: .min ... .max, using: &generator) }
            .map { String(format: "%02x", $0) }
            .joined()
    }
}
