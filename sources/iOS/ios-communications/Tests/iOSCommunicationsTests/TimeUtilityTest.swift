//  Copyright © 2026 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications

final class TimeUtilityTests: XCTestCase {

    // MARK: - currentTime

    func testCurrentTimeIsCloseToDateTimeIntervalSince1970() {
        let before = Date().timeIntervalSince1970
        let result = TimeUtility.currentTime()
        let after = Date().timeIntervalSince1970
        XCTAssertGreaterThanOrEqual(result, before)
        XCTAssertLessThanOrEqual(result, after)
    }

    func testCurrentTimeAdvancesOverTime() {
        let t1 = TimeUtility.currentTime()
        Thread.sleep(forTimeInterval: 0.05)
        let t2 = TimeUtility.currentTime()
        XCTAssertGreaterThan(t2, t1)
    }

    // MARK: - timeDeltaSeconds(TimeInterval)

    func testTimeDeltaFromPastIsPositive() {
        let past = TimeUtility.currentTime() - 1.0
        let delta = TimeUtility.timeDeltaSeconds(past)
        XCTAssertGreaterThan(delta, 0)
    }

    func testTimeDeltaFromRecentPastIsApproximatelyCorrect() {
        let past = TimeUtility.currentTime() - 2.0
        let delta = TimeUtility.timeDeltaSeconds(past)
        XCTAssertEqual(delta, 2.0, accuracy: 0.1)
    }

    func testTimeDeltaFromFutureIsNegative() {
        let future = TimeUtility.currentTime() + 5.0
        let delta = TimeUtility.timeDeltaSeconds(future)
        XCTAssertLessThan(delta, 0)
    }

    func testTimeDeltaFromNowIsNearZero() {
        let now = TimeUtility.currentTime()
        let delta = TimeUtility.timeDeltaSeconds(now)
        XCTAssertEqual(delta, 0.0, accuracy: 0.05)
    }

    // MARK: - timeDeltaSeconds(Date)

    func testTimeDeltaFromPastDateIsPositive() {
        let pastDate = Date(timeIntervalSinceNow: -1.0)
        let delta = TimeUtility.timeDeltaSeconds(pastDate)
        XCTAssertGreaterThan(delta, 0)
    }

    func testTimeDeltaFromPastDateIsApproximatelyCorrect() {
        let pastDate = Date(timeIntervalSinceNow: -3.0)
        let delta = TimeUtility.timeDeltaSeconds(pastDate)
        XCTAssertEqual(delta, 3.0, accuracy: 0.1)
    }

    func testTimeDeltaFromFutureDateIsNegative() {
        let futureDate = Date(timeIntervalSinceNow: 5.0)
        let delta = TimeUtility.timeDeltaSeconds(futureDate)
        XCTAssertLessThan(delta, 0)
    }

    func testTimeDeltaFromDateAndIntervalOverloadAreConsistent() {
        let date = Date(timeIntervalSinceNow: -2.0)
        let deltaFromDate = TimeUtility.timeDeltaSeconds(date)
        let deltaFromInterval = TimeUtility.timeDeltaSeconds(date.timeIntervalSince1970)
        XCTAssertEqual(deltaFromDate, deltaFromInterval, accuracy: 0.01)
    }
}
