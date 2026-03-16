//  Copyright © 2026 Polar. All rights reserved.

import Foundation
import RxSwift

public protocol PolarBleLowLevelApi {
    
    ///
    /// Read a file over PFtp BLE client. API user must know the exact path to the desired file.
    /// Note that not all files in device are readable by this API.
    /// API user must also take care of parsing the returned ByteArray payload to the desired data object.
    /// NOTE: this is an experimental API intended for Polar internal use only. Polar will not support 3rd party users with this API.
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_file_transfer`
    /// - Parameters:
    ///  - identifier Polar device ID or BT address
    ///  - filePath Path to the desired file in a Polar device.
    /// - Returns: Maybe (ByteArray or empty)
    ////
    func readFile(
        identifier: String,
        filePath: String
    ) -> Maybe<Data>
    
    ///
    /// Write any file over PFtp BLE client. API user must know the exact path to the desired file.
    /// API user must also take care of parsing the returned ByteArray payload to the desired data object.
    /// NOTE: this is an experimental API intended for Polar internal use only. Polar will not support 3rd party users with this API.
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_file_transfer`
    /// - Parameters:
    ///  -  identifier Polar device ID or BT address
    ///  -  filePath Path to the directory in device  in a Polar device.
    ///  -  fileData, file data in already serialized into ByteArray format.
    /// - Returns: Completable or error
    ////
    func writeFile(
        identifier: String,
        filePath: String,
        fileData: Data
    ) -> Completable
    
    ///
    /// Delete any file or directory over PFtp BLE client. API user must know the exact path to the desired file.
    /// API user must also take care of parsing the returned ByteArray payload to the desired data object.
    /// NOTE: this is an experimental API intended for Polar internal use only. Polar will not support 3rd party users with this API.
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_file_transfer`
    /// - Parameters:
    ///  -  identifier Polar device ID or BT address
    ///  -  filePath Path of the file or directory to be deleted at a Polar device.
    /// - Returns: Completable or error
    ///
    func deleteFileOrDirectory(
        identifier: String,
        filePath: String
    ) -> Completable
    
    ///
    /// List all files in the given path
    /// NOTE: this is an experimental API intended for Polar internal use only. Polar will not support 3rd party users with this API.
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_file_transfer`
    /// - Parameters:
    ///  -  identifier Polar device ID or BT address
    ///  -  directoryPath Path to the desired directory in a Polar device from which to list all files.
    ///  -  recurseDeep Recursion goes to the bottom of the file tree when true.
    /// - Returns: List of files or error

    ///
    func getFileList(
        identifier: String,
        directoryPath: String,
        recurseDeep: Bool
    ) -> Single<[String]>
}
