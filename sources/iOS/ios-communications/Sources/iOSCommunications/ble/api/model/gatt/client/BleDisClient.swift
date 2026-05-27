import Foundation
import CoreBluetooth

public class BleDisClient: BleGattClientBase, @unchecked Sendable {

    public static let DIS_SERVICE              = CBUUID(string: "180a")
    public static let MODEL_NUMBER_STRING      = CBUUID(string: "2a24")
    public static let MANUFACTURER_NAME_STRING = CBUUID(string: "2a29")
    public static let HARDWARE_REVISION_STRING = CBUUID(string: "2a27")
    public static let FIRMWARE_REVISION_STRING = CBUUID(string: "2a26")
    public static let SOFTWARE_REVISION_STRING = CBUUID(string: "2a28")
    public static let SERIAL_NUMBER_STRING     = CBUUID(string: "2a25")
    public static let SYSTEM_ID                = CBUUID(string: "2a23")
    public static let IEEE_11073_20601         = CBUUID(string: "2a2a")
    public static let PNP_ID                   = CBUUID(string: "2a50")
    public static let SYSTEM_ID_HEX            = String("SYSTEM_ID_HEX")

    private let disStreams = StreamContinuationList<(CBUUID, String)>()
    private let disStringKeyStreams = StreamContinuationList<(String, String)>()

    // disInformation and disInformationStringKey are both synchronized using disInformation.accessItem()
    private let disInformation = AtomicType<[CBUUID: String]>(initialValue: [:])
    private var disInformationStringKey = [String: String]()

    public init(gattServiceTransmitter: BleAttributeTransportProtocol) {
        super.init(serviceUuid: BleDisClient.DIS_SERVICE, gattServiceTransmitter: gattServiceTransmitter)
        addCharacteristicRead(BleDisClient.MODEL_NUMBER_STRING)
        addCharacteristicRead(BleDisClient.MANUFACTURER_NAME_STRING)
        addCharacteristicRead(BleDisClient.HARDWARE_REVISION_STRING)
        addCharacteristicRead(BleDisClient.FIRMWARE_REVISION_STRING)
        addCharacteristicRead(BleDisClient.SOFTWARE_REVISION_STRING)
        addCharacteristicRead(BleDisClient.SERIAL_NUMBER_STRING)
        addCharacteristicRead(BleDisClient.SYSTEM_ID)
        addCharacteristicRead(BleDisClient.IEEE_11073_20601)
        addCharacteristicRead(BleDisClient.PNP_ID)
    }

    override public func disconnected() {
        super.disconnected()
        disInformation.accessItem { dis in
            dis.removeAll()
            self.disInformationStringKey.removeAll()
        }
        disStreams.finish(throwing: BleGattException.gattDisconnected)
        disStringKeyStreams.finish(throwing: BleGattException.gattDisconnected)
    }

    override public func processServiceData(_ chr: CBUUID, data: Data, err: Int) {
        if err == 0 {
            var asciiRepresentation = ""
            var hexRepresentation = ""
            if let stringValue = NSString(data: data, encoding: String.Encoding.ascii.rawValue) as String? {
                asciiRepresentation = stringValue
            }
            var allReceived = false
            disInformation.accessItem { dis in
                dis[chr] = asciiRepresentation
                if chr == BleDisClient.SYSTEM_ID {
                    hexRepresentation = data.map { String(format: "%02X", $0) }.joined()
                    self.disInformationStringKey[chr.uuidString] = hexRepresentation
                } else {
                    self.disInformationStringKey[chr.uuidString] = asciiRepresentation
                }
                allReceived = self.hasAllAvailableReadableCharacteristics(dis as [CBUUID: AnyObject])
            }
            disStreams.yield((chr, asciiRepresentation))
            if allReceived {
                disStreams.finish(throwing: BleGattException.gattOperationModeChange(description: "DIS read complete"))
            }

            if chr == BleDisClient.SYSTEM_ID {
                let reorderedHex = stride(from: 0, to: hexRepresentation.count, by: 2).map {
                    String(hexRepresentation[hexRepresentation.index(hexRepresentation.startIndex, offsetBy: $0)..<hexRepresentation.index(hexRepresentation.startIndex, offsetBy: $0 + 2)])
                }.reversed().joined()
                disStringKeyStreams.yield((BleDisClient.SYSTEM_ID_HEX, reorderedHex))
            } else {
                disStringKeyStreams.yield((chr.uuidString, asciiRepresentation))
            }
            if allReceived {
                disStringKeyStreams.finish(throwing: BleGattException.gattOperationModeChange(description: "DIS read complete"))
            }
        }
    }

    /// Read DIS info. All available DIS info entries are yielded, then the stream completes.
    ///
    /// - Parameter checkConnection: check initial connection
    /// - Returns: AsyncThrowingStream of (CBUUID, String) pairs
    public func readDisInfo(_ checkConnection: Bool) -> AsyncThrowingStream<(CBUUID, String), Error> {
        guard !checkConnection || gattServiceTransmitter?.isConnected() ?? false else {
            return AsyncThrowingStream { $0.finish(throwing: BleGattException.gattDisconnected) }
        }
        return AsyncThrowingStream { continuation in
            // emit already cached entries
            var allReceived = false
            self.disInformation.accessItem { dis in
                for item in dis {
                    continuation.yield((item.key, item.value))
                }
                allReceived = self.hasAllAvailableReadableCharacteristics(dis as [CBUUID: AnyObject]) && !dis.isEmpty
            }
            if allReceived {
                continuation.finish()
                return
            }
            // subscribe for further updates
            let stream = self.disStreams.makeStream(transport: self.gattServiceTransmitter, checkConnection: false)
            Task {
                do {
                    for try await value in stream { continuation.yield(value) }
                    continuation.finish()
                } catch let e as BleGattException {
                    if case .gattOperationModeChange = e { continuation.finish() } else { continuation.finish(throwing: e) }
                } catch { continuation.finish(throwing: error) }
            }
        }
    }

    public func readDisInfoWithKeysAsStrings(_ checkConnection: Bool) -> AsyncThrowingStream<(String, String), Error> {
        guard !checkConnection || gattServiceTransmitter?.isConnected() ?? false else {
            return AsyncThrowingStream { $0.finish(throwing: BleGattException.gattDisconnected) }
        }
        return AsyncThrowingStream { continuation in
            var allReceived = false
            self.disInformation.accessItem { dis in
                for item in dis {
                    continuation.yield((item.key.uuidString, item.value))
                }
                allReceived = self.hasAllAvailableReadableCharacteristics(dis as [CBUUID: AnyObject]) && !dis.isEmpty
            }
            if allReceived {
                continuation.finish()
                return
            }
            let stream = self.disStringKeyStreams.makeStream(transport: self.gattServiceTransmitter, checkConnection: false)
            Task {
                do {
                    for try await value in stream { continuation.yield(value) }
                    continuation.finish()
                } catch let e as BleGattException {
                    if case .gattOperationModeChange = e { continuation.finish() } else { continuation.finish(throwing: e) }
                } catch { continuation.finish(throwing: error) }
            }
        }
    }
}
