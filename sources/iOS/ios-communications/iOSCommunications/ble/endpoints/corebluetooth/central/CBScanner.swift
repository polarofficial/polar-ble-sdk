
import Foundation
import CoreBluetooth
import RxSwift

class CBScanner {
    
    enum ScannerState{
        case
        idle,
        stopped,
        scanning
        
        func description () -> String {
            switch self {
            case .idle:     return "IDLE"
            case .stopped:  return "STOPPED"
            case .scanning: return "SCANNING"
            }
        }
    }
    
    enum ScanAction {
        case
        entry,
        exit,
        clientStartScan,
        clientRemoved,
        adminStartScan,
        adminStopScan,
        blePowerOff,
        blePowerOn
        
        func description () -> String {
            switch self {
            case .entry: return "ENTRY"
            case .exit: return "EXIT"
            case .clientStartScan: return "CLIENT_START_SCAN"
            case .clientRemoved: return "CLIENT_REMOVED"
            case .adminStartScan: return "ADMIN_START_SCAN"
            case .adminStopScan: return "ADMIN_STOP_SCAN"
            case .blePowerOff: return "BLE_POWER_OFF"
            case .blePowerOn: return "BLE_POWER_ON"
            }
        }
    }
    
    let central: CBCentralManager
    var state = ScannerState.idle
    var scanObservers = Set<RxObserver<BleDeviceSession>>()
    var scanDisposable: Disposable?
    var services: [CBUUID]?
    var adminStops = 0
    let sessions: AtomicList<CBDeviceSessionImpl>
    let scheduler: SerialDispatchQueueScheduler
    var isScanning: Bool {
        get {
            return state == .scanning
        }
    }
    
    init(_ central: CBCentralManager, queue: DispatchQueue, sessions: AtomicList<CBDeviceSessionImpl>){
        self.central = central
        self.sessions = sessions
        self.scheduler = SerialDispatchQueueScheduler.init(queue: queue, internalSerialQueueName: "CBScannerQueue")
    }
    
    func setServices(_ services : [CBUUID]?){
        self.commandState(ScanAction.adminStopScan)
        self.services = services
        self.commandState(ScanAction.adminStartScan)
    }
    
    func addClient(_ scanner: RxObserver<BleDeviceSession>){
        scanObservers.insert(scanner)
        self.commandState(ScanAction.clientStartScan)
    }
    
    func removeClient(_ scanner: RxObserver<BleDeviceSession>){
        scanObservers.remove(scanner)
        self.commandState(ScanAction.clientRemoved)
    }
    
    func stopScan(){
        self.commandState(ScanAction.adminStopScan)
    }
    
    func startScan(){
        self.commandState(ScanAction.adminStartScan)
    }
    
    func powerOn(){
        self.commandState(ScanAction.blePowerOn)
    }
    
    func powerOff(){
        self.commandState(ScanAction.blePowerOff)
    }
    
    private func commandState(_ action: ScanAction){
        BleLogger.trace("commandState state:" + state.description() + " action: " + action.description())
        switch (state){
        case .idle:
            scannerIdleState(action)
        case .stopped:
            scannerAdminState(action)
        case .scanning:
            scannerScanningState(action)
        }
    }
    
    private func changeState(_ newState: ScannerState){
        commandState(ScanAction.exit)
        state = newState
        commandState(ScanAction.entry)
    }
    
    func enableScan() {
        if !isScanning {
            commandState(.entry)
        }
    }
    
    func disableScan() {
        if isScanning {
            commandState(.clientRemoved)
        }
    }
    
    func scanningNeeded() -> Bool {
        let list = sessions.list()
        return list.first { (session: CBDeviceSessionImpl) -> Bool in
            return session.state == .sessionOpenPark
        } != nil || scanObservers.count != 0
    }
    
    private func scannerIdleState(_ action: ScanAction){
        switch action {
        case .entry where central.state == .poweredOn && scanningNeeded():
            changeState(ScannerState.scanning)
        case .clientStartScan where central.state == .poweredOn && scanningNeeded():
            changeState(ScannerState.scanning)
        case .adminStopScan:
            changeState(ScannerState.stopped)
        case .blePowerOn where scanningNeeded():
            changeState(ScannerState.scanning)
        case .adminStartScan: fallthrough
        case .clientRemoved: fallthrough
        case .blePowerOff: fallthrough
        case .clientStartScan: fallthrough
        case .blePowerOn: fallthrough
        case .entry: fallthrough
        case .exit:
            break
        }
    }
    
    private func scannerAdminState(_ action: ScanAction){
        // forced stopped state
        switch (action){
        case .entry:
            adminStops = 1
        case .exit:
            adminStops = 0
        case .adminStartScan:
            // go through idle state back to scanning, if needed
            adminStops -= 1
            if adminStops <= 0 {
                changeState(ScannerState.idle)
            } else {
                BleLogger.trace("Scanner waiting last admin to call start scan admins count: \(adminStops)")
            }
        case .adminStopScan:
            adminStops = +1
        case .blePowerOff:
            changeState(.idle)
        case .clientStartScan: fallthrough
        case .clientRemoved: fallthrough
        case .blePowerOn:
            // do nothing
            break
        }
    }
    
    private func scannerScanningState(_ action: ScanAction){
        switch (action){
        case .entry:
            BleLogger.trace("start scan services: \(String(describing: services))")
            // start scanning
            self.central.scanForPeripherals(withServices: services, options: [CBCentralManagerScanOptionAllowDuplicatesKey: true])
            scanDisposable = Observable<NSInteger>.interval(.seconds(10), scheduler: scheduler).subscribe{ e in
                switch e {
                case .completed:
                    BleLogger.trace("Scan complete")
                case .error(let error):
                    NSLog("Scanning error: \(error))")
                    BleLogger.error("Scanning error: \(error))")
                case .next( _):
                    NSLog("Scanning next:")
                    self.central.stopScan()
                    self.central.scanForPeripherals(withServices: self.services, options:   [CBCentralManagerScanOptionAllowDuplicatesKey: true])
                }
            }
        case .exit:
            // stop scanning
            if central.state == .poweredOn {
                central.stopScan()
            }
            scanDisposable?.dispose()
            scanDisposable = nil
        case .clientStartScan:
            // do nothing
            break
        case .clientRemoved where !scanningNeeded():
            changeState(.idle)
        case .adminStopScan:
            changeState(ScannerState.stopped)
        case .blePowerOff:
            changeState(ScannerState.idle)
        case .adminStartScan: fallthrough
        case .clientRemoved: fallthrough
        case .blePowerOn:
            break
        }
    }
}
