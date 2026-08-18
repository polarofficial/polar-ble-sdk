import CoreBluetooth
import Foundation
import Combine

open class BleMdsClient: BleGattClientBase, @unchecked Sendable {
    
    /// NOTE: this is an experimental BLE client
    ///  intended for Polar internal use only. Polar will not support 3rd party users with this API.
    /// Connection parameters read from the Memfault Diagnostic Service (MDS) on the BLE device.
    ///
    /// Returned by `PolarDeviceTelemetryApi.getTelemetryConnectionInfo(_:)`.
    /// Forward `deviceIdentifier`, `dataUri` and `authorization` to the Memfault cloud API
    /// when uploading chunks received via `startMemfaultStreaming(_:)`.
    public struct DeviceTelemetryConfiguration: Equatable, Sendable {

        /// Memfault device key — used as the `{device_identifier}` path segment in cloud API calls.
        public let deviceIdentifier: String

        /// URL for the Memfault cloud chunk upload endpoint, e.g. `"https://nrf-chunks.memfault.com/api/v0/chunks/"`.
        /// Append the device identifier to complete the upload path: `<dataUri><deviceIdentifier>`
        public let dataUri: String

        /// Authorization token (project key or Bearer token). May be empty if the device does not
        /// require authorization. When non-empty, set as `Memfault-Project-Key` or `Authorization`
        /// HTTP header when uploading chunks.
        public let authorization: String

        /// Raw bitmask read from the MDS Supported Features characteristic (UUID 54220001).
        ///
        /// Each bit indicates a capability of the device's MDS implementation.
        /// Currently the spec defines only bit 0:
        /// - bit 0 (`0x01`) — live streaming of Memfault diagnostic chunks is supported.
        ///
        /// All other bits are reserved for future use.
        /// The value `0x00` means this is the baseline MDS profile with no optional features enabled.
        public let supportedFeatures: UInt32
        
        public init(deviceIdentifier: String,
                    dataUri: String,
                    authorization: String,
                    supportedFeatures: UInt32) {
            self.deviceIdentifier = deviceIdentifier
            self.dataUri = dataUri
            self.authorization = authorization
            self.supportedFeatures = supportedFeatures
        }
    }
    
    /// NOTE: this is an experimental type intended for Polar internal use only.
    /// Memfault MDS telemetry configuration with cloud upload parameters
    /// Used by the public DeviceTelemetryConfiguration enum
    public struct MemfaultTelemetryConfiguration: Equatable, Sendable {
        public let deviceIdentifier: String
        /// URI provided by device for uploading telemetry data via HTTP to Memfault service.
        public let dataUri: String?
        /// Authorization token (Memfault-Project-Key value) for HTTP header when uploading telemetry data.
        public let authorization: String?
        /// BLE service specific list of supported features
        public let supportedFeatures: [UInt32]
        
        public init(deviceIdentifier: String, dataUri: String?, authorization: String?, supportedFeatures: [UInt32]) {
            self.deviceIdentifier = deviceIdentifier
            self.dataUri = dataUri
            self.authorization = authorization
            self.supportedFeatures = supportedFeatures
        }
    }

    // MARK: - GATT UUIDs
    public static let MDS_SERVICE            = CBUUID(string: "54220000-f6a5-4007-a371-722f4ebd8436")
    public static let MDS_SUPPORTED_FEATURES = CBUUID(string: "54220001-f6a5-4007-a371-722f4ebd8436")
    public static let MDS_DEVICE_IDENTIFIER  = CBUUID(string: "54220002-f6a5-4007-a371-722f4ebd8436")
    public static let MDS_DATA_URI           = CBUUID(string: "54220003-f6a5-4007-a371-722f4ebd8436")
    public static let MDS_AUTHORIZATION      = CBUUID(string: "54220004-f6a5-4007-a371-722f4ebd8436")
    public static let MDS_DATA_EXPORT        = CBUUID(string: "54220005-f6a5-4007-a371-722f4ebd8436")

    // MARK: - Internal response continuations for read characteristics
    private var featuresContinuation: CheckedContinuation<Data, Error>? = nil
    private var deviceIdContinuation: CheckedContinuation<Data, Error>? = nil
    private var dataUriContinuation: CheckedContinuation<Data, Error>? = nil
    private var authContinuation: CheckedContinuation<Data, Error>? = nil

