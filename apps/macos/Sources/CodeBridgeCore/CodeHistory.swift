import Foundation

public struct CodeHistory: Sendable {
    public private(set) var events: [CodeEvent]
    public let limit: Int

    public init(events: [CodeEvent] = [], limit: Int = 10) {
        self.limit = max(1, limit)
        self.events = Array(events.prefix(self.limit))
    }

    public mutating func add(_ event: CodeEvent) {
        events.removeAll { $0.id == event.id }
        events.insert(event, at: 0)
        if events.count > limit {
            events.removeLast(events.count - limit)
        }
    }

    public mutating func clear() {
        events.removeAll()
    }
}

