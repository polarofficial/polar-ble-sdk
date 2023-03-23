/// Copyright © 2023 Polar Electro Oy. All rights reserved.
///
import Foundation
import PolarBleSdk

enum DeviceSearchState {
    case inProgress
    case success
    case failed(error: String)
}

extension DeviceSearchState: Equatable {
    static func == (lhs: DeviceSearchState, rhs: DeviceSearchState) -> Bool {
        switch (lhs, rhs) {
        case (.inProgress, .inProgress):
            return true
        case (.success, .success):
            return true
        case (.failed(let lhsError), .failed(let rhsError)):
            return lhsError == rhsError
        default:
            return false
        }
    }
}

struct DeviceSearch: Identifiable {
    let id = UUID()
    var isSearching: DeviceSearchState = .success
    var foundDevices = [PolarDeviceInfo]()
}
