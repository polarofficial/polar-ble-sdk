import Foundation

open class BlePolarDeviceCapabilitiesUtility {
    public enum FileSystemType {
        case unknownFileSystem
        case h10FileSystem
        case polarFileSystemV2
    }

    private static let fileName = "polar_device_capabilities.json"
    private static let configSubfolder = "PolarConfig"
    private static let lock = NSLock()
    private static var initialized = false
    private static var capabilities: [String: DeviceCapabilities] = [:]
    private static var defaults: DeviceCapabilities?

    // MARK: - Codable structs

    private struct DeviceCapabilities: Codable {
        let fileSystemType: String?
        let recordingSupported: Bool?
        let firmwareUpdateSupported: Bool?
        let activityDataSupported: Bool?
        let isDeviceSensor: Bool?
    }

    private struct DeviceCapabilitiesConfig: Codable {
        let version: String?
        let devices: [String: DeviceCapabilities]
        let defaults: DeviceCapabilities
    }

    // MARK: - Bundle access

    private static func loadBundledData() -> Data? {
        // Priority 1: app's main bundle.
        // An app can ship its own polar_device_capabilities.json in its target resources
        // to override the SDK's baseline entirely. This works consistently whether the SDK
        // is integrated via CocoaPods or SPM.
        if let url = Bundle.main.url(forResource: "polar_device_capabilities", withExtension: "json"),
           let data = try? Data(contentsOf: url) {
            return data
        }
        // Priority 2: SDK's own bundled baseline.
        // SPM synthesises Bundle.module pointing at the package resource bundle.
        // CocoaPods use_frameworks! puts resources in the class's framework bundle.
        // SWIFT_PACKAGE is set automatically by the SPM build system.
#if SWIFT_PACKAGE
        if let url = Bundle.module.url(forResource: "polar_device_capabilities", withExtension: "json"),
           let data = try? Data(contentsOf: url) {
            return data
        }
#else
        if let url = Bundle(for: BlePolarDeviceCapabilitiesUtility.self).url(forResource: "polar_device_capabilities", withExtension: "json"),
           let data = try? Data(contentsOf: url) {
            return data
        }
#endif
        return nil
    }

    // MARK: - Initialisation

    /// Initialises the device capabilities configuration.
    ///
    /// 1. Loads the bundled JSON as the baseline configuration.
    /// 2. Attempts to use `Documents/PolarConfig/polar_device_capabilities.json` in the app sandbox:
    ///    - Seeds the file from the bundle if it does not yet exist.
    ///    - If the on-disk `version` differs from the bundled one, user-supplied entries are merged
    ///      into the bundled config and the sandbox file is updated.
    ///    - If the versions match the sandbox file is used as-is.
    /// 3. Falls back to the bundled JSON if any filesystem step fails.
    ///
    /// Safe to call multiple times; initialises only once.
    public static func initialize() {
        lock.lock()
        defer { lock.unlock() }

        guard !initialized else { return }

        guard let bundledData = loadBundledData() else {
            BleLogger.trace("BlePolarDeviceCapabilitiesUtility: Bundled polar_device_capabilities.json not found — capabilities unavailable")
            return
        }

        let decoder = JSONDecoder()
        guard let bundledConfig = try? decoder.decode(DeviceCapabilitiesConfig.self, from: bundledData) else {
            BleLogger.trace("BlePolarDeviceCapabilitiesUtility: Failed to decode bundled capabilities JSON")
            return
        }

        do {
            let fileManager = FileManager.default
            let documentsURL = try fileManager.url(
                for: .documentDirectory,
                in: .userDomainMask,
                appropriateFor: nil,
                create: true
            )
            let configFolder = documentsURL.appendingPathComponent(configSubfolder)
            let configURL = configFolder.appendingPathComponent(fileName)

            if !fileManager.fileExists(atPath: configFolder.path) {
                try fileManager.createDirectory(at: configFolder, withIntermediateDirectories: true)
            }

            if !fileManager.fileExists(atPath: configURL.path) {
                try bundledData.write(to: configURL)
                BleLogger.trace("BlePolarDeviceCapabilitiesUtility: Seeded sandbox config at \(configURL.path)")
                applyConfig(bundledConfig)
                return
            }

            let userData = try Data(contentsOf: configURL)
            guard let userConfig = try? decoder.decode(DeviceCapabilitiesConfig.self, from: userData) else {
                BleLogger.trace("BlePolarDeviceCapabilitiesUtility: Failed to decode sandbox capabilities JSON — using bundled version")
                applyConfig(bundledConfig)
                return
            }

            if userConfig.version != bundledConfig.version {
                BleLogger.trace("BlePolarDeviceCapabilitiesUtility: Version changed \(userConfig.version ?? "nil") → \(bundledConfig.version ?? "nil"), merging")
                let merged = mergeConfigs(user: userConfig, bundled: bundledConfig)
                let encoder = JSONEncoder()
                encoder.outputFormatting = .prettyPrinted
                if let mergedData = try? encoder.encode(merged) {
                    try? mergedData.write(to: configURL)
                }
                applyConfig(merged)
            } else {
                applyConfig(userConfig)
            }

            BleLogger.trace("BlePolarDeviceCapabilitiesUtility: Initialized successfully from \(configURL.path)")
        } catch {
            BleLogger.trace("BlePolarDeviceCapabilitiesUtility: Filesystem error (\(error)) — falling back to bundled config")
            applyConfig(bundledConfig)
        }
    }

