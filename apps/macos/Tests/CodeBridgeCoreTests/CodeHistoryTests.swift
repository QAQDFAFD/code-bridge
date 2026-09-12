import CodeBridgeCore
import Testing

@Test func historyKeepsNewestFirstAndRespectsLimit() {
    var history = CodeHistory(limit: 2)

    history.add(CodeEvent(id: "1", code: "111111", sender: "A"))
    history.add(CodeEvent(id: "2", code: "222222", sender: "B"))
    history.add(CodeEvent(id: "3", code: "333333", sender: "C"))

    #expect(history.events.map(\.code) == ["333333", "222222"])
}

@Test func historyReplacesEventWithSameId() {
    var history = CodeHistory()

    history.add(CodeEvent(id: "1", code: "111111", sender: "A"))
    history.add(CodeEvent(id: "2", code: "222222", sender: "B"))
    history.add(CodeEvent(id: "1", code: "999999", sender: "A"))

    #expect(history.events.map(\.code) == ["999999", "222222"])
}

