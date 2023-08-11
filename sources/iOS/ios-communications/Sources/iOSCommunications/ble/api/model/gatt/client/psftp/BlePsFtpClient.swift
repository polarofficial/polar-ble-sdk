
import Foundation
import CoreBluetooth
import RxSwift

public class BlePsFtpClient: BleGattClientBase {
    
    public static let PSFTP_SERVICE                         = CBUUID(string: "FEEE")
    public static let PSFTP_MTU_CHARACTERISTIC              = CBUUID(string: "FB005C51-02E7-F387-1CAD-8ACD2D8DF0C8")
    public static let PSFTP_D2H_NOTIFICATION_CHARACTERISTIC = CBUUID(string: "FB005C52-02E7-F387-1CAD-8ACD2D8DF0C8")
    public static let PSFTP_H2D_NOTIFICATION_CHARACTERISTIC = CBUUID(string: "FB005C53-02E7-F387-1CAD-8ACD2D8DF0C8")
    
    var mtuNotificationEnabled: AtomicInteger!
    var pftpD2HNotificationEnabled: AtomicInteger!
    public var PROTOCOL_TIMEOUT = TimeInterval(90)
    
    private let PROTOCOL_TIMEOUT_EXTENDED = TimeInterval(900)
    private let extendedWriteTimeoutFilePaths: [String] = ["/SYNCPART.TGZ"]
    
    let mtuInputQueue=AtomicList<[Data: Int]>()
    let packetsWritten=AtomicInteger(initialValue:0)
    // packet chunks define which n packet shall use write with response (i.e. ATT write request), if set to 0 then all packets in frame are written with write without response (i.e. ATT write command)
    public let packetChunks = AtomicType<Int>.init(initialValue: 6)
    
    // notification rel
    let notificationInputQueue=AtomicList<[Int : BlePsFtpUtility.BlePsFtpRfc76Frame]>()
    let notificationPacketsWritten=AtomicInteger(initialValue:0)
    
    lazy var mtuOperationQueue:OperationQueue = {
        var queue = OperationQueue()
        queue.name = "PsFtpMtuQueue" + "_obj\( NSString(format: "%p", BleGattClientBase.address(o: self)) )"
        queue.maxConcurrentOperationCount = 1
        return queue
    }()
    
    lazy var waitNotificationOperationQueue:OperationQueue = {
        var queue = OperationQueue()
        queue.name = "PsFtpWaitNotificationQueue" + "_obj\( NSString(format: "%p", BleGattClientBase.address(o: self)) )"
        queue.maxConcurrentOperationCount = 1
        return queue
    }()
    
    lazy var sendNotificationOperationQueue:OperationQueue = {
        var queue = OperationQueue()
        queue.name = "PsFtpSendNotificationQueue" + "_obj\( NSString(format: "%p", BleGattClientBase.address(o: self)) )"
        queue.maxConcurrentOperationCount = 1
        return queue
    }()
    
    let currentOperationWrite = AtomicBoolean(initialValue: false)
    
    public init(gattServiceTransmitter: BleAttributeTransportProtocol){
        super.init(serviceUuid: BlePsFtpClient.PSFTP_SERVICE, gattServiceTransmitter: gattServiceTransmitter)
        automaticEnableNotificationsOnConnect(chr: BlePsFtpClient.PSFTP_MTU_CHARACTERISTIC)
        automaticEnableNotificationsOnConnect(chr: BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC)
        addCharacteristic(BlePsFtpClient.PSFTP_H2D_NOTIFICATION_CHARACTERISTIC)
        mtuNotificationEnabled = getNotificationCharacteristicState(BlePsFtpClient.PSFTP_MTU_CHARACTERISTIC)
        pftpD2HNotificationEnabled = getNotificationCharacteristicState(BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC)
    }
    
    deinit {
        BleLogger.trace("PS-FTP deinit")
        self.disconnected()
    }
    
    // from base
    override public func disconnected() {
        super.disconnected()
        BleLogger.trace("PS-FTP reset ")
        currentOperationWrite.set(false)
        mtuOperationQueue.cancelAllOperations()
        sendNotificationOperationQueue.cancelAllOperations()
        waitNotificationOperationQueue.cancelAllOperations()
        mtuInputQueue.removeAll()
        notificationInputQueue.removeAll()
        packetsWritten.set(0)
        notificationPacketsWritten.set(0)
        mtuOperationQueue.waitUntilAllOperationsAreFinished()
        sendNotificationOperationQueue.waitUntilAllOperationsAreFinished()
        waitNotificationOperationQueue.waitUntilAllOperationsAreFinished()
    }
    
