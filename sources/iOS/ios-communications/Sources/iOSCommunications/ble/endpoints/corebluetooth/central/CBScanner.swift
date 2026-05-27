import Foundation
import CoreBluetooth
import Combine

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
    let scanSubject = PassthroughSubject<BleDeviceSession, Never>()
    private var clientCount = 0
    var scanCancellable: AnyCancellable?
    var services: [CBUUID]?
    var adminStops = 0
    let sessions: AtomicList<CBDeviceSessionImpl>
    let queue: DispatchQueue
    var isScanning: Bool {
        return state == .scanning
    }

    init(_ central: CBCentralManager, queue: DispatchQueue, sessions: AtomicList<CBDeviceSessionImpl>) {
        self.central = central
        self.sessions = sessions
        self.queue = queue
    }

    func setServices(_ services: [CBUUID]?) {
        queue.async {
            self.commandState(ScanAction.adminStopScan)
            self.services = services
            self.commandState(ScanAction.adminStartScan)
        }
    }

    func addClient() {
        queue.async {
            self.clientCount += 1
            self.commandState(ScanAction.clientStartScan)
        }
    }

    func removeClient() {
        queue.async {
            self.clientCount -= 1
            self.commandState(ScanAction.clientRemoved)
        }
    }

    func stopScan() {
        queue.async { self.commandState(ScanAction.adminStopScan) }
    }

    func startScan() {
        queue.async { self.commandState(ScanAction.adminStartScan) }
    }

    func powerOn() {
        queue.async { self.commandState(ScanAction.blePowerOn) }
    }

    func powerOff() {
        queue.async { self.commandState(ScanAction.blePowerOff) }
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
        } != nil || clientCount != 0
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
    
    private func scannerScanningState(_ action: ScanAction) {
        switch action {
        case .entry:
            BleLogger.trace("start scan services: \(String(describing: services))")
            central.scanForPeripherals(withServices: services, options: [CBCentralManagerScanOptionAllowDuplicatesKey: true])
            scanCancellable = Timer.publish(every: 10, on: .main, in: .default)
                .autoconnect()
                .receive(on: queue)
                .sink { [weak self] _ in
                    guard let self else { return }
                    BleLogger.trace("Scanning next:")
                    self.central.stopScan()
                    self.central.scanForPeripherals(withServices: self.services, options: [CBCentralManagerScanOptionAllowDuplicatesKey: true])
                }
        case .exit:
            if central.state == .poweredOn {
                central.stopScan()
            }
            scanCancellable?.cancel()
            scanCancellable = nil
        case .clientStartScan:
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
