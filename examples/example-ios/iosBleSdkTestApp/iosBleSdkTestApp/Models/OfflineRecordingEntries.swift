/// Copyright © 2022 Polar Electro Oy. All rights reserved.
///
import Foundation
import PolarBleSdk

struct OfflineRecordingEntries: Identifiable {
    let id = UUID()
    var isFetching: Bool = false
    var entries = [PolarOfflineRecordingEntry]() 
}