    override public func processServiceData(_ chr: CBUUID , data: Data , err: Int ){
        if( data.count != 0 ){
            if chr.isEqual(BlePsFtpClient.PSFTP_MTU_CHARACTERISTIC) {
                mtuInputQueue.push([data : err])
                if self.currentOperationWrite.get() && data.count == 3 {
                    // special case
                    self.packetsWritten.increment()
                }
            } else if chr.isEqual(BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC) {
                BleLogger.trace_hex("D2H in ", data: data)
                do {
                    let frame = try BlePsFtpUtility.processRfc76MessageFrame(data)
                    notificationInputQueue.push([err : frame])
                } catch let err {
                    BleLogger.trace_if_error("Processing Rfc76 message frame failed. Wait for next frame", error: err)
                }
            }
        } else {
            BleLogger.error("empty payload received in PS-FTP")
        }
    }
    
    override public func serviceDataWritten(_ chr: CBUUID, err: Int){
        if err == 0 {
            if chr.isEqual(BlePsFtpClient.PSFTP_MTU_CHARACTERISTIC) {
                self.packetsWritten.increment()
            } else if chr.isEqual(BlePsFtpClient.PSFTP_H2D_NOTIFICATION_CHARACTERISTIC) {
                self.notificationPacketsWritten.increment()
            }
        } else {
            // no signaling of written, timeout will happen on both ends
            BleLogger.trace("PS-FTP WRITE failed: ", String(err))
        }
    }
    
    func waitPacketsWritten(_ written: AtomicInteger, canceled: BlockOperation, count: Int, timeout: TimeInterval) throws {
        while written.get() < count {
            let was = written.get()
            try written.checkAndWait(count, secs: timeout, canceled: canceled, canceledError: BlePsFtpException.operationCanceled, timeoutCall: {
                BleLogger.trace("PS-FTP OPERATION TIMEOUT")
            })
            if (!(gattServiceTransmitter?.isConnected() ?? false) || was == written.get()) {
                // connection lost or some other error case
                throw BleGattException.gattDisconnected
            }
        }
        written.set(0)
    }
    
    func readResponse(_ outputStream: NSMutableData, inputQueue: AtomicList<[Data: Int]>, canceled: BlockOperation, timeout: TimeInterval) throws -> Int {
        var frameStatus=0
        var next=0
        let sequenceNumber = BlePsFtpUtility.BlePsFtpRfc76SequenceNumber()
        repeat{
            var packet: [Data:Int]!
            if self.gattServiceTransmitter?.isConnected() ?? false {
                do{
                    packet = try inputQueue.poll(PROTOCOL_TIMEOUT, canceled: canceled, cancelError: BlePsFtpException.operationCanceled)
                } catch let error {
                    try handleResponseError(packet,error: error)
                }
            } else {
                throw BleGattException.gattDisconnected
            }
            if packet.first!.1 == 0 {
                let response = try BlePsFtpUtility.processRfc76MessageFrame((packet.first?.0)!)
                if response.sequenceNumber != sequenceNumber.getSeq() {
                    if response.status == BlePsFtpUtility.RFC76_STATUS_MORE {
                        do {
                            try self.sendMtuCancelPacket()
                        } catch let error {
                            BleLogger.error("failed to send cancel: \(error)")
                        }
                    }
                    throw BlePsFtpException.responseError(errorCode:BlePsFtpUtility.PFTP_AIR_PACKET_LOST_ERROR)
                }
                sequenceNumber.increment()
                frameStatus = response.status
                if( next == response.next ){
                    next=1
                    switch frameStatus {
                    case BlePsFtpUtility.RFC76_STATUS_MORE: fallthrough
                    case BlePsFtpUtility.RFC76_STATUS_LAST:
                        //last
                        outputStream.append(response.payload)
                        break
                    case BlePsFtpUtility.RFC76_STATUS_ERROR_OR_RESPONSE:
                        BleLogger.trace("RFC76 message response: \(String(describing: response.error))")
                        return response.error ?? 0
                    default:
                        break
                    }
                }else{
                    throw BlePsFtpException.protocolError
                }
            }else{
                throw BlePsFtpException.responseError(errorCode: packet.first!.1)
            }
        } while frameStatus == BlePsFtpUtility.RFC76_STATUS_MORE
        BleLogger.trace("RFC76 message has read successfully")
        return 0
    }
    
