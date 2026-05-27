//  Copyright © 2026 Polar. All rights reserved.

import Foundation

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
    /// - Returns: File contents as `Data`, or `nil` if the file is empty or not found.
    /// - Throws: See `PolarErrors` for possible errors.
    ////
    func readFile(
        identifier: String,
        filePath: String
    ) async throws -> Data?
    
    ///
    /// Write any file over PFtp BLE client. API user must know the exact path to the desired file.
    /// API user must also take care of parsing the returned ByteArray payload to the desired data object.
    /// NOTE: this is an experimental API intended for Polar internal use only. Polar will not support 3rd party users with this API.
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_file_transfer`
    /// - Parameters:
    ///  -  identifier Polar device ID or BT address
    ///  -  filePath Path to the directory in device  in a Polar device.
    ///  -  fileData, file data in already serialized into ByteArray format.
    /// - Throws: See `PolarErrors` for possible errors.
    ////
    func writeFile(
        identifier: String,
        filePath: String,
        fileData: Data
    ) async throws
    
    ///
    /// Delete any file or directory over PFtp BLE client. API user must know the exact path to the desired file.
    /// API user must also take care of parsing the returned ByteArray payload to the desired data object.
    /// NOTE: this is an experimental API intended for Polar internal use only. Polar will not support 3rd party users with this API.
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_file_transfer`
    /// - Parameters:
    ///  -  identifier Polar device ID or BT address
    ///  -  filePath Path of the file or directory to be deleted at a Polar device.
    /// - Throws: See `PolarErrors` for possible errors.
    ///
    func deleteFileOrDirectory(
        identifier: String,
        filePath: String
    ) async throws
    
    ///
    /// List all files in the given path
    /// NOTE: this is an experimental API intended for Polar internal use only. Polar will not support 3rd party users with this API.
    /// - Requires SDK feature(s): `PolarBleSdkFeature.feature_polar_file_transfer`
    /// - Parameters:
    ///  -  identifier Polar device ID or BT address
    ///  -  directoryPath Path to the desired directory in a Polar device from which to list all files.
    ///  -  recurseDeep Recursion goes to the bottom of the file tree when true.
    /// - Returns: List of file paths found in the given directory.
    /// - Throws: See `PolarErrors` for possible errors.
    ///
    func getFileList(
        identifier: String,
        directoryPath: String,
        recurseDeep: Bool
    ) async throws -> [String]
}