    // MARK: - Streaming (DATA_EXPORT notifications → Memfault chunks)
    private let chunkStreams = StreamContinuationList<Data>()

    // Continuation awaiting the ATT Write Response for a DATA_EXPORT mode write.
    // Resumed by serviceDataWritten(_:err:) when didWriteValueFor fires.
    private var writeModeContinuation: CheckedContinuation<Void, Error>? = nil

    // Continuation awaiting the ATT result of a CCCD enable/disable write for DATA_EXPORT.
    // Resumed by the notifyDescriptorWritten override below.
    private var cccdContinuation: CheckedContinuation<Void, Error>? = nil
    
    // Tracks the last valid sequence number (bits [4:0] of byte 0) to detect duplicates and gaps.
    // Sequence numbers are modulo-32 (0-31). Reset to nil on disconnect or stream start.
    private var lastSeqNumber: UInt8? = nil

    // MARK: - Init

    public init(gattServiceTransmitter: BleAttributeTransportProtocol) {
        super.init(serviceUuid: BleMdsClient.MDS_SERVICE,
                   gattServiceTransmitter: gattServiceTransmitter)
        // Readable characteristics — discovered and read on-demand via readValue()
        addCharacteristicRead(BleMdsClient.MDS_SUPPORTED_FEATURES)
        addCharacteristicRead(BleMdsClient.MDS_DEVICE_IDENTIFIER)
        addCharacteristicRead(BleMdsClient.MDS_DATA_URI)
        addCharacteristicRead(BleMdsClient.MDS_AUTHORIZATION)
        // DATA_EXPORT is notifiable; notification is enabled on-demand (not auto on connect)
        addCharacteristic(BleMdsClient.MDS_DATA_EXPORT)
    }

    // MARK: - BleGattClientBase overrides

    override public func disconnected() {
        super.disconnected()
        
        // Cancel any pending reads
        featuresContinuation?.resume(throwing: BleGattException.gattDisconnected)
        featuresContinuation = nil
        deviceIdContinuation?.resume(throwing: BleGattException.gattDisconnected)
        deviceIdContinuation = nil
        dataUriContinuation?.resume(throwing: BleGattException.gattDisconnected)
        dataUriContinuation = nil
        authContinuation?.resume(throwing: BleGattException.gattDisconnected)
        authContinuation = nil
        
        cccdContinuation?.resume(throwing: BleGattException.gattDisconnected)
        cccdContinuation = nil
        writeModeContinuation?.resume(throwing: BleGattException.gattDisconnected)
        writeModeContinuation = nil
        chunkStreams.finish(throwing: BleGattException.gattDisconnected)
        
        // Reset sequence tracking on disconnect
        lastSeqNumber = nil
    }

    override public func processServiceData(_ chr: CBUUID, data: Data, err: Int) {
        if chr == BleMdsClient.MDS_SUPPORTED_FEATURES {
            guard let continuation = featuresContinuation else { return }
            featuresContinuation = nil
            if err == 0 {
                BleLogger.trace("MDS_SUPPORTED_FEATURES received (\(data.count) bytes)")
                continuation.resume(returning: data)
            } else {
                BleLogger.error("MDS_SUPPORTED_FEATURES read error: \(err)")
                continuation.resume(throwing: BleGattException.gattCharacteristicError)
            }
        } else if chr == BleMdsClient.MDS_DEVICE_IDENTIFIER {
            guard let continuation = deviceIdContinuation else { return }
            deviceIdContinuation = nil
            if err == 0 {
                BleLogger.trace("MDS_DEVICE_IDENTIFIER received (\(data.count) bytes)")
                continuation.resume(returning: data)
            } else {
                BleLogger.error("MDS_DEVICE_IDENTIFIER read error: \(err)")
                continuation.resume(throwing: BleGattException.gattCharacteristicError)
            }
        } else if chr == BleMdsClient.MDS_DATA_URI {
            guard let continuation = dataUriContinuation else { return }
            dataUriContinuation = nil
            if err == 0 {
                BleLogger.trace("MDS_DATA_URI received (\(data.count) bytes)")
                continuation.resume(returning: data)
            } else {
                BleLogger.error("MDS_DATA_URI read error: \(err)")
                continuation.resume(throwing: BleGattException.gattCharacteristicError)
            }
        } else if chr == BleMdsClient.MDS_AUTHORIZATION {
            guard let continuation = authContinuation else { return }
            authContinuation = nil
            if err == 0 {
                BleLogger.trace("MDS_AUTHORIZATION received (\(data.count) bytes)")
                continuation.resume(returning: data)
            } else {
                BleLogger.error("MDS_AUTHORIZATION read error: \(err)")
                continuation.resume(throwing: BleGattException.gattCharacteristicError)
            }
        } else if chr == BleMdsClient.MDS_DATA_EXPORT {
            if err == 0 {
                BleLogger.trace("MDS_DATA_EXPORT chunk received (\(data.count) bytes)")
                // Validate sequence number and filter duplicates
                if validateAndFilterChunk(data) {
                    chunkStreams.yield(Data(data.dropFirst()))
                }
            } else {
                BleLogger.error("MDS_DATA_EXPORT notification error: \(err)")
                chunkStreams.finish(throwing: BleGattException.gattCharacteristicNotifyError(
                    errorCode: err,
                    errorDescription: "MDS_DATA_EXPORT notification error \(err)"
                ))
            }
        }
    }