    fileprivate func handleResponseError(_ packet: [Data : Int]!, error: Error) throws {
        if(mtuNotificationEnabled.get() != ATT_NOTIFY_OR_INDICATE_ON){
            throw BleGattException.gattDisconnected
        }else if packet != nil && packet.first?.1 != 0 {
            throw BlePsFtpException.responseError(errorCode: (packet.first?.1)!)
        }else{
            throw error
        }
    }
    
    public class PsFtpNotification {
        // notification ID, @see PbPFtpDevToHostNotification
        public var id: Int32 = 0
        // notification parameters if any, @see pftp_notification.proto
        public var parameters = NSMutableData()
        
        public func description() -> String {
            return "Notification with ID: \(id)"
        }
    }
    
    fileprivate func resetMtuPipe() {
        self.mtuInputQueue.removeAll()
        self.packetsWritten.set(0)
    }
    
    fileprivate func resetNotificationPipe() {
        self.notificationPacketsWritten.set(0)
    }
    
    fileprivate func transmitMtuPacket(_ packet: Data, canceled: BlockOperation, response: Bool, timeout: TimeInterval) throws {
        if !canceled.isCancelled {
            if let transport = self.gattServiceTransmitter {
                try transport.transmitMessage(self, serviceUuid: BlePsFtpClient.PSFTP_SERVICE, characteristicUuid: BlePsFtpClient.PSFTP_MTU_CHARACTERISTIC, packet: packet, withResponse: response)
                BleLogger.trace_hex("MTU send ", data: packet)
                try self.waitPacketsWritten(self.packetsWritten, canceled: canceled, count: 1, timeout: timeout)
                return
            }
            throw BleGattException.gattTransportNotAvailable
        }
        throw BlePsFtpException.operationCanceled
    }
    
    fileprivate func transmitNotificationPacket(_ packet: Data, response: Bool) throws {
        if let transport = self.gattServiceTransmitter {
            try transport.transmitMessage(self, serviceUuid: BlePsFtpClient.PSFTP_SERVICE, characteristicUuid: BlePsFtpClient.PSFTP_H2D_NOTIFICATION_CHARACTERISTIC, packet: packet, withResponse: response)
            BleLogger.trace_hex("H2D send ", data: packet)
            try self.waitPacketsWritten(self.notificationPacketsWritten, canceled: BlockOperation(), count: 1, timeout: PROTOCOL_TIMEOUT)
            return
        }
        throw BleGattException.gattTransportNotAvailable
    }
    
    // api
    
    /// isBusy
    ///
    /// - Returns: true if any mtu or notification operations are queued for execute.
    public func isBusy() -> Bool {
        return mtuOperationQueue.operationCount != 0 || sendNotificationOperationQueue.operationCount != 0
    }
    
