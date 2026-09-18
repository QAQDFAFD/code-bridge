import CodeBridgeCore
import Foundation
import Testing
@testable import CodeBridgeMac

@Test func historyStoreRoundTripsEvents() throws {
    let suiteName = "HistoryStoreTests-\(UUID().uuidString)"
    let defaults = UserDefaults(suiteName: suiteName)!
    defer { defaults.removePersistentDomain(forName: suiteName) }

    let store = HistoryStore(defaults: defaults)
    #expect(store.load().isEmpty)

    let events = [
        CodeEvent(code: "111111", sender: "A", receivedAt: Date(timeIntervalSince1970: 100)),
        CodeEvent(code: "222222", sender: "B", receivedAt: Date(timeIntervalSince1970: 200))
    ]
    store.save(events)

    let loaded = store.load()
    #expect(loaded.map(\.code) == ["111111", "222222"])
    #expect(loaded.map(\.receivedAt) == events.map(\.receivedAt))

    store.clear()
    #expect(store.load().isEmpty)
}

@Test func historyStoreToleratesCorruptData() {
    let suiteName = "HistoryStoreTests-\(UUID().uuidString)"
    let defaults = UserDefaults(suiteName: suiteName)!
    defer { defaults.removePersistentDomain(forName: suiteName) }

    defaults.set(Data("not-json".utf8), forKey: "historyEvents")
    #expect(HistoryStore(defaults: defaults).load().isEmpty)
}
