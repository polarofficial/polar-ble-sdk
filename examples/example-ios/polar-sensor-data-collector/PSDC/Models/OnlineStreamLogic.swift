/// Copyright © 2026 Polar Electro Oy. All rights reserved.

import Foundation
import PolarBleSdk

/// Pure logic behind the online streaming screen.
enum OnlineStreamLogic {
    /// PPI and HR have no configurable settings, so every other type needs the settings dialog first.
    static func needsSettings(_ dataType: PolarDeviceDataType) -> Bool {
        dataType != .ppi && dataType != .hr
    }

    static func toggled(_ dataType: PolarDeviceDataType, in selection: Set<PolarDeviceDataType>) -> Set<PolarDeviceDataType> {
        var result = selection
        if !result.insert(dataType).inserted { result.remove(dataType) }
        return result
    }

    /// Selected, available and not yet streaming types, in a stable order.
    static func typesToStart(
        selected: Set<PolarDeviceDataType>,
        available: [PolarDeviceDataType: Bool],
        isStreaming: (PolarDeviceDataType) -> Bool
    ) -> [PolarDeviceDataType] {
        PolarDeviceDataType.allCases.filter {
            selected.contains($0) && available[$0] == true && !isStreaming($0)
        }
    }

    static func buttonTitle(streaming: Bool) -> String {
        streaming ? "STOP STREAMING" : "START STREAMING"
    }

    static func isButtonEnabled(streaming: Bool, hasSelection: Bool) -> Bool {
        streaming || hasSelection
    }
}