    /// Sends a single request to device, can be called many at once,
    /// but will internally make operations atomic. Note when removing a file, client can use this function (as it is in   protocol level the same operation)
    ///
    /// - Parameters:
    ///   - header: protocol buffer encoded request PbPFtpOperation(GET, REMOVE or PUT(folder, with no data))
    /// - Returns: Single stream of NSData
    ///         Produces:  onSuccess, when file/content has been successfully read, note data can be 0 length for PUT(dir) or REMOVE operation
    ///                    onError, @see BlePsFtpException
    ///                            @see BleGattException
    public func request(_ header: Data) -> Single<NSData>{
        return Single.create{ observer in
            let block = BlockOperation()
            block.addExecutionBlock { [unowned self, weak block] in
                BleLogger.trace("PS-FTP new request operation")
                self.gattServiceTransmitter?.attributeOperationStarted()
                if !(block?.isCancelled ?? true) {
                    self.resetMtuPipe()
                    let totalStream = BlePsFtpUtility.makeCompleteMessageStream(header as Data, type: BlePsFtpUtility.MessageType.request, id: 0)
                    let sequenceNumber=BlePsFtpUtility.BlePsFtpRfc76SequenceNumber()
                    totalStream.open()
                    defer {
                        // poor mans raii
                        totalStream.close()
                    }
                    var anySend = false
                    do{
                        // send request
                        let requs = BlePsFtpUtility.buildRfc76MessageFrameAll(totalStream, mtuSize: self.mtuSize, sequenceNumber: sequenceNumber)
                        for packet in requs {
                            try self.transmitMtuPacket(packet, canceled: block ?? BlockOperation(), response: true, timeout: PROTOCOL_TIMEOUT)
                            anySend = true
                        }
                        let outputStream=NSMutableData()
                        let error = try self.readResponse(outputStream, inputQueue: self.mtuInputQueue, canceled: block ?? BlockOperation(), timeout: PROTOCOL_TIMEOUT)
                        switch ( error ){
                        case 0:
                            observer(.success(outputStream))
                            return
                        default:
                            observer(.failure(BlePsFtpException.responseError(errorCode: error)))
                            return
                        }
                    } catch let error {
                        BleLogger.error("PS-FTP request interrupted error: \(error)")
                        if !(self.gattServiceTransmitter?.isConnected() ?? false) {
                            observer(.failure(BleGattException.gattDisconnected))
                        } else {
                            if (block?.isCancelled ?? true) {
                                // send cancel streaming packet
                                do{
                                    if anySend {
                                        try self.sendMtuCancelPacket()
                                    }
                                } catch {
                                    BleLogger.error("Stream cancelation failed")
                                }
                            }
                            observer(.failure(error))
                        }
                        return
                    }
                } else {
                    observer(.failure(BlePsFtpException.operationCanceled))
                }
            }
            self.mtuOperationQueue.addOperation(block)
            return Disposables.create {
                BleLogger.trace("PS-FTP request operation DISPOSED")
                self.gattServiceTransmitter?.attributeOperationFinished()
                block.cancel()
            }
        }
    }
    
