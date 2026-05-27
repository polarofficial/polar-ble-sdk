
import UIKit
import PolarBleSdk

class SensorSettingsDialogViewController: UIViewController, UITableViewDelegate, UITableViewDataSource {
    
    var available = Set<UInt16>()
    private var continuation: CheckedContinuation<UInt16, Never>?
    
    @IBOutlet weak var titleLabel: UILabel!
    @IBOutlet weak var tableView: UITableView!
    
    override func viewDidLoad() {
        super.viewDidLoad()
        tableView.allowsSelection = true
        tableView.rowHeight = 40
    }
    
    func start(_ text: String, set: Set<UInt16>) async -> UInt16 {
        self.available = set
        self.titleLabel.text = text
        tableView.reloadData()
        return await withCheckedContinuation { cont in
            self.continuation = cont
        }
    }
    
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return available.count
    }
    
    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = self.tableView.dequeueReusableCell(withIdentifier: "SensorSettingCell") as! SensorSettingCell
        cell.selectionStyle = UITableViewCell.SelectionStyle.default
        cell.setting.text = "\(available[available.index(available.startIndex, offsetBy: indexPath.row)])"
        return cell
    }
    
    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        let value = available[available.index(available.startIndex, offsetBy: indexPath.row)]
        continuation?.resume(returning: value)
        continuation = nil
        self.dismiss(animated: false) {
        }
    }
}
