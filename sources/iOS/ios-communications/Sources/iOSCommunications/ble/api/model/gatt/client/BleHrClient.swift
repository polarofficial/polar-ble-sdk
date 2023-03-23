
import Foundation
import CoreBluetooth
import RxSwift

/// The BleHrClient class implements BLE Heart Rate client for receiving heart rate data from BLE Heart Rate service.
///
/// This class implements the BLE Heart Rate client, which conforms to version 1.0 of the Heart Rate Service specification
/// defined by the Bluetooth Special Interest Group (SIG) at https://www.bluetooth.com/specifications/specs/heart-rate-service-1-0/
///
/// The client receives RR-Interval data in 1/1024 milliseconds units. The RR-Interval data unit is defined in chapter 3.113.2 of
/// the GATT Specification Supplement v8
///
/// - gattServiceTransmitter: The BLE GATT transmitter interface used to send and receive GATT requests and responses.

public class BleHrClient: BleGattClientBase {
    public static let HR_SERVICE = CBUUID(string: "180D")
    private static let BODY_SENSOR_LOCATION = CBUUID(string: "2a38")
    static let HR_MEASUREMENT = CBUUID(string: "2a37")
    
    /// A tuple representing a Heart Rate (HR) notification received over BLE.
    ///
    /// - hr: heart rate in BPM (beats per minute)
    /// - sensorContact: true if the sensor has contact (with a measurable surface e.g. skin)
    /// - sensorContactSupported: true if the sensor supports `sensorContact`
    /// - energy: he accumulated energy expended in kilo Joules since the last time it was reset.
    /// - rrs: list of RR-intervals represented by 1/1024 second as unit. The interval with index 0 is older then the interval with index 1.
    /// - rrsMs: list of RRs in milliseconds. The interval with index 0 is older then the interval with index 1.
    /// - rrPresent: true if RR data is available in this sample
    public typealias BleHrNotification = (hr: Int, sensorContact: Bool, sensorContactSupported: Bool, energy: Int, rrs: [Int], rrsMs: [Int], rrPresent: Bool)
    
    private(set) var observers = AtomicList<RxObserver<BleHrNotification>>()
    private let disposeBag = DisposeBag()
    
    public init(gattServiceTransmitter: BleAttributeTransportProtocol){
        super.init(serviceUuid: BleHrClient.HR_SERVICE, gattServiceTransmitter: gattServiceTransmitter)
        addCharacteristicRead(BleHrClient.BODY_SENSOR_LOCATION)
        automaticEnableNotificationsOnConnect(chr: BleHrClient.HR_MEASUREMENT, disableOnDisconnect:true)
    }
    
    // from base
    override public func disconnected() {
        super.disconnected()
        RxUtils.postErrorAndClearList(observers, error: BleGattException.gattDisconnected)
    }
    
    // from base
    override public func processServiceData(_ chr: CBUUID , data: Data , err: Int ){
        if( chr.isEqual(BleHrClient.HR_MEASUREMENT) && err == 0 ){
            var offset=0
            let hrFormat = data[0] & 0x01
            let sensorContact = ((data[0] & 0x06) >> 1) == 0x03
            let contactSupported = (data[0] & 0x04) != 0
            let energyExpended = (data[0] & 0x08) >> 3
            let rrPresent = (data[0] & 0x10) >> 4
            
            let hrValue = hrFormat == 1 ? (Int(data[1]) + (Int(data[2]) << 8)) : Int(data[1])
            offset = Int(hrFormat) + 2
            var energy = 0
            if energyExpended == 1 {
                energy = Int(data[offset]) + (Int(data[offset + 1]) << 8)
                offset += 2
            }
            var rrs = [Int]()
            var rrsMs = [Int]()
            if rrPresent == 1 {
                let len = data.count
                while (offset < len) {
                    let rrValueRaw = Int(data[offset]) | (Int(data[offset + 1]) << 8)
                    offset += 2
                    rrs.append(rrValueRaw)
                    rrsMs.append(BleHrClient.mapRr1024ToRrMs(rrsRaw: rrValueRaw))
                }
            }
            RxUtils.emitNext(observers) { (observer) in
                observer.obs.onNext((hr: hrValue, sensorContact: sensorContact, sensorContactSupported: contactSupported, energy: energy, rrs: rrs, rrsMs: rrsMs, rrPresent: rrPresent == 1 ))
            }
        }
    }
    
    private static func mapRr1024ToRrMs(rrsRaw: Int) -> Int {
        return Int(round((Float(rrsRaw) / 1024.0) * 1000.0))
    }
    
    /// Observable for observing heart rate data from BLE HR Service
    ///
    /// - Parameter checkConnection: if connection is checked on start of observation
    /// - Returns: observable stream of heart rate  data
    public func observeHrNotifications(_ checkConnection: Bool) -> Observable<BleHrNotification> {
        return RxUtils.monitor(observers, transport: gattServiceTransmitter, checkConnection: checkConnection)
            .share(replay: 0)
    }
}