    /// Write a file to device, can be called many at once,
    /// but will internally make operations atomic
    ///
    /// - Parameters:
    ///   - header: protocol buffer encoded header PbPFtpOperation(put or merge with data)
    ///   - data: file data to be written on device
    /// - Returns: Observable stream of progress
    ///         Produces:  onNext, after every successfully transmitted payload air packet, given current offset of data written
    ///                    onCompleted, after successfully received response from write request
    ///                    onError, @see BlePsFtpException
    ///                             @see BleGattException
    public func write(_ header: NSData, data: InputStream) -> Observable<UInt> {
        return Observable.create{ observer in
            // TODO make improvement, if ex. rx retry is used this create could be called n times,
            // now the InputStream on n=1... is already consumed
            var timeout = self.PROTOCOL_TIMEOUT
            do {
                timeout = try self.writeTimeout(for: Communications_PbPFtpOperation(serializedData: Data(referencing: header)).path)
            } catch {
                BleLogger.error("Failed to parse PbPFtpOperation: \(error)")
            }
            let block = BlockOperation()

            block.addExecutionBlock { [unowned self, weak block] in
                BleLogger.trace("PS-FTP new write operation")
                self.gattServiceTransmitter?.attributeOperationStarted()
                if !(block?.isCancelled ?? true) {
                    self.currentOperationWrite.set(true)
                    self.resetMtuPipe()
                    let headerSize=Int64(header.length)
                    let totalStream = BlePsFtpUtility.makeCompleteMessageStream(header as Data, type: BlePsFtpUtility.MessageType.request, id: 0)
                    totalStream.open()
                    data.open()
                    defer {
                        totalStream.close()
                        data.close()
                    }
                    var next=0
                    var pCounter: UInt64 = 0
                    var response = false
                    let sequenceNumber = BlePsFtpUtility.BlePsFtpRfc76SequenceNumber()
                    var totalTransmitted: Int64 = 0
                    var more = true
                    repeat{
                        do{
                            let packet = BlePsFtpUtility.buildRfc76MessageFrame(totalStream, data: data, next: next, mtuSize: self.mtuSize, sequenceNumber: sequenceNumber)
                            pCounter += 1
                            more = (packet[0] & 0x06) == 0x06
                            if( !more ) {
                                self.currentOperationWrite.set(false)
                            }
                            if self.packetChunks.get() == 0 {
                                response = false
                            } else {
                                response = (pCounter % UInt64(self.packetChunks.get())) == 0
                            }
                            
                            if next == 0 {
                                // first write cannot be canceled
                                try self.transmitMtuPacket(packet, canceled: BlockOperation(), response: response, timeout: timeout)
                            } else {
                                try self.transmitMtuPacket(packet, canceled: block ?? BlockOperation(), response: response, timeout: timeout)
                            }
                            
                            next = 1
                            totalTransmitted += Int64(packet.count - 1)
                            var transferred: Int64 = 0
                            let component = totalTransmitted - headerSize - Int64(2)
                            if (component > 0) {
                                transferred = (totalTransmitted - headerSize - 2)
                            }
                            if( more ){
                                // check input queue
                                do{
                                    let cancelPacket = try self.mtuInputQueue.poll()
                                    BleLogger.trace("Frame sending interrupted by device!")
                                    let response = try BlePsFtpUtility.processRfc76MessageFrame((cancelPacket.first?.0)!)
                                    if (response.status == BlePsFtpUtility.RFC76_STATUS_ERROR_OR_RESPONSE),
                                       let errorCode = response.error {
                                        observer.onError(BlePsFtpException.responseError(errorCode: errorCode))
                                    } else {
                                        observer.onError(BlePsFtpException.protocolError)
                                    }
                                    return
                                } catch {
                                    // ignore
                                }
                            }
                            observer.onNext(UInt(transferred))
                        } catch let error {
                            BleLogger.error("PS-FTP write interrupted error: \(error)")
                            if !(self.gattServiceTransmitter?.isConnected() ?? false) {
                                observer.onError(BleGattException.gattDisconnected)
                            } else {
                                if (block?.isCancelled ?? true)  {
                                    if (error is AtomicIntegerException) && (error as! AtomicIntegerException) == .waitTimeout{
                                        // skip if previous packet timeout, cancel cannot be send
                                        BleLogger.error("PS-FTP no cancel send no packets written")
                                    } else {
                                        do{
                                            try self.sendMtuCancelPacket()
                                        } catch {
                                            BleLogger.error("Stream cancelation failed")
                                        }
                                    }
                                }
                                observer.onError(error)
                            }
                            return
                        }
                    } while more
                    let output = NSMutableData()
                    do {
                        let error = try self.readResponse(output, inputQueue: self.mtuInputQueue, canceled: block ?? BlockOperation(), timeout: timeout)
                        
                        switch ( error ) {
                        case 0:
                            observer.onCompleted()
                            return
                        default:
                            observer.onError(BlePsFtpException.responseError(errorCode: error))
                            return
                        }
                    } catch let error {
                        observer.onError(error)
                        return
                    }
                } else {
                    observer.onError(BlePsFtpException.operationCanceled)
                }
            }
            self.mtuOperationQueue.addOperation(block)
            return Disposables.create {
                BleLogger.trace("PS-FTP write operation DISPOSED")
                self.currentOperationWrite.set(false)
                self.gattServiceTransmitter?.attributeOperationFinished()
                block.cancel()
            }
        }
    }
    
    private func writeTimeout(for filePath: String) -> TimeInterval {
        for path in extendedWriteTimeoutFilePaths {
            if filePath.hasPrefix(path) {
                return PROTOCOL_TIMEOUT_EXTENDED
            }
        }
        return PROTOCOL_TIMEOUT
    }
    
    fileprivate func sendMtuCancelPacket() throws {
        let cancelPacket = [UInt8](repeating: 0, count: 3)
        packetsWritten.set(0)
        BleLogger.trace("PS-FTP mtu send cancel packet")
        try self.transmitMtuPacket(Data(cancelPacket), canceled: BlockOperation(), response: true, timeout: PROTOCOL_TIMEOUT)
    }
    
