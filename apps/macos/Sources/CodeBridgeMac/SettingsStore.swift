import Darwin
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

    func storeToken(_ token: String) {
        defaults.set(token, forKey: "receiverToken")
    }

    var autoClearsClipboard: Bool {
        get { defaults.bool(forKey: "autoClearsClipboard") }
        set { defaults.set(newValue, forKey: "autoClearsClipboard") }
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

    /// Short human-friendly name for this Mac, e.g. "Mac-mini".
    static func deviceName() -> String {
        let host = ProcessInfo.processInfo.hostName
        return host.split(separator: ".").first.map(String.init) ?? host
    }

    /// All LAN IPv4 addresses (loopback excluded), used for pairing.
    static func lanIPv4Addresses() -> [String] {
        var ifaddr: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifaddr) == 0, let first = ifaddr else {
            return []
        }
        defer { freeifaddrs(ifaddr) }

        var addresses: [String] = []
        var cursor: UnsafeMutablePointer<ifaddrs>? = first
        while let current = cursor {
            let interface = current.pointee
            defer { cursor = interface.ifa_next }

            guard interface.ifa_addr?.pointee.sa_family == UInt8(AF_INET) else { continue }
            let name = String(cString: interface.ifa_name)
            guard name != "lo0" else { continue }

            var address = interface.ifa_addr!.pointee
            var host = [CChar](repeating: 0, count: Int(NI_MAXHOST))
            let result = getnameinfo(
                &address,
                socklen_t(interface.ifa_addr!.pointee.sa_len),
                &host,
                socklen_t(NI_MAXHOST),
                nil,
                0,
                NI_NUMERICHOST
            )
            if result == 0 {
                addresses.append(String(cString: host))
            }
        }
        return addresses
    }
}
