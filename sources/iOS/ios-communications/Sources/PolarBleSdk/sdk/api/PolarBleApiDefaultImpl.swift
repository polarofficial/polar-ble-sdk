/// Copyright © 2019 Polar Electro Oy. All rights reserved.

import Foundation

/// Class to provide the default implementation of the Polar Ble API
public class PolarBleApiDefaultImpl {
    
    /// New instance of Polar Ble API implementation
    ///
    /// - Parameter queue: context of where the API is used
    /// - Parameter features: set of SDK features to enable. Pass an empty set to enable all SDK features by default.
    /// - Returns: api instance
    public static func polarImplementation(_ queue: DispatchQueue, features: Set<PolarBleSdkFeature>) -> PolarBleApi {
        return PolarBleApiImpl(queue, features: features)
    }
    
    /// Return current version
    ///
    /// - Returns: version in format major.minor.patch
    public static func versionInfo() -> String {
        return "7.0.1"
    }
}
