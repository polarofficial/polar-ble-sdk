import Foundation
import CoreBluetooth
import Combine


public protocol BlePsFtpProgressCallback: AnyObject {
    func onProgressUpdate(bytesReceived: Int)
}

open class BlePsFtpClient: BleGattClientBase, @unchecked Sendable {
    
    public static let PSFTP_SERVICE                         = CBUUID(string: "FEEE")
    public static let PSFTP_MTU_CHARACTERISTIC              = CBUUID(string: "FB005C51-02E7-F387-1CAD-8ACD2D8DF0C8")
    public static let PSFTP_D2H_NOTIFICATION_CHARACTERISTIC = CBUUID(string: "FB005C52-02E7-F387-1CAD-8ACD2D8DF0C8")
    public static let PSFTP_H2D_NOTIFICATION_CHARACTERISTIC = CBUUID(string: "FB005C53-02E7-F387-1CAD-8ACD2D8DF0C8")
    
    var mtuNotificationEnabled: AtomicInteger!
    var pftpD2HNotificationEnabled: AtomicInteger!
    public var PROTOCOL_TIMEOUT = TimeInterval(90)
    
    private let PROTOCOL_TIMEOUT_EXTENDED = TimeInterval(900)
    private let extendedWriteTimeoutFilePaths: [String] = ["/SYNCPART.TGZ"]
    