    /// Sends a single query to device, can be called many at once, but will internally make operations atomic
    ///
    /// - Parameters:
    ///   - id: one of the Protocol_PbPFtpQuery value
    ///   - parameters: optional parameters field, can be nil
    /// - Returns: Observable stream
    ///                      Produces:  onSuccess, only once when query has been received successfully response Note data might be 0  length
    ///                    onError, @see BlePsFtpException
    ///                             @see BleGattException
    public func query(_ id: Int, parameters: NSData?) -> Single<NSData>{
        return Single.create{ observer in
            let block = BlockOperation()
            block.addExecutionBlock { [unowned self, weak block] in
                BleLogger.trace("PS-FTP new query operation")
                if !(block?.isCancelled ?? true) {
                    self.mtuInputQueue.removeAll()
                    let totalStream = BlePsFtpUtility.makeCompleteMessageStream(parameters as Data?, type: BlePsFtpUtility.MessageType.query, id: id)
                    totalStream.open()
                    defer {
                        // poor mans raii
                        totalStream.close()
                    }
                    // send request
                    do {
                        let sequenceNumber=BlePsFtpUtility.BlePsFtpRfc76SequenceNumber()
                        let requs = BlePsFtpUtility.buildRfc76MessageFrameAll(totalStream, mtuSize: self.mtuSize, sequenceNumber: sequenceNumber)
                        for packet in requs {
                            try self.transmitMtuPacket(packet, canceled: BlockOperation.init(), response: true, timeout: PROTOCOL_TIMEOUT)
                        }
                        let outputStream=NSMutableData()
                        let error = try self.readResponse(outputStream, inputQueue: self.mtuInputQueue, canceled: block ?? BlockOperation(), timeout: PROTOCOL_TIMEOUT)
                        switch ( error ){
                        case 0:
                            observer(.success(outputStream))
                            return
                        default:
                            observer(.failure(BlePsFtpException.responseError(errorCode: error)))
                            return
                        }
                    } catch let error {
                        BleLogger.error("PS-FTP query interrupted error: \(error)")
                        if !(self.gattServiceTransmitter?.isConnected() ?? false) {
                            observer(.failure(BleGattException.gattDisconnected))
                        } else {
                            if (block?.isCancelled ?? false) {
                                if (error is AtomicIntegerException) && (error as! AtomicIntegerException) == .waitTimeout{
                                    // skip no packets written
                                    BleLogger.error("PS-FTP no cancel send no packets written")
                                } else {
                                    do{
                                        try self.sendMtuCancelPacket()
                                    } catch {
                                        BleLogger.error("Stream cancelation failed")
                                    }
                                }
                            }
                            observer(.failure(error))
                        }
                        return
                    }
                } else {
                    observer(.failure(BlePsFtpException.operationCanceled))
                }
            }
            self.mtuOperationQueue.addOperation(block)
            return Disposables.create {
                BleLogger.trace("PS-FTP query operation DISPOSED")
                block.cancel()
                // does signal wait if it is waiting, and will send cancel packet
                self.mtuInputQueue.removeAll()
            }
        }
    }
    
    /// Sends a single notification to device, can be called many at once, but will internally make operations atomic
    ///
    /// - Parameters:
    ///   - id: one of the PbPFtpHostToDevNotification values
    ///   - parameters: optional parameters field, can be nil
    /// - Returns: Observable stream
    ///         Produces:  onSuccess, notification has been send
    ///                 onError, @see BlePsFtpException
    ///                       @see BleGattException
    public func sendNotification(_ id: Int, parameters: NSData?) -> Completable {
        return Completable.create{ observer in
            let block = BlockOperation()
            block.addExecutionBlock { [unowned self, weak block] in
                BleLogger.trace("PS-FTP new notification")
                if !(block?.isCancelled ?? true) {
                    let totalStream = BlePsFtpUtility.makeCompleteMessageStream(parameters as Data?, type: BlePsFtpUtility.MessageType.notification, id: id)
                    totalStream.open()
                    defer {
                        // poor mans raii
                        totalStream.close()
                    }
                    self.notificationPacketsWritten.set(0)
                    // send notification
                    do{
                        self.notificationPacketsWritten.set(0)
                        let sequenceNumber=BlePsFtpUtility.BlePsFtpRfc76SequenceNumber()
                        let requs = BlePsFtpUtility.buildRfc76MessageFrameAll(totalStream, mtuSize: self.mtuSize, sequenceNumber: sequenceNumber)
                        for packet in requs {
                            // NOTE no support for notification send cancelation, as typically notification fit into a single air packet
                            try self.transmitNotificationPacket(packet, response: true)
                        }
                        observer(.completed)
                    } catch let error {
                        BleLogger.error("PS-FTP notifcation send interrupted error: \(error)")
                        observer(.error(error))
                    }
                } else {
                    observer(.error(BlePsFtpException.operationCanceled))
                }
            }
            self.sendNotificationOperationQueue.addOperation(block)
            return Disposables.create {
                BleLogger.trace("PS-FTP Notification send DISPOSED")
                block.cancel()
            }
        }
    }
    
