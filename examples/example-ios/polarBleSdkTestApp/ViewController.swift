
import UIKit
import PolarBleSdk
import RxSwift
import CoreBluetooth

class ViewController: UIViewController,
                      PolarBleApiObserver,
                      PolarBleApiPowerStateObserver,
                      PolarBleApiDeviceHrObserver,
                      PolarBleApiDeviceInfoObserver,
                      PolarBleApiDeviceFeaturesObserver,
                      PolarBleApiLogger,
                      PolarBleApiCCCWriteObserver {
    
    // NOTICE this example utilizes all available features
    var api = PolarBleApiDefaultImpl.polarImplementation(DispatchQueue.main, features: Features.allFeatures.rawValue)
    var broadcast: Disposable?
    var ecgToggle: Disposable?
    var accToggle: Disposable?
    var gyroToggle: Disposable?
    var magnetometerToggle: Disposable?
    var ppgToggle: Disposable?
    var ppiToggle: Disposable?
    var searchToggle: Disposable?
    var autoConnect: Disposable?
    var exerciseEntry: PolarExerciseEntry?
    var deviceId = "89647C2E" //TODO replace this with your device id
    
    override func viewDidLoad() {
        super.viewDidLoad()
        api.observer = self
        api.deviceHrObserver = self
        api.deviceInfoObserver = self
        api.powerStateObserver = self
        api.deviceFeaturesObserver = self
        api.logger = self
        api.cccWriteObserver = self
        api.polarFilter(false)
        NSLog("\(PolarBleApiDefaultImpl.versionInfo())")
    }
    
    override func didReceiveMemoryWarning() {
        super.didReceiveMemoryWarning()
        // Dispose of any resources that can be recreated.
    }
    
    @IBAction func broadcastToggle(_ sender: Any) {
        if broadcast == nil {
            broadcast = api.startListenForPolarHrBroadcasts(nil)
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .completed:
                        NSLog("completed")
                    case .error(let err):
                        NSLog("listening error: \(err)")
                    case .next(let broadcast):
                        NSLog("\(broadcast.deviceInfo.name) HR BROADCAST: \(broadcast.hr)")
                    }
                }
        } else {
            broadcast?.dispose()
            broadcast = nil
        }
    }
    
    @IBAction func connectToDevice(_ sender: Any) {
        do{
            try api.connectToDevice(deviceId)
        } catch let err {
            print("\(err)")
        }
    }
    
    @IBAction func disconnectFromDevice(_ sender: Any) {
        do{
            try api.disconnectFromDevice(deviceId)
        } catch let err {
            print("\(err)")
        }
    }
    
    @IBAction func autoConnect(_ sender: Any) {
        autoConnect?.dispose()
        autoConnect = api.startAutoConnectToDevice(-55, service: nil, polarDeviceType: nil)
            .subscribe{ e in
                switch e {
                case .completed:
                    NSLog("auto connect search complete")
                case .error(let err):
                    NSLog("auto connect failed: \(err)")
                @unknown default:
                    fatalError()
                }
            }
    }
    
    @IBAction func ecgToggle(_ sender: Any) {
        if ecgToggle == nil {
            ecgToggle = api.requestStreamSettings(deviceId, feature: DeviceStreamingFeature.ecg)
                .asObservable()
                .flatMap({ (settings) -> Observable<PolarEcgData> in
                    return self.api.startEcgStreaming(self.deviceId, settings: settings.maxSettings())
                })
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .next(let data):
                        for µv in data.samples {
                            NSLog("    µV: \(µv)")
                        }
                    case .error(let err):
                        NSLog("ECG error: \(err)")
                        self.ecgToggle = nil
                    case .completed:
                        break
                    }
                }
        } else {
            ecgToggle?.dispose()
            ecgToggle = nil
        }
    }
    
    @IBAction func accToggle(_ sender: Any) {
        if accToggle == nil {
            accToggle = api.requestStreamSettings(deviceId, feature: DeviceStreamingFeature.acc)
                .asObservable()
                .flatMap({ (settings) -> Observable<PolarAccData> in
                    NSLog("settings: \(settings.settings)")
                    return self.api.startAccStreaming(self.deviceId, settings: settings.maxSettings())
                })
                .observe(on: MainScheduler.instance).subscribe{ e in
                    switch e {
                    case .next(let data):
                        for item in data.samples {
                            NSLog("    x: \(item.x) y: \(item.y) z: \(item.z)")
                        }
                    case .error(let err):
                        NSLog("ACC error: \(err)")
                        self.accToggle = nil
                    case .completed:
                        break
                    }
                }
        } else {
            accToggle?.dispose()
            accToggle = nil
        }
    }
    
    @IBAction func gyroToggle(_ sender: Any) {
        if gyroToggle == nil {
            gyroToggle = api.requestStreamSettings(deviceId, feature:DeviceStreamingFeature.gyro)
                .asObservable()
                .flatMap({ (settings) -> Observable<PolarGyroData> in
                    return self.api.startGyroStreaming(self.deviceId, settings: settings.maxSettings())
                })
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .next(let data):
                        for item in data.samples {
                            NSLog("    x: \(item.x) y: \(item.y) z: \(item.z)")
                        }
                    case .error(let err):
                        NSLog("Gyro error: \(err)")
                        self.gyroToggle = nil
                    case .completed:
                        break
                    }
                }
        } else {
            gyroToggle?.dispose()
            gyroToggle = nil
        }
    }
    
    @IBAction func magnetometerToggle(_ sender: Any) {
        if magnetometerToggle == nil {
            magnetometerToggle = api.requestStreamSettings(deviceId, feature:DeviceStreamingFeature.magnetometer)
                .asObservable()
                .flatMap({ (settings) -> Observable<PolarGyroData> in
                    return self.api.startMagnetometerStreaming(self.deviceId, settings: settings.maxSettings())
                })
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .next(let data):
                        for item in data.samples {
                            NSLog("    x: \(item.x) y: \(item.y) z: \(item.z)")
                        }
                    case .error(let err):
                        NSLog("Magnetometer error: \(err)")
                        self.magnetometerToggle = nil
                    case .completed:
                        break
                    }
                }
        } else {
            magnetometerToggle?.dispose()
            magnetometerToggle = nil
        }
    }
    
    @IBAction func ppgToggle(_ sender: Any) {
        if ppgToggle == nil {
            ppgToggle = api.requestStreamSettings(deviceId, feature: DeviceStreamingFeature.ppg)
                .asObservable()
                .flatMap({ (settings) -> Observable<PolarOhrData> in
                    return self.api.startOhrStreaming(self.deviceId, settings: settings.maxSettings())
                })
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .next(let data):
                        if(data.type == OhrDataType.ppg3_ambient1) {
                            for item in data.samples {
                                NSLog("    ppg0: \(item[0]) ppg1: \(item[1]) ppg2: \(item[2]) ambient: \(item[3])")
                            }
                        }
                    case .error(let err):
                        NSLog("PPG error: \(err)")
                        self.ppgToggle = nil
                    case .completed:
                        NSLog("ppg complete")
                    }
                }
        } else {
            ppgToggle?.dispose()
            ppgToggle = nil
        }
    }
    
    @IBAction func ppiToggle(_ sender: Any) {
        if ppiToggle == nil {
            ppiToggle = api.startOhrPPIStreaming(deviceId)
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .next(let data):
                        for item in data.samples {
                            NSLog("    PPI: \(item.ppInMs) sample.blockerBit: \(item.blockerBit)  errorEstimate: \(item.ppErrorEstimate)")
                        }
                    case .error(let err):
                        NSLog("PPI error: \(err)")
                        self.ppiToggle = nil
                    case .completed:
                        NSLog("ppi complete")
                    }
                }
        } else {
            ppiToggle?.dispose()
            ppiToggle = nil
        }
    }
    
    @IBAction func searchToggle(_ sender: Any) {
        if searchToggle == nil {
            searchToggle = api.searchForDevice()
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .completed:
                        NSLog("search complete")
                    case .error(let err):
                        NSLog("search error: \(err)")
                    case .next(let item):
                        NSLog("polar device found: \(item.name) connectable: \(item.connectable) address: \(item.address.uuidString)")
                    }
                }
        } else {
            searchToggle?.dispose()
            searchToggle = nil
        }
    }
    
    @IBAction func listExercises(_ sender: Any) {
        _ = api.fetchStoredExerciseList(deviceId)
            .observe(on: MainScheduler.instance)
            .subscribe{ e in
                switch e {
                case .completed:
                    NSLog("list exercises done")
                case .error(let err):
                    NSLog("failed to list exercises: \(err)")
                case .next(let polarExerciseEntry):
                    NSLog("entry: \(polarExerciseEntry.date.description) path: \(polarExerciseEntry.path) id: \(polarExerciseEntry.entryId)");
                    self.exerciseEntry = polarExerciseEntry
                }
            }
    }
    
    @IBAction func readExercise(_ sender: Any) {
        guard let e = exerciseEntry else {
            return
        }
        _ = api.fetchExercise(deviceId, entry: e)
            .observe(on: MainScheduler.instance)
            .subscribe{ e in
                switch e {
                case .failure(let err):
                    NSLog("failed to read exercises: \(err)")
                case .success(let data):
                    NSLog("exercise data count: \(data.samples.count) samples: \(data.samples)")
                }
            }
    }
    
    @IBAction func removeExercise(_ sender: Any) {
        guard let e = exerciseEntry else {
            return
        }
        _ = api.removeExercise(deviceId, entry: e)
            .observe(on: MainScheduler.instance)
            .subscribe{ e in
                switch e {
                case .completed:
                    NSLog("remove completed")
                case .error(let err):
                    NSLog("remove failed: \(err)")
                @unknown default:
                    fatalError()
                }
            }
    }
    
    @IBAction func startH10Recording(_ sender: Any) {
        _ = api.startRecording(deviceId, exerciseId: "TEST_APP_ID", interval: .interval_1s, sampleType: .rr)
            .observe(on: MainScheduler.instance)
            .subscribe{ e in
                switch e {
                case .completed:
                    NSLog("recording started")
                case .error(let err):
                    NSLog("recording start fail: \(err)")
                @unknown default:
                    fatalError()
                }
            }
    }
    
    @IBAction func stopH10Recording(_ sender: Any) {
        _ = api.stopRecording(deviceId)
            .observe(on: MainScheduler.instance)
            .subscribe{ e in
                switch e {
                case .completed:
                    NSLog("recording stopped")
                case .error(let err):
                    NSLog("recording stop fail: \(err)")
                @unknown default:
                    fatalError()
                }
            }
    }
    
    @IBAction func H10RecordingStatus(_ sender: Any) {
        _ = api.requestRecordingStatus(deviceId)
            .observe(on: MainScheduler.instance)
            .subscribe{ e in
                switch e {
                case .failure(let err):
                    NSLog("recording status request failed: \(err)")
                case .success(let pair):
                    NSLog("recording on: \(pair.ongoing) id: \(pair.entryId)")
                }
            }
    }
    
    @IBAction func setTime(_ sender: Any) {
        let time = Date()
        let timeZone = TimeZone.current
        _ = api.setLocalTime(deviceId, time: time, zone: timeZone)
            .observe(on: MainScheduler.instance)
            .subscribe{ e in
                switch e {
                case .completed:
                    NSLog("time set to device")
                case .error(let err):
                    NSLog("set time failed: \(err)")
                @unknown default:
                    fatalError()
                }
            }
    }
    
    // PolarBleApiObserver
    func deviceConnecting(_ polarDeviceInfo: PolarDeviceInfo) {
        NSLog("DEVICE CONNECTING: \(polarDeviceInfo)")
    }
    
    func deviceConnected(_ polarDeviceInfo: PolarDeviceInfo) {
        NSLog("DEVICE CONNECTED: \(polarDeviceInfo)")
        deviceId = polarDeviceInfo.deviceId
    }
    
    func deviceDisconnected(_ polarDeviceInfo: PolarDeviceInfo) {
        NSLog("DISCONNECTED: \(polarDeviceInfo)")
    }
    
    // PolarBleApiDeviceInfoObserver
    func batteryLevelReceived(_ identifier: String, batteryLevel: UInt) {
        NSLog("battery level updated: \(batteryLevel)")
    }
    
    func disInformationReceived(_ identifier: String, uuid: CBUUID, value: String) {
        NSLog("dis info: \(uuid.uuidString) value: \(value)")
    }
    
    // PolarBleApiDeviceHrObserver
    func hrValueReceived(_ identifier: String, data: PolarHrData) {
        NSLog("(\(identifier)) HR notification: \(data.hr) rrs: \(data.rrs) rrsMs: \(data.rrsMs) contact: \(data.contact) contact supported: \(data.contactSupported)")
    }
    
    func hrFeatureReady(_ identifier: String) {
        NSLog("HR READY")
    }
    
    func streamingFeaturesReady(_ identifier: String, streamingFeatures: Set<DeviceStreamingFeature>) {
        for feature in streamingFeatures {
            NSLog("Feature \(feature) is ready.")
        }
    }
    
    // PolarBleApiPowerStateObserver
    func blePowerOn() {
        NSLog("BLE ON")
    }
    
    func blePowerOff() {
        NSLog("BLE OFF")
    }
    
    // PPI
    func ohrPPIFeatureReady(_ identifier: String) {
        NSLog("PPI Feature ready")
    }
    
    func ftpFeatureReady(_ identifier: String) {
        NSLog("FTP ready")
    }
    
    func message(_ str: String) {
        NSLog("Polar SDK log:  \(str)")
    }
    
    /// ccc write observer
    func cccWrite(_ address: UUID, characteristic: CBUUID) {
        NSLog("ccc write: \(address) chr: \(characteristic)")
    }
}
