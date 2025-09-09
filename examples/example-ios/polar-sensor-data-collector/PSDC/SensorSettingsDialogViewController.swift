
import UIKit
import PolarBleSdk
import RxSwift

class SensorSettingsDialogViewController: UIViewController, UITableViewDelegate, UITableViewDataSource {
    
    var available = Set<UInt16>()
    var obs: ((RxSwift.SingleEvent<UInt16>) -> ())?
    
    @IBOutlet weak var titleLabel: UILabel!
    @IBOutlet weak var tableView: UITableView!
    
    override func viewDidLoad() {
        super.viewDidLoad()
        tableView.allowsSelection = true
        tableView.rowHeight = 40
    }
    
    func start(_ text: String, set: Set<UInt16>) -> Single<UInt16> {
        self.available = set
        self.titleLabel.text = text
        return Single.create{ observer in
            self.obs = observer
            self.tableView.reloadData()
            return Disposables.create {
                if !self.isBeingDismissed {
                    self.dismiss(animated: false) {
                    }
                }
            }
        }.subscribe(on: MainScheduler.instance)
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
        self.obs?(.success(available[available.index(available.startIndex, offsetBy: indexPath.row)]))
        self.dismiss(animated: false) {
        }
    }
}