    /// waits for device notifications endlessly, only dispose , take(1) etc ... stops notifications waiting
    ///
    /// - Returns: Observable stream of PsFtpNotification
    ///        Produces:  onNext, after successfully received notification from device
    ///                    onCompleted, non produced unless stream is further configured
    ///                    onError, @see BlePsFtpException
    ///                             @see BleGattException
    public func waitNotification() -> Observable<PsFtpNotification> {
        return Observable.create{ observer in
            // allow only single wait notification observer at time
            let block = BlockOperation()
            block.addExecutionBlock({ [unowned self, weak block] in
                if !(block?.isCancelled ?? true)  {
                    // if self.pftpD2HNotificationEnabled!.get() {
                    // NOTE no pipe clear, because host may want to purge all existing notifications
                    // self.notificationInputQueue.removeAll()
                    do {
                        repeat {
                            let packet = try self.notificationInputQueue.pollUntilSignaled(canceled: block ?? BlockOperation(),cancelError: BlePsFtpException.operationCanceled)
                            
                            // Check packet has no error
                            if packet.first?.key == 0, var frame = packet.first?.value {
                                if (frame.next == 0 && frame.status != BlePsFtpUtility.RFC76_STATUS_ERROR_OR_RESPONSE ) {
                                    
                                    let notification = PsFtpNotification()
                                    notification.id = Int32(frame.payload[0])
                                    notification.parameters.append(frame.payload.subdata(in: 1..<frame.payload.count))
                                    
                                    while frame.status == BlePsFtpUtility.RFC76_STATUS_MORE {
                                        let packet = try self.notificationInputQueue.poll(self.PROTOCOL_TIMEOUT)
                                        if packet.first?.key == 0, let newFrame = packet.first?.value {
                                            frame = newFrame
                                            if (frame.status != BlePsFtpUtility.RFC76_STATUS_ERROR_OR_RESPONSE) {
                                                notification.parameters.append(frame.payload.subdata(in: 0..<frame.payload.count))
                                            }
                                        } else {
                                            BleLogger.error("Unexpected error while receiving notifcations. Received packet error: \(String(describing: packet.first?.key)), Received packet content: \(String(describing: packet.first?.value))")
                                            observer.onError(BlePsFtpException.responseError(errorCode: (packet.first?.key) ?? -1))
                                            return
                                        }
                                    }
                                    if( frame.status == BlePsFtpUtility.RFC76_STATUS_LAST) {
                                        observer.onNext(notification)
                                    } else {
                                        BleLogger.error("Notification stream was interrupted. Frame next: \(frame.next), Frame status: \(frame.status), Frame error: \(String(describing: frame.error)). Wait for next packet")
                                    }
                                } else {
                                    BleLogger.error("Wait notification in unexpected state. Frame next: \(frame.next), Frame status: \(frame.status), Frame error: \(String(describing: frame.error)). Wait for next packet")
                                }
                            } else {
                                BleLogger.error("Unexpected error while receiving notifcations. Received packet error: \(String(describing: packet.first?.key)), Received packet content: \(String(describing: packet.first?.value))")
                                
                                observer.onError(BlePsFtpException.responseError(errorCode: (packet.first?.key) ?? -1))
                                return
                            }
                        } while true
                    } catch let error {
                        observer.onError(error)
                    }
                } else {
                    observer.onError(BlePsFtpException.operationCanceled)
                }
            })
            self.waitNotificationOperationQueue.addOperation(block)
            return Disposables.create {
                // do nothing
                BleLogger.trace("PS-FTP Notification wait disposed")
                block.cancel()
                self.notificationInputQueue.removeAll()
            }
        }
    }
    
    public func waitPsFtpReady(_ checkConnection: Bool) -> Completable {
        return waitNotificationEnabled(BlePsFtpClient.PSFTP_MTU_CHARACTERISTIC, checkConnection: checkConnection).concat(
            waitNotificationEnabled(BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC, checkConnection: checkConnection))
    }
    
    public override func clientReady(_ checkConnection: Bool) -> Completable {
        return waitPsFtpReady(checkConnection)
    }
}
