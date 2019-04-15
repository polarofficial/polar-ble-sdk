import UIKit
import PolarBleSdk
import RxSwift

class ViewController: UIViewController,
    PolarBleApiObserver,
    PolarBleApiPowerStateObserver,
    PolarBleApiDeviceFeaturesObserver,
    PolarBleApiLogger {
  
    func message(_ str: String) {
        NSLog(str)
    }
    
    @IBOutlet weak var textX: UILabel!
    @IBOutlet weak var textY: UILabel!
    @IBOutlet weak var textZ: UILabel!
    @IBOutlet weak var connectionStatus: UILabel!
    @IBOutlet weak var buttonStart: UIButton!
    
    var deviceId = ""
    lazy var api = PolarBleApiDefaultImpl.polarImplementation(DispatchQueue.main, features: Features.polarSensorStreaming.rawValue)
    var accToggle: Disposable?
    var multiplier: Double?
    
    //Bluetooth state check
    let alertMessage = "Please enable Bluetooth in your device's Settings"
    let alertTitle = "Bluetooth is off"
    var btIsOn = false
    
    func showBtAlert(){
        let btAlert = UIAlertController(title: alertTitle, message: alertMessage, preferredStyle: UIAlertController.Style.alert)
        btAlert.addAction(UIAlertAction(title: "OK", style: .default, handler: nil))
        self.present(btAlert, animated: true, completion: nil)
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        api.observer = self
        api.powerStateObserver = self
        api.deviceFeaturesObserver = self
        //api.logger = self
        _ = api.startAutoConnectToDevice(-50, service: nil, polarDeviceType: nil).subscribe()
        connectionStatus.text = ""
    }
    
    @IBAction func streamACC(_ sender: Any){
        if accToggle == nil && btIsOn {
            buttonStart.setTitle("Stop ACC", for: .normal)
            accToggle = api.requestAccSettings(deviceId).asObservable().flatMap({ (settings) -> Observable<PolarAccData> in
                return self.api.startAccStreaming(self.deviceId, settings: settings.maxSettings())
            }).observeOn(MainScheduler.instance).subscribe{ e in
                switch e {
                case .next(let data):
                    for item in data.samples {
                        self.textX.text = String(format: "%.2f", ((Float(item.x) / 1000.0)))
                        self.textY.text = String(format: "%.2f", ((Float(item.y) / 1000.0)))
                        self.textZ.text = String(format: "%.2f", ((Float(item.z) / 1000.0)))
                    }
                case .error(let err):
                    print("ACC error: \(err)")
                    self.accToggle = nil
                case .completed:
                    break
                }
            }
        } else {
            if(!btIsOn){
                showBtAlert()
            }
            accToggle?.dispose()
            accToggle = nil
            buttonStart.setTitle("Start ACC", for: .normal)
        }
    }
    
    func hrFeatureReady(_ identifier: String) {
        NSLog("HR CONNECTED")
    }
    
    func ecgFeatureReady(_ identifier: String) {
        NSLog("ECG CONNECTED")
    }
    
    func accFeatureReady(_ identifier: String) {
        NSLog("ACC CONNECTED")
    }
    
    func ohrPPGFeatureReady(_ identifier: String) {
        NSLog("PPG CONNECTED")
    }
    
    func ohrPPIFeatureReady(_ identifier: String) {
        NSLog("PPI CONNECTED")
    }
    
    func ftpFeatureReady(_ identifier: String) {
        NSLog("FTP CONNECTED")
    }
    
    func deviceConnecting(_ identifier: PolarDeviceInfo) {
        connectionStatus.text = "Connecting"
    }
    
    func deviceConnected(_ identifier: PolarDeviceInfo) {
        deviceId = identifier.deviceId
        connectionStatus.text = "Connected"
    }
    
    func deviceDisconnected(_ identifier: PolarDeviceInfo) {
        connectionStatus.text = "Disconnected"
    }
    
    func blePowerOn() {
        NSLog("Bluetooth on")
        btIsOn = true
    }
    
    func blePowerOff() {
        NSLog("Bluetooth off")
        btIsOn = false
    }
}

