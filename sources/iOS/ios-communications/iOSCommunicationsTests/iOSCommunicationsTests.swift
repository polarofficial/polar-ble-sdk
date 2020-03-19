
import XCTest
import iOSCommunications
import RxSwift
import CoreBluetooth

class iOSCommunicationsTests: XCTestCase {
    
    override func setUp() {
        super.setUp()
        // Put setup code here. This method is called before the invocation of each test method in the class.
    }
    
    override func tearDown() {
        // Put teardown code here. This method is called after the invocation of each test method in the class.
        super.tearDown()
    }
    
    func testPsFtpUtils(){
        
        class logger: BleLoggerProtocol {
            func logMessage(_ message: String) {
                NSLog(message)
            }
        }
        
        BleLogger.setLogger(logger())
        
        let seq = BlePsFtpUtility.BlePsFtpRfc76SequenceNumber()
        let bytes = [UInt8](repeating: 0, count: 60)
        let stream = InputStream.init(data: Data.init(bytes: UnsafePointer<UInt8>(bytes), count: 60));
        stream.open()
        let requs = BlePsFtpUtility.buildRfc76MessageFrameAll(stream, mtuSize: 20, sequenceNumber: seq)
        stream.close()
        XCTAssert(requs.count == 4)
        
        let bytes2 = [UInt8](repeating: 3, count: 2)
        let frame = BlePsFtpUtility.processRfc76MessageFrame(Data(bytes: UnsafePointer<UInt8>(bytes2), count: 2))
        XCTAssert(frame.next == 1 &&
                  frame.status == BlePsFtpUtility.RFC76_STATUS_LAST &&
                  frame.sequenceNumber == 0 &&
                  frame.payload != nil)
        
        var bytes3 = [UInt8](repeating: 1, count: 3)
        bytes3[1] = 0
        bytes3[2] = 0
        let frame2 = BlePsFtpUtility.processRfc76MessageFrame(Data(bytes: UnsafePointer<UInt8>(bytes3), count: 3))
        XCTAssert(frame2.next == 1 &&
                  frame2.status == BlePsFtpUtility.RFC76_STATUS_ERROR_OR_RESPONSE &&
                  frame2.sequenceNumber == 0 &&
                  frame2.payload == nil)
        
        var bytes4 = [UInt8](repeating: 0xF3, count: 2)
        bytes4[1] = 0
        let frame3 = BlePsFtpUtility.processRfc76MessageFrame(Data(bytes: UnsafePointer<UInt8>(bytes4), count: 2))
        XCTAssert(frame3.next == 1 &&
            frame3.status == BlePsFtpUtility.RFC76_STATUS_LAST &&
            frame3.sequenceNumber == 0x0F &&
            frame3.payload != nil)
        }
}
