
import UIKit
import PolarBleSdk
import RxSwift

class SensorSelectPopupViewController: UIViewController, UITableViewDelegate, UITableViewDataSource  {
    
    @IBOutlet weak var tableView: UITableView!
    @IBOutlet weak var cancelButton: UIButton!
    weak var delegate: ViewController!
    var api: PolarBleApi!
    var devices = [PolarDeviceInfo]()
    var disposable: Disposable?
    var connectedDevices: [(PolarDeviceInfo, Disposable?)] = []
    
    override func viewDidLoad() {
        super.viewDidLoad()
        cancelButton.layer.cornerRadius = 10
        cancelButton.clipsToBounds = true
        self.tableView.layer.cornerRadius = 5
        disposable = api.searchForDevice()
            .observe(on: MainScheduler.instance)
            .subscribe{ e in
                switch e {
                case .completed:
                    break
                case .error(let err):
                    print("scan error: \(err)")
                case .next(let value):
                    self.devices.append(value)
                    self.devices.sort(by: { (info1, info2) -> Bool in
                        return info1.rssi > info2.rssi
                    })
                    self.tableView.reloadData()
                }
            }
    }
    
    @IBAction func cancel(_ sender: Any) {
        disposable?.dispose()
        self.dismiss(animated: false) {
            // do nothing
        }
    }
    
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return devices.count
    }
    
    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = self.tableView.dequeueReusableCell(withIdentifier: "SensorSelectCell") as! SensorSelectCell
        let info = devices[indexPath.row]
        cell.label1.text = info.name
        cell.label2.text = "\(info.rssi)"
        
        if connectedDevices.contains(where: { $0.0.deviceId == info.deviceId }) {
            cell.backgroundColor = UIColor(hex: ColorConstants.BLUETOOTH_BLUE)
        } else {
            cell.backgroundColor = UIColor.clear
        }
        return cell
    }
    
    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        disposable?.dispose()
        self.dismiss(animated: false) {
            self.delegate!.setDevice(self.devices[indexPath.row])
        }
    }
}
