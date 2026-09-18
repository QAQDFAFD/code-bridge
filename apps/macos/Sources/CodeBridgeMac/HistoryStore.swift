import CodeBridgeCore
import Foundation

/// Persists the recent-code history across launches (last N events only,
/// keeping the "short-lived by default" product principle).
final class HistoryStore {
    private let defaults: UserDefaults
    private let key: String

    init(defaults: UserDefaults = .standard, key: String = "historyEvents") {
        self.defaults = defaults
        self.key = key
    }

    func load() -> [CodeEvent] {
        guard let data = defaults.data(forKey: key) else { return [] }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return (try? decoder.decode([CodeEvent].self, from: data)) ?? []
    }

    func save(_ events: [CodeEvent]) {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        if let data = try? encoder.encode(events) {
            defaults.set(data, forKey: key)
        }
    }

    func clear() {
        defaults.removeObject(forKey: key)
    }
}
