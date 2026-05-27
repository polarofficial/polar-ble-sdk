
import UIKit
import PolarBleSdk

class SensorDatalogSettingsViewController: UIViewController {
    
    var available = Set<UInt16>()
    var api: PolarBleApi!
    var deviceId = String()
    
    weak var delegate: ViewController!
    @IBOutlet weak var ohrSwitch: UISwitch!
    @IBOutlet weak var accSwitch: UISwitch!
    @IBOutlet weak var skinTempSwitch: UISwitch!
    @IBOutlet weak var metSwitch: UISwitch!
    @IBOutlet weak var caloriesSwitch: UISwitch!
    @IBOutlet weak var sleepSwitch: UISwitch!
    
    @IBOutlet weak var sdLogCancelButton: UIButton!
    @IBOutlet weak var sdLogSetButton: UIButton!
    var logConfig: SDLogConfig?
    
    override func viewDidLoad() {
        super.viewDidLoad()
        sdLogCancelButton.layer.cornerRadius = 10
        sdLogCancelButton.clipsToBounds = true
        sdLogSetButton.layer.cornerRadius = 10
        sdLogSetButton.clipsToBounds = true
        
        Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                let config = try await api.getSDLogConfiguration(deviceId)
                logConfig = config
                ohrSwitch.isOn = config.ohrLogEnabled
                accSwitch.isOn = config.accelerationLogEnabled
                skinTempSwitch.isOn = config.skinTemperatureLogEnabled
                metSwitch.isOn = config.metLogEnabled
                caloriesSwitch.isOn = config.caloriesLogEnabled
                sleepSwitch.isOn = config.sleepLogEnabled
            } catch {
                print("Failed to load sensor datalog settings, \(error)")
                dismiss(animated: false)
            }
        }
    }
    
    @IBAction func setSDLogButtonSelected(_ sender: Any) {
        setSDLogSettings()
        self.dismiss(animated: false)
    }
    
    @IBAction func cancel(_ sender: Any) {
        self.dismiss(animated: false) {
            // do nothing
        }
    }
    
    @IBAction func setOhr(_ sender: Any) {
        self.logConfig?.ohrLogEnabled = ohrSwitch.isOn
    }
    
    @IBAction func setAcc(_ sender: Any) {
        self.logConfig?.accelerationLogEnabled = accSwitch.isOn
    }
    
    @IBAction func setSkinTemp(_ sender: Any) {
        self.logConfig?.skinTemperatureLogEnabled = skinTempSwitch.isOn
    }
    
    @IBAction func setMet(_ sender: Any) {
        self.logConfig?.metLogEnabled = metSwitch.isOn
    }
    
    @IBAction func setCalories(_ sender: Any) {
        caloriesSwitch.isOn = ((self.logConfig?.caloriesLogEnabled) != nil)
        self.logConfig?.caloriesLogEnabled = caloriesSwitch.isOn
    }
    
    @IBAction func setSleep(_ sender: Any) {
        self.logConfig?.sleepLogEnabled = sleepSwitch.isOn
    }
    
    func setSDLogSettings() {
    
        self.logConfig?.accelerationLogEnabled = accSwitch.isOn
        self.logConfig?.ohrLogEnabled = ohrSwitch.isOn
        self.logConfig?.skinTemperatureLogEnabled = skinTempSwitch.isOn
        self.logConfig?.metLogEnabled = metSwitch.isOn
        self.logConfig?.caloriesLogEnabled = caloriesSwitch.isOn
        self.logConfig?.sleepLogEnabled = sleepSwitch.isOn
        
        Task.detached {
            do {
                try await self.api.setSDLogConfiguration(self.deviceId, logConfiguration: self.logConfig!).value
            }
            catch let err {
                NSLog("Setting Sensor Datalog failed: \(err)")
            }
        }
    }
}
