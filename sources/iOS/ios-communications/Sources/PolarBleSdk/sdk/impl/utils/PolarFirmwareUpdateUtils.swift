//  Copyright © 2024 Polar. All rights reserved.

import Foundation
import Zip

class PolarFirmwareUpdateUtils {
    static let FIRMWARE_UPDATE_FILE_PATH = "/SYSUPDAT.IMG"
    static let DEVICE_FIRMWARE_INFO_PATH = "/DEVICE.BPB"

    public class FwFileComparator {
        private static let SYSUPDAT_IMG = "SYSUPDAT.IMG"

        static func compare(_ file1: String, _ file2: String) -> ComparisonResult {
            if file1.contains(SYSUPDAT_IMG) {
                return .orderedDescending
            } else if file2.contains(SYSUPDAT_IMG) {
                return .orderedAscending
            } else {
                return .orderedSame
            }
        }
    }
    
    static func readDeviceFirmwareInfo(client: BlePsFtpClient, deviceId: String) async -> PolarFirmwareVersionInfo? {
        let request = Protocol_PbPFtpOperation.with {
            $0.command = .get
            $0.path = DEVICE_FIRMWARE_INFO_PATH
        }
        do {
            let serializedBytes = try request.serializedData()
            let response = try await client.request(serializedBytes)
            let proto = try Data_PbDeviceInfo(serializedBytes: response as Data)
            return PolarFirmwareVersionInfo(
                deviceFwVersion: devicePbVersionToString(pbVersion: proto.deviceVersion),
                deviceModelName: proto.modelName,
                deviceHardwareCode: proto.hardwareCode
            )
        } catch {
            BleLogger.error("Failed to request device info: \(deviceId), error: \(error)")
            return nil
        }
    }

    static func isAvailableFirmwareVersionHigher(currentVersion: String, availableVersion: String) -> Bool {
        let current = currentVersion.split(separator: ".").map { Int($0)! }
        let available = availableVersion.split(separator: ".").map { Int($0)! }

        for i in 0..<current.count {
            if available.count > i {
                if current[i] < available[i] {
                    return true
                } else if current[i] > available[i] {
                    return false
                }
            }
        }
        return available.count > current.count
    }

    static func unzipFirmwarePackage(zippedData: Data) -> [String: Data]? {
        let temporaryDirectory = FileManager.default.temporaryDirectory
        
        let zipFilePath = temporaryDirectory.appendingPathComponent(UUID().uuidString + ".zip")
        do {
            try zippedData.write(to: zipFilePath)

            let destinationURL = temporaryDirectory.appendingPathComponent(UUID().uuidString)

            try Zip.unzipFile(zipFilePath, destination: destinationURL, overwrite: true, password: nil)

            let contents = try FileManager.default.contentsOfDirectory(at: destinationURL, includingPropertiesForKeys: nil)
            guard !contents.isEmpty else {
                BleLogger.error("unzipFirmwarePackage() error: No files found in the extracted directory")
                return nil
            }
            var fileDataDictionary: [String: Data] = [:]
            for fileURL in contents {
                let fileName = fileURL.lastPathComponent
                let decompressedData = try Data(contentsOf: fileURL)
                fileDataDictionary[fileName] = decompressedData
                BleLogger.trace("Extracted file: \(fileName) - Size: \(decompressedData.count) bytes")
            }

            try FileManager.default.removeItem(at: zipFilePath)
            try FileManager.default.removeItem(at: destinationURL)

            return fileDataDictionary
        } catch {
            BleLogger.error("Error during unzipFirmwarePackage(): \(error)")
            return nil
        }
    }
    
    private static func devicePbVersionToString(pbVersion: PbVersion) -> String {
        return "\(pbVersion.major).\(pbVersion.minor).\(pbVersion.patch)"
    }
}
