/// Copyright © 2024 Polar Electro Oy. All rights reserved.
///
import Foundation
import PolarBleSdk

struct DataDeletionFeature {
    let id = UUID()
    var selectedDate: Date = Date()
    var selectedDataType = PolarStoredDataType.StoredDataType.UNDEFINED
}