    /// MDS is considered ready as soon as the service is discovered; notifications are on-demand.
    public override func clientReady(_ checkConnection: Bool) -> AnyPublisher<Never, Error> {
        waitDiscovered(checkConnection: checkConnection)
    }

    /// Intercept CCCD write results for MDS_DATA_EXPORT and forward them to `cccdContinuation`.
    override public func notifyDescriptorWritten(_ chr: CBUUID, enabled: Bool, err: Int) {
        super.notifyDescriptorWritten(chr, enabled: enabled, err: err)
        guard chr.isEqual(BleMdsClient.MDS_DATA_EXPORT) else { return }
        guard let continuation = cccdContinuation else { return }
        cccdContinuation = nil
        if err == 0 {
            continuation.resume()
        } else {
            continuation.resume(throwing: BleGattException.gattCharacteristicNotifyError(
                errorCode: err,
                errorDescription: "MDS_DATA_EXPORT CCCD write failed with err \(err)"
            ))
        }
    }

    /// Read Supported Features bitmask.
    /// Bit 0 = streaming supported, Bit 1 = data export supported.
    /// The MDS spec defines this as a variable-length little-endian field (1–4 bytes).
    open func readSupportedFeatures() async throws -> UInt32 {
        guard let transport = gattServiceTransmitter else {
            throw BleGattException.gattTransportNotAvailable
        }
        
        let data = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Data, Error>) in
            featuresContinuation = continuation
            do {
                try transport.readValue(self, serviceUuid: BleMdsClient.MDS_SERVICE,
                                        characteristicUuid: BleMdsClient.MDS_SUPPORTED_FEATURES)
            } catch {
                featuresContinuation = nil
                continuation.resume(throwing: error)
            }
        }
        
        guard data.count >= 1 && data.count <= 4 else {
            BleLogger.error("MDS_SUPPORTED_FEATURES unexpected length: \(data.count) bytes")
            throw BleGattException.gattOperationModeChange()
        }
        // Zero-pad to 4 bytes so we can always load as a little-endian UInt32.
        var padded = Data(repeating: 0, count: 4)
        padded.replaceSubrange(0..<data.count, with: data)
        return padded.withUnsafeBytes { $0.load(as: UInt32.self) }.littleEndian
    }

    /// Read the Memfault device identifier (UTF-8 string).
    open func readDeviceIdentifier() async throws -> String {
        guard let transport = gattServiceTransmitter else {
            throw BleGattException.gattTransportNotAvailable
        }
        
        let data = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Data, Error>) in
            deviceIdContinuation = continuation
            do {
                try transport.readValue(self, serviceUuid: BleMdsClient.MDS_SERVICE,
                                        characteristicUuid: BleMdsClient.MDS_DEVICE_IDENTIFIER)
            } catch {
                deviceIdContinuation = nil
                continuation.resume(throwing: error)
            }
        }
        
        return String(bytes: data, encoding: .utf8) ?? ""
    }

    /// Read the cloud data URI (UTF-8 string).
    open func readDataUri() async throws -> String {
        guard let transport = gattServiceTransmitter else {
            throw BleGattException.gattTransportNotAvailable
        }
        
        let data = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Data, Error>) in
            dataUriContinuation = continuation
            do {
                try transport.readValue(self, serviceUuid: BleMdsClient.MDS_SERVICE,
                                        characteristicUuid: BleMdsClient.MDS_DATA_URI)
            } catch {
                dataUriContinuation = nil
                continuation.resume(throwing: error)
            }
        }
        
        return String(bytes: data, encoding: .utf8) ?? ""
    }

    /// Read the authorization token (UTF-8 string, may be empty).
    open func readAuthorization() async throws -> String {
        guard let transport = gattServiceTransmitter else {
            throw BleGattException.gattTransportNotAvailable
        }
        
        let data = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Data, Error>) in
            authContinuation = continuation
            do {
                try transport.readValue(self, serviceUuid: BleMdsClient.MDS_SERVICE,
                                        characteristicUuid: BleMdsClient.MDS_AUTHORIZATION)
            } catch {
                authContinuation = nil
                continuation.resume(throwing: error)
            }
        }
        
        return String(bytes: data, encoding: .utf8) ?? ""
    }

    /// Returns a stream of raw Memfault chunk Data packets delivered via MDS_DATA_EXPORT notifications.
    /// Each emitted Data value is one Memfault chunk, ready to be forwarded to the Memfault cloud.
    /// The stream ends when the device disconnects, an error occurs, or streaming is stopped.
    open func observeMemfaultChunks(checkConnection: Bool) -> AsyncThrowingStream<Data, Error> {
        return chunkStreams.makeStream(transport: gattServiceTransmitter, checkConnection: checkConnection)
    }

    /// Enable MDS_DATA_EXPORT notifications and start streaming.
    ///
    /// Per the MDS spec the host must both:
    /// 1. Subscribe to CCCD notifications on the characteristic.
    /// 2. Write `0x01` (streaming enabled) to the characteristic value.
    open func enableDataExportNotification() async throws {
        // Reset sequence tracking for new stream
        lastSeqNumber = nil
        
        // Remove any stale notification registration so we always do a fresh CCCD write.
        removeCharacteristicNotification(BleMdsClient.MDS_DATA_EXPORT)
        // Re-register so notifyDescriptorWritten can update state and tearDown() works.
        automaticEnableNotificationsOnConnect(chr: BleMdsClient.MDS_DATA_EXPORT, disableOnDisconnect: true)
        // 1. Subscribe to CCCD — awaitCccdWrite issues the write and suspends until
        //    notifyDescriptorWritten resumes cccdContinuation with the ATT result.
        BleLogger.trace("BleMdsClient enabling CCCD for DATA_EXPORT")
        try await awaitCccdWrite(notify: true)
        // 2. Write mode byte 0x01 — awaits ATT Write Response via serviceDataWritten.
        BleLogger.trace("BleMdsClient enabling streaming (write 0x01 to DATA_EXPORT)")
        try await writeDataExportMode(0x01)
    }

    /// Stop streaming and unsubscribe from MDS_DATA_EXPORT notifications.
    ///
    /// Per the MDS spec the host must both:
    /// 1. Write `0x00` (streaming disabled) to the characteristic value.
    /// 2. Unsubscribe from CCCD notifications.
    open func disableDataExportNotification() async throws {
        // 1. Write mode byte 0x00 — awaits ATT Write Response via serviceDataWritten.
        BleLogger.trace("BleMdsClient disabling streaming (write 0x00 to DATA_EXPORT)")
        try await writeDataExportMode(0x00)
        // 2. Unsubscribe from CCCD only if we previously subscribed.
        if containsNotifyCharacteristic(BleMdsClient.MDS_DATA_EXPORT) {
            BleLogger.trace("BleMdsClient disabling CCCD for DATA_EXPORT")
            try await awaitCccdWrite(notify: false)
            removeCharacteristicNotification(BleMdsClient.MDS_DATA_EXPORT)
        }
    }

    /// Issue a CCCD enable/disable write for DATA_EXPORT and suspend until the ATT response
    /// arrives via `notifyDescriptorWritten(_:enabled:err:)`.
    ///
    /// Stores `cccdContinuation` BEFORE calling `setCharacteristicNotify` to handle mocks that
    /// fire `notifyDescriptorWritten` synchronously inside the call.
    private func awaitCccdWrite(notify: Bool) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            cccdContinuation = continuation
            guard let transport = gattServiceTransmitter else {
                cccdContinuation = nil
                continuation.resume(throwing: BleGattException.gattTransportNotAvailable)
                return
            }
            do {
                try transport.setCharacteristicNotify(
                    self,
                    serviceUuid: BleMdsClient.MDS_SERVICE,
                    characteristicUuid: BleMdsClient.MDS_DATA_EXPORT,
                    notify: notify
                )
                BleLogger.trace("BleMdsClient DATA_EXPORT CCCD notify=\(notify) write issued")
            } catch {
                cccdContinuation = nil
                continuation.resume(throwing: error)
            }
        }
    }

    /// Write a streaming-mode byte to the DATA_EXPORT characteristic (write-with-response).
    ///
    /// Registers a CheckedContinuation BEFORE issuing the write to avoid a race, then
    /// suspends until serviceDataWritten(_:err:) resumes it when the ATT response arrives.
    private func writeDataExportMode(_ mode: UInt8) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            // Store continuation first — serviceDataWritten may be called on any thread.
            writeModeContinuation = continuation
            guard let transport = gattServiceTransmitter else {
                writeModeContinuation = nil
                continuation.resume(throwing: BleGattException.gattTransportNotAvailable)
                return
            }
            do {
                try transport.transmitMessage(
                    self,
                    serviceUuid: BleMdsClient.MDS_SERVICE,
                    characteristicUuid: BleMdsClient.MDS_DATA_EXPORT,
                    packet: Data([mode]),
                    withResponse: true
                )
                BleLogger.trace("BleMdsClient DATA_EXPORT mode 0x\(String(mode, radix: 16)) write issued")
            } catch {
                writeModeContinuation = nil
                continuation.resume(throwing: error)
            }
        }
    }

    /// Validate chunk sequence number (byte 0, bits [4:0]) and filter duplicates.
    ///
    /// Per Memfault MDS spec, each chunk starts with a modulo-32 sequence counter in bits [4:0] of byte 0.
    /// This method detects and drops duplicate chunks, and logs warnings when chunks are dropped.
    ///
    /// - Parameter data: Raw chunk data from MDS_DATA_EXPORT notification
    /// - Returns: `true` if chunk should be yielded to stream, `false` if duplicate (should drop)
    private func validateAndFilterChunk(_ data: Data) -> Bool {
        guard data.count >= 1 else {
            BleLogger.error("MDS chunk too short (\(data.count) bytes), dropping")
            return false
        }
        
        // Extract sequence number from low 5 bits of byte 0 (modulo-32)
        let seqNum = data[0] & 0x1F
        
        // First chunk after stream start — accept any sequence number
        guard let last = lastSeqNumber else {
            BleLogger.trace("MDS first chunk seq=\(seqNum)")
            lastSeqNumber = seqNum
            return true
        }
        
        // Calculate expected next sequence number (modulo-32 wrap-around)
        let expected = (last + 1) % 32
        
        if seqNum == expected {
            // Normal sequential chunk
            lastSeqNumber = seqNum
            return true
        } else if seqNum == last {
            // Duplicate chunk — drop silently
            BleLogger.trace("MDS duplicate chunk seq=\(seqNum), dropping")
            return false
        } else {
            // Gap detected — some chunks were dropped by BLE layer
            let gap = (seqNum + 32 - expected) % 32
            BleLogger.error("MDS chunk gap: expected seq=\(expected), got=\(seqNum), ~\(gap) chunk(s) dropped")
            // Yield the chunk anyway — let Memfault cloud handle missing data
            lastSeqNumber = seqNum
            return true
        }
    }

    /// Called by the BLE stack when a write-with-response completes for DATA_EXPORT.
    override public func serviceDataWritten(_ chr: CBUUID, err: Int) {
        guard chr.isEqual(BleMdsClient.MDS_DATA_EXPORT) else { return }
        guard let continuation = writeModeContinuation else { return }
        writeModeContinuation = nil
        if err == 0 {
            continuation.resume()
        } else {
            continuation.resume(throwing: BleGattException.gattCharacteristicError)
        }
    }
}
