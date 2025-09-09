
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
                      PolarBleApiLogger {
    func deviceDisconnected(_ identifier: PolarBleSdk.PolarDeviceInfo, pairingError: Bool) {
        return
    }
    
    func hrValueReceived(_ identifier: String, data: (hr: UInt8, rrs: [Int], rrsMs: [Int], contact: Bool, contactSupported: Bool)) {
        return
    }
    
    func disInformationReceivedWithKeysAsStrings(_ identifier: String, key: String, value: String) {
        return
    }
    
    func streamingFeaturesReady(_ identifier: String, streamingFeatures: Set<PolarBleSdk.PolarDeviceDataType>) {
        return
    }
    
    func bleSdkFeatureReady(_ identifier: String, feature: PolarBleSdk.PolarBleSdkFeature) {
        return
    }
    
    
    @IBOutlet weak var ppgSelectButton: UIButton!
    @IBOutlet weak var accSelectButton: UIButton!
    @IBOutlet weak var connectionsButton: UIButton!
    @IBOutlet weak var disconnectButton: UIButton!
    @IBOutlet weak var sensorDatalogButton: UIButton!
    
    var connectedDevices: [(PolarDeviceInfo, Disposable?)] = [] {
        didSet {
            disconnectButton.isEnabled = !connectedDevices.isEmpty
        }
    }
    var selectedDevice: PolarDeviceInfo?
    
    var api = PolarBleApiDefaultImpl.polarImplementation(DispatchQueue.main, features: [PolarBleSdkFeature.feature_polar_online_streaming,
                                                                                        PolarBleSdkFeature.feature_hr,
                                                                                        PolarBleSdkFeature.feature_battery_info,
                                                                                        PolarBleSdkFeature.feature_device_info,
                                                                                        PolarBleSdkFeature.feature_polar_offline_recording
                                                                                        ]
    )
    var accSelected = false, ppgSelected = false
    var previousAccData: PolarAccData?
    var previousPpgData: PolarPpgData?
    var previousPpiData: PolarPpiData?
    var previousEcgData: PolarEcgData?
    let collector = DataCollector()
    var timer: Disposable?
    var logConfig: SDLogConfig?
    let disposeBag = DisposeBag()
    var supportsSdLog = false
    
    @IBOutlet weak var ecgSwitch: UISwitch!
    @IBOutlet weak var ppiSwitch: UISwitch!
    @IBOutlet weak var ppgSwitch: UISwitch!
    @IBOutlet weak var accSwitch: UISwitch!
    @IBOutlet weak var deviceState: UILabel!
    @IBOutlet weak var accZ: UILabel!
    @IBOutlet weak var accY: UILabel!
    @IBOutlet weak var accX: UILabel!
    @IBOutlet weak var btState: UILabel!
    @IBOutlet weak var sdkVersion: UILabel!
    @IBOutlet weak var ppg0: UILabel!
    @IBOutlet weak var ppg1: UILabel!
    @IBOutlet weak var ppg2: UILabel!
    @IBOutlet weak var batteryLevel: UILabel!
    @IBOutlet weak var firmwareVersion: UILabel!
    @IBOutlet weak var hr: UILabel!
    @IBOutlet weak var rrsMs: UILabel!
    @IBOutlet weak var ecg: UILabel!
    @IBOutlet weak var ppi: UILabel!
    @IBOutlet weak var bioz: UILabel!
    
    override func viewDidLoad() {
        super.viewDidLoad()
        connectionsButton.layer.cornerRadius = 10
        connectionsButton.clipsToBounds = true
        disconnectButton.layer.cornerRadius = 10
        disconnectButton.clipsToBounds = true
        disconnectButton.isEnabled = !connectedDevices.isEmpty
        accSelectButton.layer.cornerRadius = 10
        accSelectButton.clipsToBounds = true
        ppgSelectButton.layer.cornerRadius = 10
        ppgSelectButton.clipsToBounds = true
        sensorDatalogButton.layer.cornerRadius = 10
        sensorDatalogButton.clipsToBounds = true
        sensorDatalogButton.isEnabled = false
        api.observer = self
        api.deviceFeaturesObserver = self
        api.deviceHrObserver = self
        api.deviceInfoObserver = self
        api.logger = self
        api.powerStateObserver = self
        self.btState.text = api.isBlePowered ? "BT ON" : "BT OFF"
        sdkVersion.text = PolarBleApiDefaultImpl.versionInfo()
        api.automaticReconnection = false
        UIApplication.shared.isIdleTimerDisabled = true
    }
    
    @IBAction func startToggled(_ sender: Any) {
        let storyboard = UIStoryboard(name: "SensorSelectPopup", bundle: nil)
        let secondViewController = storyboard.instantiateViewController(withIdentifier: "SensorSelectPopupViewController") as! SensorSelectPopupViewController
        secondViewController.delegate = self
        secondViewController.api = api
        secondViewController.connectedDevices = connectedDevices
        navigationController?.pushViewController(secondViewController, animated: true)
        self.modalPresentationStyle = UIModalPresentationStyle.currentContext
        self.present(secondViewController, animated: true, completion: nil)
    }
    
    @IBAction func disconnectToggled(_ sender: Any) {
        do {
            try self.api.disconnectFromDevice(selectedDevice?.deviceId ?? "")
        } catch( _) {
        }
        
        self.batteryLevel.text = "-"
        self.firmwareVersion.text = "-"
        
        if let device = selectedDevice, let index = connectedDevices.firstIndex(where: { $0.0.deviceId == device.deviceId }) {
            connectedDevices[index].1?.dispose()
            connectedDevices.remove(at: index)
        }
        selectedDevice = nil
    }
    
    @IBAction func sensorSettings(_ sender: Any) {
        if selectedDevice == nil {
            sensorDatalogButton.isEnabled = false
        } else {
            checkLogConfigAvailability()
            if self.supportsSdLog {
                let storyboard = UIStoryboard(name: "SensorDatalogSettingsPopup", bundle: nil)
                let datalogSettingsController = storyboard.instantiateViewController(withIdentifier: "SensorDatalogSettingsViewController") as! SensorDatalogSettingsViewController
                datalogSettingsController.delegate = self
                datalogSettingsController.api = api
                datalogSettingsController.deviceId = self.selectedDevice!.deviceId
                navigationController?.pushViewController(datalogSettingsController, animated: true)
                self.modalPresentationStyle = UIModalPresentationStyle.currentContext
                self.present(datalogSettingsController, animated: true, completion: nil)
            } else {
                sensorDatalogButton.isEnabled = false
                sensorDatalogButton.setTitle("Sensor Datalog not supported", for: .disabled)
            }
        }
    }
    
    override func prepare(for segue: UIStoryboardSegue, sender: Any?) {
        super.prepare(for: segue, sender: sender)
    }
    
    func setDevice(_ device: PolarDeviceInfo) {
        print("device set \(device.name)")
        selectedDevice = device
        connectedDevices.append((device, nil))
        self.connectionsButton.setTitle("CONNECTIONS", for: .normal)
        do{
            try api.connectToDevice(device.deviceId)
            sensorDatalogButton.isEnabled = true
        } catch let err {
            print("connect error: \(err)")
        }
    }
    
    func checkLogConfigAvailability() {
        api.getSDLogConfiguration(self.selectedDevice!.deviceId)
            .observe(on: MainScheduler.instance)
            .subscribe{ [self] e in
                switch e {
                case .success(_):
                    self.supportsSdLog = true
                case .failure(let err):
                    self.supportsSdLog = false
                    self.dismiss(animated: false)
                }
            }.disposed(by: disposeBag)
    }
    
    @IBAction func ppgSelection(_ sender: Any) {
        ppgSelected = !ppgSelected
        ppgSelectButton.setTitle(ppgSelected ? "User selects" : "ACC default max", for: .normal)
    }
    
    @IBAction func accSelection(_ sender: Any) {
        accSelected = !accSelected
        accSelectButton.setTitle(accSelected ? "User selects" : "ACC default max", for: .normal)
    }
    
    func showSettingsSelection(_ title: String, settings: Set<UInt16>) -> Single<UInt16> {
        let storyboard = UIStoryboard(name: "SensorSettingsDialog", bundle: nil)
        let secondViewController = storyboard.instantiateViewController(withIdentifier: "SensorSettingsDialogViewController") as! SensorSettingsDialogViewController
        navigationController?.pushViewController(secondViewController, animated: true)
        self.modalPresentationStyle = UIModalPresentationStyle.currentContext
        self.present(secondViewController, animated: true, completion: nil)
        return secondViewController.start(title, set: settings)
    }

    func startTimer(for device: PolarDeviceInfo) {
        let timer = Observable<Int>
            .interval(RxTimeInterval.seconds(1), scheduler: MainScheduler.instance)
            .observe(on: MainScheduler.instance)
            .subscribe { e in
                switch e {
                case .next(let time):
                    let hours = (Int) (time / 3600)
                    let minutes = (Int) (time/60 % 60)
                    let seconds = (Int) (time % 60)
                    if let selectedDevice = self.selectedDevice, selectedDevice.deviceId == device.deviceId {
                        self.deviceState.text = String(format: "%02d:%02d:%02d", hours, minutes, seconds)
                    }
                case .completed:
                    break
                case .error(let err):
                    print("timer failed: \(err)")
                }
            }
        if let index = connectedDevices.firstIndex(where: { $0.0.deviceId == device.deviceId && $0.0.address == device.address }) {
            connectedDevices[index].1 = timer
        } else {
            connectedDevices.append((device, timer))
        }
    }
    
    //BLE
    func hrFeatureReady(_ identifier: String) {
        
    }
    
    func ecgFeatureReady(_ identifier: String) {
        if ecgSwitch.isOn {
            _ = api.requestStreamSettings(identifier, feature: PolarDeviceDataType.ecg)
                .asObservable()
                .flatMap({ (settings) -> Observable<PolarEcgData> in
                    self.collector.startEcgStream(self.selectedDevice!.name)
                    return self.api.startEcgStreaming(identifier, settings: settings.maxSettings())
                })
                .observe(on: MainScheduler.instance).subscribe{ e in
                    switch e {
                    case .completed:
                        break
                    case .next(let data):
                        if self.previousEcgData != nil {
                            let delta = (data.timeStamp - self.previousEcgData!.timeStamp) / UInt64(data.samples.count)
                            var base = self.previousEcgData!.timeStamp - (UInt64(self.previousEcgData!.samples.count-1)*delta)
                            self.previousEcgData!.samples.forEach({ (arg0) in
                                self.collector.streamEcg(base, ecg: arg0.voltage)
                                base += delta
                            })
                        }
                        self.previousEcgData = data
                        for sample in data.samples {
                            self.ecg.text = "\(sample)"
                        }
                    case .error(let err):
                        print("ECG error: \(err)")
                        self.ecg.text = "-"
                    }
                }
        }
    }
    
    // TODO Fix this function as this does not get compiled with the latest changes in PolarBleApiDefaultImpl.
    /*func accFeatureReady(_ identifier: String) {
            if accSwitch.isOn {
                _ = api.requestStreamSettings(identifier, feature: PolarDeviceDataType.acc)
                    .asObservable()
                    .flatMap({ (settings) -> Observable<PolarSensorSetting> in
                        if self.accSelected {
                            return self.showSettingsSelection("ACC", settings: settings.settings[PolarSensorSetting.SettingType.sampleRate] ?? Set()).asObservable().map({ (value) -> PolarSensorSetting in
                                var c = settings.settings.mapValues({ (set) -> UInt32 in
                                    return set.max() ?? 0
                                })
                                c[PolarSensorSetting.SettingType.sampleRate] = UInt32(value)
                                return PolarSensorSetting(c)
                            })
                        }
                        return Observable.just(settings.maxSettings())
                    })
                    .flatMap({ (selected) -> Observable<PolarAccData> in
                        self.collector.startACCStream(self.selectedDevice!.name)
                        return self.api.startAccStreaming(identifier, settings: selected)
                    })
                    .observe(on: MainScheduler.instance)
                    .subscribe{ e in
                        switch e {
                        case .next(let data):
                            if self.previousAccData != nil {
                                let delta = (data.timeStamp - self.previousAccData!.timeStamp) / UInt64(data.samples.count)
                                var base = self.previousAccData!.timeStamp - (UInt64(self.previousAccData!.samples.count-1)*delta)
                                self.previousAccData!.samples.forEach({ (arg0) in
                                    let (x, y, z) = arg0
                                    self.collector.streamAcc(base, x: x, y: y, z: z)
                                    base += delta
                                })
                            }
                            self.previousAccData = data
                            data.samples.forEach({ (arg0) in
                                let (x, y, z) = arg0
                                self.accX.text = "\(x)"
                                self.accY.text = "\(y)"
                                self.accZ.text = "\(z)"
                            })
                        case .error(let err):
                            NSLog("ACC error: \(err)")
                            self.accX.text = "-"
                            self.accY.text = "-"
                            self.accZ.text = "-"
                        case .completed:
                            break
                        }
                    }
            }
        } */
    
    func calculateBaseAndDelta(_ timeStamp0: UInt64, timeStamp1: UInt64, count0: UInt64, count1: UInt64) -> (base :UInt64, delta: UInt64) {
        let delta = (timeStamp1 - timeStamp0) / UInt64(count1)
        let base = timeStamp0 - (UInt64(count0-1)*delta)
        return (base,delta)
    }
    
    // TODO Fix this function as this does not get compiled with the latest changes in PolarBleApiDefaultImpl.
    /* func ohrPPGFeatureReady(_ identifier: String) {
        if ppgSwitch.isOn {
            _ = api.requestStreamSettings(identifier, feature: PolarDeviceDataType.ppg)
                .asObservable()
                .flatMap({ (settings) -> Observable<PolarSensorSetting> in
                    if self.ppgSelected {
                        return self.showSettingsSelection("PPG", settings: settings.settings[PolarSensorSetting.SettingType.sampleRate] ?? Set()).asObservable().map({ (value) -> PolarSensorSetting in
                            var c = settings.settings.mapValues({ (set) -> UInt32 in
                                return set.max() ?? 0
                            })
                            c[PolarSensorSetting.SettingType.sampleRate] = UInt32(value)
                            return PolarSensorSetting(c)
                        })
                    }
                    return Observable.just(settings.maxSettings())
                })
                .flatMap({ (selected) -> Observable<PolarPpgData> in
                    self.collector.startPPGStream(self.selectedDevice!.name)
                    return self.api.startPpgStreaming(identifier, settings: selected)
                })
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .next(let data):
                        if self.previousPpgData != nil {
                            let delta = (data.samples.first!.timeStamp - ((self.previousPpgData?.samples.first!.timeStamp)!)) / UInt64(data.samples.count)
                            var base = self.previousPpgData!.samples.first!.timeStamp - (UInt64(self.previousPpgData!.samples.count-1)*delta)
                            self.previousPpgData!.samples.forEach({ (arg0) in
                                let (ppg0, ppg1, ppg2, ambient) = arg0
                                self.collector.streamPpg(base, ppg0: ppg0, ppg1: ppg1, ppg2: ppg2, ambient: ambient)
                                base += delta
                            })
                        }
                        self.previousPpgData = data
                        for item in data.samples {
                            self.ppg0.text = "\(item.ppg0)"
                            self.ppg1.text = "\(item.ppg1)"
                            self.ppg2.text = "\(item.ppg2)"
                        }
                    case .error(let err):
                        NSLog("PPG error: \(err)")
                        self.ppg0.text = "-"
                        self.ppg1.text = "-"
                        self.ppg2.text = "-"
                    case .completed:
                        break
                    }
                }
        }
    } */
    
    func ohrPPIFeatureReady(_ identifier: String) {
        if ppiSwitch.isOn {
            collector.startPPIStream(selectedDevice!.name)
            _ = api.startPpiStreaming(identifier)
                .observe(on: MainScheduler.instance)
                .subscribe{ e in
                    switch e {
                    case .completed:
                        break
                    case .error(let err):
                        NSLog("PPI error: \(err)")
                        self.ppi.text = "-"
                    case .next(let data):
                        if self.previousPpiData != nil {
                            let delta = (data.timeStamp - self.previousPpiData!.timeStamp) / UInt64(data.samples.count)
                            var base = self.previousPpiData!.timeStamp - (UInt64(self.previousPpiData!.samples.count-1)*delta)
                            self.previousPpiData!.samples.forEach({ (arg0) in
                                self.collector.streamPpi(base, ppi: arg0.ppInMs, errorEstimate: arg0.ppErrorEstimate, blocker: arg0.blockerBit, contact: arg0.skinContactStatus, contactSupported: arg0.skinContactSupported, hr: arg0.hr)
                                base += delta
                            })
                        }
                        self.previousPpiData = data
                        for item in data.samples {
                            self.ppi.text = "\(item.ppInMs)"
                        }
                    }
                }
        }
    }
    
    func ftpFeatureReady(_ identifier: String) {
        // do nothing
    }
    
    func biozFeatureReady(_ identifier: String) {
        if self.ppgSwitch.isOn {
            _ = api.requestStreamSettings(identifier, feature: PolarDeviceDataType.ppg)
                .asObservable()
                .flatMap({ (settings) -> Observable<PolarPpgData> in
                    self.api.startPpgStreaming(identifier, settings: settings.maxSettings())
                })
                .observe(on: MainScheduler.instance)
                .subscribe { e in
                    switch e {
                    case .completed:
                        break
                    case .error(let err):
                        print("BIOZ error: \(err)")
                        self.bioz.text = "-"
                    case .next(let value):
                        value.samples.forEach({ (bioz) in
                            self.bioz.text = "\(bioz)"
                        })
                    }
                }
        }
    }
    
    func message(_ str: String) {
        NSLog(str)
    }
    
    func deviceConnecting(_ identifier: PolarDeviceInfo) {
        print("connecting")
        self.btState.text = "CONNECTING: \(identifier.deviceId)"
    }
    
    func deviceConnected(_ identifier: PolarDeviceInfo) {
        print("connected")
        let deviceIds = connectedDevices.map { $0.0.deviceId }
        let connectedDevicesText = "CONNECTED: \(deviceIds.joined(separator: ", "))"
        self.btState.text = connectedDevicesText
        selectedDevice = identifier
        startTimer(for: identifier)
    }
    
    func deviceDisconnected(_ identifier: PolarDeviceInfo) {
        print("disconnected")
        self.connectionsButton.setTitle("START", for: .normal)
        hr.text = "-"
        rrsMs.text = "-"
        if let index = connectedDevices.firstIndex(where: { $0.0.deviceId == identifier.deviceId }) {
            print("dispose timer")
            connectedDevices[index].1?.dispose()
            connectedDevices.remove(at: index)
        }
        if let newDevice = connectedDevices.first?.0 {
            selectedDevice = newDevice
        } else {
            selectedDevice = nil
        }
        if (connectedDevices.isEmpty) {
            self.btState.text = "NO DEVICES CONNECTED"
        } else {
            let deviceIds = connectedDevices.map { $0.0.deviceId }
            let connectedDevicesText = "CONNECTED: \(deviceIds.joined(separator: ", "))"
            self.btState.text = connectedDevicesText
        }

        collector.finalizeStreams(identifier.name, view: self)
    }
    
    func blePowerOn() {
        btState.text = "BT ON"
    }
    
    func blePowerOff() {
        btState.text = "BT OFF"
    }
    
    func hrValueReceived(_ identifier: String, data: PolarHrData) {
        self.hr.text = "\(data[0].hr)"
        self.rrsMs.text = "\(data[0].rrsMs[0])"
    }

    func batteryLevelReceived(_ identifier: String, batteryLevel: UInt) {
        self.batteryLevel.text = "\(batteryLevel)%"
    }
    
    func disInformationReceived(_ identifier: String, uuid: CBUUID, value: String) {
        if BleDisClient.FIRMWARE_REVISION_STRING.uuidString.isEqual(uuid.uuidString.uppercased()) {
            self.firmwareVersion.text = "Firmware version: \(value)"
        }
    }
}
