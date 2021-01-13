
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
    var ppgToggle: Disposable?
    var ppiToggle: Disposable?
    var searchToggle: Disposable?
    var autoConnect: Disposable?
    var entry: PolarExerciseEntry?
    var deviceId = "4F443C26" // TODO replace this with your device id
    
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
    
    @IBAction func accToggle(_ sender: Any) {
        if accToggle == nil {
            accToggle = api.requestAccSettings(deviceId)
                .asObservable()
                .flatMap({ (settings) -> Observable<PolarAccData> in
                    NSLog("settings: \(settings.settings)")
                    return self.api.startAccStreaming(self.deviceId, settings: settings.maxSettings())
                })
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
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
    
    @IBAction func ecgToggle(_ sender: Any) {
        if ecgToggle == nil {
            ecgToggle = api.requestEcgSettings(deviceId)
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
                        NSLog("start ecg error: \(err)")
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
    
    @IBAction func listFilesToggle(_ sender: Any) {
        _ = api.fetchStoredExerciseList(deviceId)
            .observe(on: MainScheduler.instance)
            .subscribe{ e in
                switch e {
                case .completed:
                    NSLog("list files done")
                case .error(let err):
                    NSLog("list files: \(err)")
                case .next(let e):
                    NSLog("entry: \(e.date.description) id: \(e.entryId)")
                    self.entry = e
                }
            }
        
    }
    
    @IBAction func ppgToggle(_ sender: Any) {
        if ppgToggle == nil {
            ppgToggle = api.requestPpgSettings(deviceId)
                .asObservable()
                .flatMap({ (settings) -> Observable<PolarPpgData> in
                    return self.api.startOhrPPGStreaming(self.deviceId, settings: settings.maxSettings())
                })
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .completed:
                        NSLog("ppg finished")
                    case .error(let err):
                        NSLog("start ppg error: \(err)")
                        self.ppgToggle = nil
                    case .next(let data):
                        for item in data.samples {
                            NSLog("    ppg0: \(item.ppg0) ppg1: \(item.ppg1) ppg2: \(item.ppg2)")
                        }
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
                    case .completed:
                        NSLog("ppi complete")
                    case .error(let err):
                        NSLog("start ppi error: \(err)")
                        self.ppiToggle = nil
                    case .next(let data):
                        for item in data.samples {
                            NSLog("PPI: \(item.ppInMs)")
                        }
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
    
    @IBAction func readExercise(_ sender: Any) {
        guard let e = entry else {
            return
        }
        _ = api.fetchExercise(deviceId, entry: e)
            .observe(on: MainScheduler.instance)
            .subscribe{ e in
                switch e {
                case .failure(let err):
                    NSLog("read ex error: \(err)")
                case .success(let data):
                    NSLog("\(data.samples)")
                }
            }
    }
    
    @IBAction func removeExercise(_ sender: Any) {
        guard let e = entry else {
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
        NSLog("(\(identifier)) HR notification: \(data.hr) rrs: \(data.rrs) rrsMs: \(data.rrsMs) c: \(data.contact) s: \(data.contactSupported)")
    }
    
    func hrFeatureReady(_ identifier: String) {
        NSLog("HR READY")
    }
    
    // PolarBleApiDeviceEcgObserver
    func ecgFeatureReady(_ identifier: String) {
        NSLog("ECG READY \(identifier)")
    }
    
    // PolarBleApiDeviceAccelerometerObserver
    func accFeatureReady(_ identifier: String) {
        NSLog("ACC READY")
    }
    
    func ohrPPGFeatureReady(_ identifier: String) {
        NSLog("OHR PPG ready")
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
        NSLog(str)
    }
    
    /// ccc write observer
    func cccWrite(_ address: UUID, characteristic: CBUUID) {
        NSLog("ccc write: \(address) chr: \(characteristic)")
    }
}
