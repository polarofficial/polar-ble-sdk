
import UIKit
import PolarBleSdk
import RxSwift

class ViewController: UIViewController, UICollectionViewDataSource, UICollectionViewDelegate, PolarBleApiPowerStateObserver, PolarBleApiLogger
{
    func message(_ str: String) {
        NSLog(str)
    }
    
    
    @IBOutlet weak var buttonBR: UIButton!
    @IBOutlet weak var collectionView: UICollectionView!
    var api: PolarBleApi!
    var brToggle: Disposable?
    var deviceArray: [String:PolarHrBroadcastData] = [:]
    let reuseID = "reuseID"
    
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
        api = PolarBleApiDefaultImpl.polarImplementation(DispatchQueue.main, features: 0)
        api.powerStateObserver = self
        //api.logger = self
    }
    
    @IBAction func startBR(_ sender: Any) {
        if brToggle == nil && btIsOn {
            buttonBR.setTitle("Stop broadcast", for: .normal)
            brToggle = api.startListenForPolarHrBroadcasts(nil).observeOn(MainScheduler.instance).subscribe { e in
                switch e {
                case .completed:
                    break
                case .error(let err):
                    print("\(err)")
                case .next(let broadcast):
                    NSLog("\(broadcast.deviceInfo.deviceId) HR BROADCAST: \(broadcast.hr) battery: \(broadcast.batteryStatus)")
                    let receivedID = broadcast.deviceInfo.deviceId
                    
                    if(self.deviceArray[receivedID] != nil){
                        if(self.deviceArray[receivedID]?.hr != broadcast.hr){
                            self.deviceArray[receivedID] = broadcast
                        }
                    } else {
                        self.deviceArray[receivedID] = broadcast
                    }
                    self.collectionView.reloadData()
                }
            }
        } else {
            if(!btIsOn){
                showBtAlert()
            }
            brToggle?.dispose()
            brToggle = nil
            buttonBR.setTitle("Start broadcast", for: .normal)
        }
    }
    
    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        return deviceArray.count
    }
    
    func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let cell = collectionView.dequeueReusableCell(withReuseIdentifier: reuseID, for: indexPath as IndexPath) as! PolarCollectionViewCell
        
        cell.idLabel.text = Array(self.deviceArray.keys)[indexPath.item]
        cell.hrLabel.text = String(Array(self.deviceArray.values)[indexPath.item].hr)
        return cell
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

