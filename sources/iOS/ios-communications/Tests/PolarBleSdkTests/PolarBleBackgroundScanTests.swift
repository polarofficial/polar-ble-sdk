import XCTest
import CoreBluetooth
import UIKit
@testable import PolarBleSdk

final class PolarBleBackgroundScanTests: XCTestCase {
    private final class BackgroundApi: PolarBleApiImpl {
        override var isApplicationInBackground: Bool { true }
    }

    private final class ForegroundApi: PolarBleApiImpl {
        override var isApplicationInBackground: Bool { false }
    }

    @MainActor
    func testBackgroundLaunchConfiguresServiceFilterWithoutLifecycleNotification() {
        let api = BackgroundApi(.main, features: [.feature_hr])
        XCTAssertEqual(api.listener.servicesToScanFor, [CBUUID(string: "180D")],
                       "A background restoration launch must not start an unfiltered scan")
    }

    @MainActor
    func testForegroundLaunchKeepsUnfilteredScan() {
        let api = ForegroundApi(.main, features: [.feature_hr])
        XCTAssertNil(api.listener.servicesToScanFor)
    }

    @MainActor
    func testLifecycleTransitionsStillUpdateFilterAfterBackgroundLaunch() {
        let api = BackgroundApi(.main, features: [.feature_hr])
        NotificationCenter.default.post(name: UIApplication.willEnterForegroundNotification, object: nil)
        XCTAssertNil(api.listener.servicesToScanFor)
        NotificationCenter.default.post(name: UIApplication.didEnterBackgroundNotification, object: nil)
        XCTAssertEqual(api.listener.servicesToScanFor, [CBUUID(string: "180D")])
    }
}
