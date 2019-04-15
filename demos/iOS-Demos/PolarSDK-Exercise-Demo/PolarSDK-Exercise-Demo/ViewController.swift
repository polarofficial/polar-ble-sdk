

import UIKit
import PolarBleSdk
import RxSwift
import CoreBluetooth

class ViewController: UIViewController, UITableViewDelegate, UITableViewDataSource,
    PolarBleApiObserver,
    PolarBleApiPowerStateObserver,
    PolarBleApiDeviceInfoObserver,
    PolarBleApiDeviceFeaturesObserver {
    
    
    var api: PolarBleApi!
    var deviceId: String?
    @IBOutlet weak var connectionStatus: UILabel!
    @IBOutlet weak var exeTable: UITableView!
    var exerciseArray: [PolarExerciseEntry] = []
    
    let cellReuseID = "cellReuseID"
    
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
        api = PolarBleApiDefaultImpl.polarImplementation(DispatchQueue.main, features: Features.batteryStatus.rawValue | Features.deviceInfo.rawValue | Features.polarFileTransfer.rawValue)
        api.observer = self
        api.powerStateObserver = self
        api.deviceInfoObserver = self
        api.deviceFeaturesObserver = self
        _ = api.startAutoConnectToDevice(-50, service: CBUUID.init(string: "180D"), polarDeviceType: "OH1").subscribe()
        connectionStatus.text = "Disconnected"
    }
    @IBAction func listExercises(_ sender: Any) {
        if(btIsOn && deviceId != nil){
            self.exerciseArray.removeAll()
            _ = api.fetchStoredExerciseList(deviceId!).observeOn(MainScheduler.instance).subscribe { e in
                switch e {
                case .completed:
                    break
                case .next(let entry):
                    print("entry: \(entry.date.description)")
                    self.exerciseArray.append(entry)
                    self.exeTable.reloadData()
                case .error(let err):
                    print("ERROR: \(err)")
                }
            }
        } else {
            showBtAlert()
        }
    }
    
    func numberOfSections(in tableView: UITableView) -> Int {
        return 1
    }
    
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return exerciseArray.count
    }
    
    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath)
        -> UITableViewCell
    {
        let cell = tableView.dequeueReusableCell(withIdentifier: cellReuseID, for: indexPath as IndexPath) as! PolarTableViewCell
        let text = exerciseArray[indexPath.row]
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "dd/MM/yyyy hh:mm"
        cell.infoLabel.text = dateFormatter.string(for: text.date)
        
        return cell
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
        self.exerciseArray.removeAll()
        _ = api.fetchStoredExerciseList(identifier).observeOn(MainScheduler.instance).subscribe { e in
            switch e {
            case .completed:
                break
            case .next(let entry):
                print("entry: \(entry.date.description)")
                self.exerciseArray.append(entry)
                self.exeTable.reloadData()
            case .error(let err):
                print("ERROR: \(err)")
            }
        }
    }
    
    func deviceConnected(_ identifier: PolarDeviceInfo) {
        NSLog("Device connected " + identifier.deviceId)
        deviceId = identifier.deviceId
        connectionStatus.text = "Connected"
    }
    
    func deviceConnecting(_ identifier: PolarDeviceInfo) {
        NSLog("Device connecting " + identifier.deviceId)
        connectionStatus.text = "Connecting"
    }
    
    func deviceDisconnected(_ identifier: PolarDeviceInfo) {
        NSLog("Device disconnected " + identifier.deviceId)
        connectionStatus.text = "Disconnected"
        deviceId = nil
    }
    
    func blePowerOn() {
        NSLog("Bluetooth on")
        btIsOn = true
    }
    
    func blePowerOff() {
        NSLog("Bluetooth off")
        btIsOn = false
    }
    
    func batteryLevelReceived(_ identifier: String, batteryLevel: UInt) {
        NSLog("Battery level \(identifier): \(batteryLevel) ")
    }
    
    func disInformationReceived(_ identifier: String, uuid: CBUUID, value: String) {
        NSLog("DIS info \(identifier): \(uuid.uuidString) : \(value)")
    }
}
