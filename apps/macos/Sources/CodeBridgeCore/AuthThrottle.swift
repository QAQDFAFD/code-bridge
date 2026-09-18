import Foundation

/// Blocks hosts that repeatedly fail token auth within a time window.
/// All-mutation happens on the server's serial queue, so no locking is needed.
public struct AuthThrottle: Sendable, Equatable {
    public let limit: Int
    public let window: TimeInterval

    private var failures: [String: [Date]] = [:]

    public init(limit: Int = 5, window: TimeInterval = 60) {
        self.limit = limit
        self.window = window
    }

    public func isBlocked(_ host: String, now: Date = Date()) -> Bool {
        guard let timestamps = failures[host] else { return false }
        return timestamps.filter { now.timeIntervalSince($0) < window }.count >= limit
    }

    public mutating func recordFailure(_ host: String, now: Date = Date()) {
        var timestamps = (failures[host] ?? []).filter { now.timeIntervalSince($0) < window }
        timestamps.append(now)
        failures[host] = timestamps
    }

    public mutating func recordSuccess(_ host: String) {
        failures[host] = nil
    }
}