    internal var progressCallback: BlePsFtpProgressCallback?

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
        BleLogger.trace("PS-FTP init")
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
        BleLogger.trace("processServiceData called with characteristic: \(chr), error: \(err), data size: \(data.count) bytes")
        if( data.count != 0 ){
            if chr.isEqual(BlePsFtpClient.PSFTP_MTU_CHARACTERISTIC) {
                BleLogger.trace_hex("Processing PSFTP_MTU_CHARACTERISTIC ", data: data)
                mtuInputQueue.push([data : err])
                if self.currentOperationWrite.get() && data.count == 3 {
                    // special case
                    self.packetsWritten.increment()
                }

                if let callback = progressCallback, data.count > 0 {
                    callback.onProgressUpdate(bytesReceived: data.count)
                }
                
            } else if chr.isEqual(BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC) {
                BleLogger.trace_hex("Processing PSFTP_D2H_NOTIFICATION_CHARACTERISTIC, data: ", data: data)
                do {
                    let frame = try BlePsFtpUtility.processRfc76MessageFrame(data)
                    notificationInputQueue.push([err : frame])
                } catch let err {
                    BleLogger.trace_if_error("Processing Rfc76 message frame failed. Wait for next frame", error: err)
                }
            }
        } else {
            BleLogger.error("Empty payload received in PS-FTP, characteristic: \(chr)")
        }
    }
    
    override public func serviceDataWritten(_ chr: CBUUID, err: Int){
        BleLogger.trace("serviceDataWritten called with characteristic: \(chr), error: \(err)")
        if err == 0 {
            if chr.isEqual(BlePsFtpClient.PSFTP_MTU_CHARACTERISTIC) {
                BleLogger.trace("Incrementing packetsWritten for PSFTP_MTU_CHARACTERISTIC")
                self.packetsWritten.increment()
            } else if chr.isEqual(BlePsFtpClient.PSFTP_H2D_NOTIFICATION_CHARACTERISTIC) {
                BleLogger.trace("Incrementing notificationPacketsWritten for PSFTP_H2D_NOTIFICATION_CHARACTERISTIC")
                self.notificationPacketsWritten.increment()
            }
        } else {
            // no signaling of written, timeout will happen on both ends
            BleLogger.trace("PS-FTP WRITE failed: ", String(err))
        }
    }
    
    func waitPacketsWritten(_ written: AtomicInteger, canceled: BlockOperation, count: Int, timeout: TimeInterval) throws {
        BleLogger.trace("waitPacketsWritten started with count: \(count), timeout: \(timeout) seconds")
        while written.get() < count {
            BleLogger.trace("Current written: \(written.get()), Target count: \(count)")
            try written.checkAndWait(count, secs: timeout, canceled: canceled, canceledError: BlePsFtpException.operationCanceled, timeoutCall: {
                BleLogger.trace("PS-FTP operation timeout occurred after \(timeout) seconds")
            })
            if !(gattServiceTransmitter?.isConnected() ?? false) {
                BleLogger.error("Connection lost during packet write operation")
                throw BleGattException.gattDisconnected
            }
        }
        BleLogger.trace("All packets written successfully, resetting written count")
        written.set(0)
    }
    
    func readResponse(_ outputStream: NSMutableData, inputQueue: AtomicList<[Data: Int]>, canceled: BlockOperation, timeout: TimeInterval) throws -> Int {
        BleLogger.trace("readResponse started with timeout: \(timeout) seconds")
        var frameStatus=0
        var next=0
        let sequenceNumber = BlePsFtpUtility.BlePsFtpRfc76SequenceNumber()
        repeat{
            var packet: [Data:Int]!
            if self.gattServiceTransmitter?.isConnected() ?? false {
                BleLogger.trace("Device is connected, attempting to poll input queue")
                do{
                    packet = try inputQueue.poll(PROTOCOL_TIMEOUT, canceled: canceled, cancelError: BlePsFtpException.operationCanceled)
                    BleLogger.trace("Polled packet with size: \(packet.first?.0.count ?? 0) bytes, error code: \(packet.first?.1 ?? -1)")
                } catch let error {
                    BleLogger.error("Polling input queue failed: \(error.localizedDescription)")
                    try handleResponseError(packet,error: error)
                }
            } else {
                BleLogger.error("Device disconnected during readResponse")
                throw BleGattException.gattDisconnected
            }
            if packet.first!.1 == 0 {
                let response = try BlePsFtpUtility.processRfc76MessageFrame((packet.first?.0)!)
                BleLogger.trace("Processed RFC76 message frame, sequence number: \(response.sequenceNumber), status: \(response.status)")
                if response.sequenceNumber != sequenceNumber.getSeq() {
                    BleLogger.error("Sequence number mismatch: expected \(sequenceNumber.getSeq()), got \(response.sequenceNumber)")
                    if response.status == BlePsFtpUtility.RFC76_STATUS_MORE {
                        do {
                            try self.sendMtuCancelPacket()
                            BleLogger.trace("MTU cancel packet sent successfully")
                        } catch let error {
                            BleLogger.error("Failed to send MTU cancel packet: \(error)")
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
                        BleLogger.trace("Appending payload to output stream, payload size: \(response.payload.count) bytes")
                        outputStream.append(response.payload)
                        break
                    case BlePsFtpUtility.RFC76_STATUS_ERROR_OR_RESPONSE:
                        BleLogger.trace("RFC76 message response received with error code: \(String(describing: response.error))")
                        return response.error ?? 0
                    default:
                        BleLogger.error("Unknown frame status: \(frameStatus)")
                        break
                    }
                }else{
                    BleLogger.error("Next frame mismatch: expected \(next), got \(response.next)")
                    throw BlePsFtpException.protocolError
                }
            }else{
                logPsFtpError(
                    "PS-FTP response error",
                    BlePsFtpException.responseError(errorCode: packet.first!.1)
                )

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
                try DispatchQueue.global(qos: .userInitiated).sync {
                    try transport.transmitMessage(self, serviceUuid: BlePsFtpClient.PSFTP_SERVICE, characteristicUuid: BlePsFtpClient.PSFTP_MTU_CHARACTERISTIC, packet: packet, withResponse: response)
                    BleLogger.trace_hex("MTU send ", data: packet)
                    try self.waitPacketsWritten(self.packetsWritten, canceled: canceled, count: 1, timeout: timeout)
                }
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
    
    /// Sends a single request to device. Suspends until a full response is received.
    /// - Parameter header: protocol buffer encoded request PbPFtpOperation
    /// - Returns: response data
    open func request(_ header: Data) async throws -> NSData {
        return try await request(header, progressCallback: nil)
    }

    func request(_ header: Data, progressCallback: BlePsFtpProgressCallback?) async throws -> NSData {
        var shouldClearProgressCallback = self.progressCallback != nil
        return try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<NSData, Error>) in
            let block = BlockOperation()
            block.addExecutionBlock { [unowned self, weak block] in
                BleLogger.trace("PS-FTP new request operation")
                self.gattServiceTransmitter?.attributeOperationStarted()
                defer { self.gattServiceTransmitter?.attributeOperationFinished() }
                if !(block?.isCancelled ?? true) {
                    self.resetMtuPipe()
                    let totalStream = BlePsFtpUtility.makeCompleteMessageStream(header as Data, type: BlePsFtpUtility.MessageType.request, id: 0)
                    let sequenceNumber = BlePsFtpUtility.BlePsFtpRfc76SequenceNumber()
                    totalStream.open()
                    defer { totalStream.close() }
                    var anySend = false
                    do {
                        let requs = BlePsFtpUtility.buildRfc76MessageFrameAll(totalStream, mtuSize: self.mtuSize, sequenceNumber: sequenceNumber)
                        for packet in requs {
                            try self.transmitMtuPacket(packet, canceled: block ?? BlockOperation(), response: true, timeout: self.PROTOCOL_TIMEOUT)
                            anySend = true
                        }
                        let outputStream = NSMutableData()
                        let error = try self.readResponse(outputStream, inputQueue: self.mtuInputQueue, canceled: block ?? BlockOperation(), timeout: self.PROTOCOL_TIMEOUT)
                        if shouldClearProgressCallback { self.progressCallback = nil }
                        switch error {
                        case 0:
                            continuation.resume(returning: outputStream)
                        default:
                            continuation.resume(throwing: BlePsFtpException.responseError(errorCode: error))
                        }
                    } catch let error {
                        self.logPsFtpError("PS-FTP request interrupted", error)
                        if shouldClearProgressCallback { self.progressCallback = nil }
                        if !(self.gattServiceTransmitter?.isConnected() ?? false) {
                            continuation.resume(throwing: BleGattException.gattDisconnected)
                        } else {
                            if block?.isCancelled ?? true {
                                do { if anySend { try self.sendMtuCancelPacket() } } catch { BleLogger.error("Stream cancelation failed") }
                            }
                            continuation.resume(throwing: error)
                        }
                    }
                } else {
                    if shouldClearProgressCallback { self.progressCallback = nil }
                    continuation.resume(throwing: BlePsFtpException.operationCanceled)
                }
            }
            self.mtuOperationQueue.addOperation(block)
        }
    }
    
    /// Write a file to device. Yields progress updates and completes when write is acknowledged.
    /// - Parameters:
    ///   - header: protocol buffer encoded header PbPFtpOperation
    ///   - data: file data to be written on device
    /// - Returns: AsyncThrowingStream emitting progress (bytes transferred) updates
    open func write(_ header: NSData, data: InputStream) -> AsyncThrowingStream<UInt, Error> {
        var timeout = self.PROTOCOL_TIMEOUT
        do {
            timeout = try self.writeTimeout(for: Communications_PbPFtpOperation(serializedBytes: Data(referencing: header)).path)
        } catch {
            BleLogger.error("Failed to parse PbPFtpOperation: \(error)")
        }
        data.open()
        var dataBytes = Data()
        let bufferSize = 4096
        var buffer = [UInt8](repeating: 0, count: bufferSize)
        while data.hasBytesAvailable {
            let bytesRead = data.read(&buffer, maxLength: bufferSize)
            if bytesRead > 0 { dataBytes.append(buffer, count: bytesRead) }
        }
        data.close()

        return AsyncThrowingStream<UInt, Error> { cont in
            let block = BlockOperation()
            block.addExecutionBlock { [unowned self, weak block] in
                BleLogger.trace("PS-FTP new write operation")
                self.gattServiceTransmitter?.attributeOperationStarted()
                defer {
                    self.currentOperationWrite.set(false)
                    self.gattServiceTransmitter?.attributeOperationFinished()
                }
                if !(block?.isCancelled ?? true) {
                    self.currentOperationWrite.set(true)
                    self.resetMtuPipe()
                    let headerSize = Int64(header.length)
                    let totalStream = BlePsFtpUtility.makeCompleteMessageStream(header as Data, type: BlePsFtpUtility.MessageType.request, id: 0)
                    let localDataStream = InputStream(data: dataBytes)
                    totalStream.open(); localDataStream.open()
                    defer { totalStream.close(); localDataStream.close() }
                    var next = 0
                    var pCounter: UInt64 = 0
                    var response = false
                    let sequenceNumber = BlePsFtpUtility.BlePsFtpRfc76SequenceNumber()
                    var totalTransmitted: Int64 = 0
                    var more = true
                    repeat {
                        do {
                            let packet = BlePsFtpUtility.buildRfc76MessageFrame(totalStream, data: localDataStream, next: next, mtuSize: self.mtuSize, sequenceNumber: sequenceNumber)
                            pCounter += 1
                            more = (packet[0] & 0x06) == 0x06
                            if !more { self.currentOperationWrite.set(false) }
                            if self.packetChunks.get() == 0 {
                                response = false
                            } else {
                                response = (pCounter % UInt64(self.packetChunks.get())) == 0
                            }
                            if next == 0 {
                                try self.transmitMtuPacket(packet, canceled: BlockOperation(), response: response, timeout: timeout)
                                BleLogger.trace("Transmitted first MTU packet with size: \(packet.count) bytes")
                            } else {
                                try self.transmitMtuPacket(packet, canceled: block ?? BlockOperation(), response: response, timeout: timeout)
                                BleLogger.trace("Transmitted MTU packet with size: \(packet.count) bytes")
                            }
                            next = 1
                            totalTransmitted += Int64(packet.count - 1)
                            var transferred: Int64 = 0
                            let component = totalTransmitted - headerSize - Int64(2)
                            if component > 0 { transferred = totalTransmitted - headerSize - 2 }
                            if more {
                                do {
                                    let cancelPacket = try self.mtuInputQueue.poll()
                                    BleLogger.trace("Frame sending interrupted by device!")
                                    let resp = try BlePsFtpUtility.processRfc76MessageFrame((cancelPacket.first?.0)!)
                                    if resp.status == BlePsFtpUtility.RFC76_STATUS_ERROR_OR_RESPONSE, let errorCode = resp.error {
                                        cont.finish(throwing: BlePsFtpException.responseError(errorCode: errorCode))
                                    } else {
                                        cont.finish(throwing: BlePsFtpException.protocolError)
                                    }
                                    return
                                } catch {
                                    // emptyQueueSignal is expected here — means device has not interrupted the transfer
                                    BleLogger.trace("No interruption from device: \(error)")
                                }
                            }
                            cont.yield(UInt(transferred))
                        } catch let error {
                            self.logPsFtpError("PS-FTP write interrupted", error)
                            if !(self.gattServiceTransmitter?.isConnected() ?? false) {
                                cont.finish(throwing: BleGattException.gattDisconnected)
                            } else {
                                if block?.isCancelled ?? true {
                                    if (error is AtomicIntegerException) && (error as! AtomicIntegerException) == .waitTimeout {
                                        BleLogger.error("PS-FTP no cancel send no packets written")
                                    } else {
                                        do { try self.sendMtuCancelPacket() } catch { BleLogger.error("Stream cancelation failed") }
                                    }
                                }
                                cont.finish(throwing: error)
                            }
                            return
                        }
                    } while more
                    let output = NSMutableData()
                    do {
                        let error = try self.readResponse(output, inputQueue: self.mtuInputQueue, canceled: block ?? BlockOperation(), timeout: timeout)
                        switch error {
                        case 0:  cont.finish()
                        default: cont.finish(throwing: BlePsFtpException.responseError(errorCode: error))
                        }
                    } catch let error {
                        cont.finish(throwing: error)
                    }
                } else {
                    cont.finish(throwing: BlePsFtpException.operationCanceled)
                }
            }
            self.mtuOperationQueue.addOperation(block)
        }
    }
    
    private func writeTimeout(for filePath: String) -> TimeInterval {
        for path in extendedWriteTimeoutFilePaths {
            if filePath.hasPrefix(path) { return PROTOCOL_TIMEOUT_EXTENDED }
        }
        return PROTOCOL_TIMEOUT
    }
    
    fileprivate func sendMtuCancelPacket() throws {
        let cancelPacket = [UInt8](repeating: 0, count: 3)
        packetsWritten.set(0)
        BleLogger.trace("PS-FTP mtu send cancel packet")
        try self.transmitMtuPacket(Data(cancelPacket), canceled: BlockOperation(), response: true, timeout: PROTOCOL_TIMEOUT)
    }
    
    /// Sends a single query to device.
    /// - Parameters:
    ///   - id: one of the Protocol_PbPFtpQuery value
    ///   - parameters: optional parameters field, can be nil
    /// - Returns: response data
    open func query(_ id: Int, parameters: NSData?) async throws -> NSData {
        let parametersData: Data? = parameters.map { Data(referencing: $0) }
        return try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<NSData, Error>) in
            let block = BlockOperation()
            block.addExecutionBlock { [unowned self, weak block] in
                BleLogger.trace("PS-FTP new query operation started for ID: \(id)")
                defer { self.mtuInputQueue.removeAll() }
                if !(block?.isCancelled ?? true) {
                    self.mtuInputQueue.removeAll()
                    let totalStream = BlePsFtpUtility.makeCompleteMessageStream(parametersData, type: BlePsFtpUtility.MessageType.query, id: id)
                    totalStream.open()
                    defer { totalStream.close() }
                    do {
                        let sequenceNumber = BlePsFtpUtility.BlePsFtpRfc76SequenceNumber()
                        let requs = BlePsFtpUtility.buildRfc76MessageFrameAll(totalStream, mtuSize: self.mtuSize, sequenceNumber: sequenceNumber)
                        for packet in requs {
                            /* Passing a new BlockOperation() to transmitMtuPacket since query packets should not be canceled by the enclosing block's
                             cancellation, as the device is expected to respond to all query packets even if the operation is canceled midway. Cancellation
                             of the query operation will be handled by canceling the response waiting, and optionally sending a cancel packet if the error
                             is not a timeout. */
                            try self.transmitMtuPacket(packet, canceled: BlockOperation(), response: true, timeout: self.PROTOCOL_TIMEOUT)
                        }
                        let outputStream = NSMutableData()
                        let error = try self.readResponse(outputStream, inputQueue: self.mtuInputQueue, canceled: block ?? BlockOperation(), timeout: self.PROTOCOL_TIMEOUT)
                        switch error {
                        case 0:  continuation.resume(returning: outputStream)
                        default: continuation.resume(throwing: BlePsFtpException.responseError(errorCode: error))
                        }
                    } catch let error {
                        self.logPsFtpError("PS-FTP query failed for ID=\(id)", error)
                        if !(self.gattServiceTransmitter?.isConnected() ?? false) {
                            continuation.resume(throwing: BleGattException.gattDisconnected)
                        } else {
                            if block?.isCancelled ?? false {
                                if (error is AtomicIntegerException) && (error as! AtomicIntegerException) == .waitTimeout {
                                    BleLogger.error("PS-FTP query timed out, no cancel packet sent for ID: \(id)")
                                } else {
                                    do { try self.sendMtuCancelPacket() } catch { BleLogger.error("Stream cancellation failed") }
                                }
                            }
                            continuation.resume(throwing: error)
                        }
                    }
                } else {
                    continuation.resume(throwing: BlePsFtpException.operationCanceled)
                }
            }
            self.mtuOperationQueue.addOperation(block)
        }
    }
    
    /// Sends a single notification to device.
    /// - Parameters:
    ///   - id: one of the PbPFtpHostToDevNotification values
    ///   - parameters: optional parameters field
    open func sendNotification(_ id: Int, parameters: NSData?) async throws {
        let parametersData: Data? = parameters.map { Data(referencing: $0) }
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            let block = BlockOperation()
            block.addExecutionBlock { [unowned self, weak block] in
                BleLogger.trace("PS-FTP new notification operation started for ID: \(id)")
                if !(block?.isCancelled ?? true) {
                    let totalStream = BlePsFtpUtility.makeCompleteMessageStream(parametersData, type: BlePsFtpUtility.MessageType.notification, id: id)
                    totalStream.open()
                    defer { totalStream.close() }
                    self.notificationPacketsWritten.set(0)
                    do {
                        self.notificationPacketsWritten.set(0)
                        let sequenceNumber = BlePsFtpUtility.BlePsFtpRfc76SequenceNumber()
                        let requs = BlePsFtpUtility.buildRfc76MessageFrameAll(totalStream, mtuSize: self.mtuSize, sequenceNumber: sequenceNumber)
                        for packet in requs { try self.transmitNotificationPacket(packet, response: true) }
                        continuation.resume()
                    } catch let error {
                        BleLogger.error("PS-FTP notification send interrupted error for ID: \(id) - \(error)")
                        continuation.resume(throwing: error)
                    }
                } else {
                    continuation.resume(throwing: BlePsFtpException.operationCanceled)
                }
            }
            self.sendNotificationOperationQueue.addOperation(block)
        }
    }
    
    /// Waits for device notifications indefinitely, yielding each notification as it arrives.
    /// Cancel the enclosing Task to stop.
    /// - Returns: AsyncThrowingStream of PsFtpNotification
    open func waitNotification() -> AsyncThrowingStream<PsFtpNotification, Error> {
        return AsyncThrowingStream<PsFtpNotification, Error> { cont in
            let block = BlockOperation()
            block.addExecutionBlock { [unowned self, weak block] in
                BleLogger.trace("PS-FTP wait notification operation started")
                if !(block?.isCancelled ?? true) {
                    do {
                        repeat {
                            let packet = try self.notificationInputQueue.pollUntilSignaled(canceled: block ?? BlockOperation(), cancelError: BlePsFtpException.operationCanceled)
                            if packet.first?.key == 0, var frame = packet.first?.value {
                                if frame.next == 0 && frame.status != BlePsFtpUtility.RFC76_STATUS_ERROR_OR_RESPONSE {
                                    let notification = PsFtpNotification()
                                    notification.id = Int32(frame.payload[0])
                                    notification.parameters.append(frame.payload.subdata(in: 1..<frame.payload.count))
                                    while frame.status == BlePsFtpUtility.RFC76_STATUS_MORE {
                                        let packet = try self.notificationInputQueue.poll(self.PROTOCOL_TIMEOUT)
                                        if packet.first?.key == 0, let newFrame = packet.first?.value {
                                            frame = newFrame
                                            if frame.status != BlePsFtpUtility.RFC76_STATUS_ERROR_OR_RESPONSE {
                                                notification.parameters.append(frame.payload.subdata(in: 0..<frame.payload.count))
                                            }
                                        } else {
                                            cont.finish(throwing: BlePsFtpException.responseError(errorCode: (packet.first?.key) ?? -1))
                                            return
                                        }
                                    }
                                    if frame.status == BlePsFtpUtility.RFC76_STATUS_LAST {
                                        cont.yield(notification)
                                    }
                                }
                            } else {
                                cont.finish(throwing: BlePsFtpException.responseError(errorCode: (packet.first?.key) ?? -1))
                                return
                            }
                        } while true
                    } catch let error {
                        self.logPsFtpError("PS-FTP waitNotification interrupted", error)
                        cont.finish(throwing: error)
                    }
                } else {
                    cont.finish(throwing: BlePsFtpException.operationCanceled)
                }
            }
            self.waitNotificationOperationQueue.addOperation(block)
        }
    }

    public func waitPsFtpReady(_ checkConnection: Bool) async throws {
        var cancellable: AnyCancellable?
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            cancellable = clientReady(checkConnection)
                .sink(receiveCompletion: { completion in
                    switch completion {
                    case .finished: continuation.resume()
                    case .failure(let error): continuation.resume(throwing: error)
                    }
                }, receiveValue: { _ in })
        }
        cancellable?.cancel()
    }

    public override func clientReady(_ checkConnection: Bool) -> AnyPublisher<Never, Error> {
        Publishers.Merge(
            waitNotificationEnabled(BlePsFtpClient.PSFTP_MTU_CHARACTERISTIC, checkConnection: checkConnection),
            waitNotificationEnabled(BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC, checkConnection: checkConnection)
        ).eraseToAnyPublisher()
    }
    
    private func logPsFtpError(_ prefix: String, _ error: Error) {
        if let psftp = error as? BlePsFtpException {
            BleLogger.error("\(prefix): \(psftp.errorName) (code=\(psftp._code))")
        } else {
            BleLogger.error("\(prefix): \(error)")
        }
    }
}
