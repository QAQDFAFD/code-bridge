import CodeBridgeCore
import Foundation
import Testing

@Test func throttleBlocksAfterRepeatedFailures() {
    var throttle = AuthThrottle(limit: 3, window: 60)
    let start = Date(timeIntervalSince1970: 1_000)

    #expect(!throttle.isBlocked("h1", now: start))
    throttle.recordFailure("h1", now: start)
    throttle.recordFailure("h1", now: start.addingTimeInterval(1))
    #expect(!throttle.isBlocked("h1", now: start.addingTimeInterval(2)))

    throttle.recordFailure("h1", now: start.addingTimeInterval(2))
    #expect(throttle.isBlocked("h1", now: start.addingTimeInterval(3)))
}

@Test func throttleBlocksHostsIndependently() {
    var throttle = AuthThrottle(limit: 2, window: 60)
    let now = Date()

    throttle.recordFailure("h1", now: now)
    throttle.recordFailure("h1", now: now)
    #expect(throttle.isBlocked("h1", now: now))
    #expect(!throttle.isBlocked("h2", now: now))
}

@Test func throttleWindowExpires() {
    var throttle = AuthThrottle(limit: 2, window: 60)
    let start = Date(timeIntervalSince1970: 2_000)

    throttle.recordFailure("h1", now: start)
    throttle.recordFailure("h1", now: start.addingTimeInterval(10))
    #expect(throttle.isBlocked("h1", now: start.addingTimeInterval(20)))
    #expect(!throttle.isBlocked("h1", now: start.addingTimeInterval(95)))
}

@Test func throttleSuccessResetsFailures() {
    var throttle = AuthThrottle(limit: 2, window: 60)
    let now = Date()

    throttle.recordFailure("h1", now: now)
    throttle.recordSuccess("h1")
    throttle.recordFailure("h1", now: now)
    #expect(!throttle.isBlocked("h1", now: now))
}
