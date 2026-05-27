
import UIKit
import PolarBleSdk

class SensorSelectPopupViewController: UIViewController, UITableViewDelegate, UITableViewDataSource  {
    
    @IBOutlet weak var tableView: UITableView!
    @IBOutlet weak var cancelButton: UIButton!
    weak var delegate: ViewController!
    var api: PolarBleApi!
    var devices = [PolarDeviceInfo]()
    var searchTask: Task<Void, Never>?
    var connectedDevices: [PolarDeviceInfo] = []
    
    override func viewDidLoad() {
        super.viewDidLoad()
        cancelButton.layer.cornerRadius = 10
        cancelButton.clipsToBounds = true
        self.tableView.layer.cornerRadius = 5
        searchTask = Task { [weak self] in
            guard let self else { return }
            do {
                for try await value in api.searchForDevice() {
                    await MainActor.run {
                        self.devices.append(value)
                        self.devices.sort(by: { $0.rssi > $1.rssi })
                        self.tableView.reloadData()
                    }
                }
            } catch {
                print("scan error: \(error)")
            }
        }
    }
    
    @IBAction func cancel(_ sender: Any) {
        searchTask?.cancel()
        searchTask = nil
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
        
        if connectedDevices.contains(where: { $0.deviceId == info.deviceId }) {
            cell.backgroundColor = UIColor(hex: ColorConstants.BLUETOOTH_BLUE)
        } else {
            cell.backgroundColor = UIColor.clear
        }
        return cell
    }
    
    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        searchTask?.cancel()
        searchTask = nil
        self.dismiss(animated: false) {
            self.delegate!.setDevice(self.devices[indexPath.row])
        }
    }
}
