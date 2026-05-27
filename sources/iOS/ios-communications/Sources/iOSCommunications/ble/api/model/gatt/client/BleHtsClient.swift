import CoreBluetooth
import Foundation

public class HealthThermometer {
    public static let HTS_SERVICE = CBUUID(string: "00001809-0000-1000-8000-00805f9b34fb")
    public static let TEMPERATURE_MEASUREMENT = CBUUID(string: "2A1C")
    public static let TEMPERATURE_TYPE = CBUUID(string: "2A1D")
}

public class BleHtsClient: BleGattClientBase, @unchecked Sendable {
    public struct TemperatureMeasurement {
        public let temperatureCelsius: Float
        public let temperatureFahrenheit: Float
    }

    public init(gattServiceTransmitter: BleAttributeTransportProtocol) {
        super.init(serviceUuid: HealthThermometer.HTS_SERVICE, gattServiceTransmitter: gattServiceTransmitter)
        automaticEnableNotificationsOnConnect(chr: HealthThermometer.TEMPERATURE_MEASUREMENT, disableOnDisconnect: true)
    }

    static let TEMP_ACCURACY: Int = 100

    private let streamLock = NSLock()
    private var continuations: [UUID: AsyncThrowingStream<TemperatureMeasurement, Error>.Continuation] = [:]

    override public func disconnected() {
        super.disconnected()
        streamLock.lock()
        let conts = continuations
        continuations.removeAll()
        streamLock.unlock()
        for cont in conts.values {
            cont.finish(throwing: BleGattException.gattDisconnected)
        }
    }

    override public func processServiceData(_ chr: CBUUID, data: Data, err: Int) {
        BleLogger.trace("processServiceData uuid=\(chr), err=\(err) len(data)=\(data.count)")
        if err == 0 {
            if chr == HealthThermometer.TEMPERATURE_MEASUREMENT {
                BleLogger.trace_hex("TEMPERATURE_MEASUREMENT ", data: data)
                let flags = UInt8(data[0])
                let isFahrenheit = (flags & 0x01) != 0
                let exponent = Int8(bitPattern: data[4])
                let value = UInt32(data[1]) | (UInt32(data[2]) << 8) | (UInt32(data[3]) << 16)
                let temperature = (Float(value) * pow(10.0, Float(exponent)) * Float(BleHtsClient.TEMP_ACCURACY)).rounded() / Float(BleHtsClient.TEMP_ACCURACY)
                let celsius = !isFahrenheit ? temperature : (temperature - 32.0) * 5.0 / 9.0
                let fahrenheit = isFahrenheit ? temperature : temperature * 9.0 / 5.0 + 32.0
                let measurement = TemperatureMeasurement(temperatureCelsius: celsius, temperatureFahrenheit: fahrenheit)
                streamLock.lock()
                let conts = continuations
                streamLock.unlock()
                for cont in conts.values {
                    cont.yield(measurement)
                }
            }
            if chr == HealthThermometer.TEMPERATURE_TYPE {
                BleLogger.trace_hex("TEMPERATURE_TYPE ", data: data)
            }
        }
    }

    public func observeHtsNotifications(checkConnection: Bool) -> AsyncThrowingStream<TemperatureMeasurement, Error> {
        if checkConnection && !(gattServiceTransmitter?.isConnected() ?? false) {
            return AsyncThrowingStream { $0.finish(throwing: BleGattException.gattDisconnected) }
        }
        let id = UUID()
        var capturedCont: AsyncThrowingStream<TemperatureMeasurement, Error>.Continuation!
        let stream = AsyncThrowingStream<TemperatureMeasurement, Error> { cont in
            capturedCont = cont
        }
        streamLock.lock()
        continuations[id] = capturedCont
        streamLock.unlock()
        capturedCont.onTermination = { [weak self] _ in
            self?.streamLock.lock()
            self?.continuations.removeValue(forKey: id)
            self?.streamLock.unlock()
        }
        return stream
    }
}