    private static func applyConfig(_ config: DeviceCapabilitiesConfig) {
        defaults = config.defaults
        capabilities = config.devices
        initialized = true
    }

    // MARK: - Merge

    /// Merges user-supplied device entries on top of the bundled baseline.
    /// User-specified non-nil values win; new devices added in the bundle are carried over.
    private static func mergeConfigs(
        user: DeviceCapabilitiesConfig,
        bundled: DeviceCapabilitiesConfig
    ) -> DeviceCapabilitiesConfig {
        var mergedDevices = bundled.devices
        for (key, userDevice) in user.devices {
            let bundledDevice = bundled.devices[key]
            mergedDevices[key] = DeviceCapabilities(
                fileSystemType: userDevice.fileSystemType ?? bundledDevice?.fileSystemType,
                recordingSupported: userDevice.recordingSupported ?? bundledDevice?.recordingSupported,
                firmwareUpdateSupported: userDevice.firmwareUpdateSupported ?? bundledDevice?.firmwareUpdateSupported,
                activityDataSupported: userDevice.activityDataSupported ?? bundledDevice?.activityDataSupported,
                isDeviceSensor: userDevice.isDeviceSensor ?? bundledDevice?.isDeviceSensor
            )
        }
        return DeviceCapabilitiesConfig(
            version: bundled.version,
            devices: mergedDevices,
            defaults: bundled.defaults
        )
    }

    // MARK: - Lazy-init helper

    private static func ensureInitialized() -> Bool {
        if !initialized { initialize() }
        return initialized
    }

    // MARK: - Public API

    /// Get type of filesystem the device supports.
    /// - Parameter deviceType: device type (case-insensitive)
    /// - Returns: filesystem type, or `.unknownFileSystem` if unavailable
    public static func fileSystemType(_ deviceType: String) -> FileSystemType {
        guard ensureInitialized() else { return .unknownFileSystem }
        let fsType = (capabilities[deviceType.lowercased()]?.fileSystemType ?? defaults?.fileSystemType)?.uppercased()
        switch fsType {
        case "H10_FILE_SYSTEM":    return .h10FileSystem
        case "POLAR_FILE_SYSTEM_V2": return .polarFileSystemV2
        default:                   return .unknownFileSystem
        }
    }

    /// Check if device supports recording start and stop over BLE.
    /// - Parameter deviceType: device type (case-insensitive)
    /// - Returns: true if recording is supported
    public static func isRecordingSupported(_ deviceType: String) -> Bool {
        guard ensureInitialized() else { return false }
        return capabilities[deviceType.lowercased()]?.recordingSupported ?? defaults?.recordingSupported ?? false
    }

    /// Check if device supports firmware update.
    /// - Parameter deviceType: device type (case-insensitive)
    /// - Returns: true if firmware update is supported
    public static func isFirmwareUpdateSupported(_ deviceType: String) -> Bool {
        guard ensureInitialized() else { return false }
        return capabilities[deviceType.lowercased()]?.firmwareUpdateSupported ?? defaults?.firmwareUpdateSupported ?? false
    }

    /// Check if device supports activity data storage and sync.
    /// - Parameter deviceType: device type (case-insensitive)
    /// - Returns: true if activity data is supported
    public static func isActivityDataSupported(_ deviceType: String) -> Bool {
        guard ensureInitialized() else { return false }
        return capabilities[deviceType.lowercased()]?.activityDataSupported ?? defaults?.activityDataSupported ?? false
    }

    /// Check if device is a sensor (as opposed to a watch or training computer).
    /// - Parameter deviceType: device type (case-insensitive)
    /// - Returns: true if device is a sensor
    public static func isDeviceSensor(_ deviceType: String) -> Bool {
        guard ensureInitialized() else { return false }
        return capabilities[deviceType.lowercased()]?.isDeviceSensor ?? defaults?.isDeviceSensor ?? false
    }
}
