
import Foundation
import PolarBleSdk
import MessageUI

class DataCollector  {
    enum StreamType {
        case
            ACC,
            PPG,
            PPI,
            AGS,
            BIOZ,
            MARKER,
            ECG,
            HR,
            OHR_SNR
    }
    
    var streams = [StreamType : (URL,OutputStream,String)]()
    
    static func currentDateTime() -> String {
        let dateFormatter : DateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyyMMdd_HHmmss"
        let date = Date()
        return dateFormatter.string(from: date)
    }
    
    static func applicationDocumentsDirectory() -> String! {
        let paths = NSSearchPathForDirectoriesInDomains(FileManager.SearchPathDirectory.documentDirectory, FileManager.SearchPathDomainMask.userDomainMask, true)
        var basePath: String!
        if(paths.count > 0) {
            basePath = paths[0]
        }
        return basePath
    }
    
    func startStreamWithTag(_ deviceId: String, tag: String) -> (URL, OutputStream, String) {
        let name = deviceId + "_" + DataCollector.currentDateTime() + "_" + tag + ".txt"
        let fileURL = try! FileManager.default.url(for: .documentDirectory, in: .userDomainMask, appropriateFor: nil, create: false)
            .appendingPathComponent(deviceId + "_" + DataCollector.currentDateTime() + "_" + tag + ".txt")
        
        let outputStream = OutputStream(url: fileURL, append: true)
        outputStream!.open()
        return (fileURL, outputStream!, name)
    }
    
    func startACCStream(_ deviceId: String) {
        streams[.ACC] = startStreamWithTag(deviceId, tag: "ACC")
    }
    
    func startPPGStream(_ deviceId: String) {
        streams[.PPG] = startStreamWithTag(deviceId, tag: "PPG")
    }
    
    func startPPIStream(_ deviceId: String) {
        streams[.PPI] = startStreamWithTag(deviceId, tag: "PPI")
    }
    
    func startEcgStream(_ deviceId: String) {
        streams[.ECG] = startStreamWithTag(deviceId, tag: "ECG")
    }
    
    func streamAcc(_ timeStamp: UInt64, x: Int32, y: Int32, z: Int32) {
        _ = streams[.ACC]!.1.write("\(x) \(y) \(z) \(timeStamp)\r\n")
    }
    
    func streamPpg(_ timeStamp: UInt64, ppg0: Int32, ppg1: Int32, ppg2: Int32, ambient: Int32) {
        _ = streams[.PPG]!.1.write("\(ppg0) \(ppg1) \(ppg2) \(ambient) \(timeStamp)\r\n")
    }
    
    func streamPpi(_ timeStamp: UInt64, ppi: UInt16, errorEstimate: UInt16, blocker: Int, contact: Int, contactSupported: Int, hr: Int) {
        _ = streams[.PPI]!.1.write("\(ppi) \(errorEstimate) \(blocker) \(contact) \(contactSupported) \(hr) \(timeStamp)\r\n")
    }
    
    func streamEcg(_ timeStamp: UInt64, ecg: Int32) {
        _ = streams[.ECG]!.1.write("\(ecg) \(timeStamp)\r\n")
    }
    
    func finalizeStreams(_ deviceId: String, view: ViewController) {
        var datas = [(String,Data)]()
        for item in streams {
            item.value.1.close()
            do {
                datas.append((item.value.2, try Data.init(contentsOf: item.value.0)))
            } catch(_){
                print("Error")
            }
        }
        if datas.count != 0 {
            sendMailWithAttachment(datas: datas, deviceId: deviceId, view: view)
        }
    }
    
    func sendMailWithAttachment( datas: [(String,Data)], deviceId: String, view: ViewController){
        if( datas.count != 0 ){
            if MFMailComposeViewController.canSendMail()  {
                let mailComposer = MFMailComposeViewController()
                let fileName = DataCollector.currentDateTime() + "_" + deviceId
                mailComposer.setSubject(fileName)
                mailComposer.setMessageBody(deviceId, isHTML: false)
                for item in datas {
                    mailComposer.addAttachmentData(item.1, mimeType: "text/plain", fileName: item.0)
                }
                view.present(mailComposer, animated: true)
            } else {
                print("Mail sending prohibited")
            }
        } else {
            print("No data to send")
        }
    }
}

extension OutputStream {
    func write(_ string: String, encoding: String.Encoding = .utf8, allowLossyConversion: Bool = false) -> Int {
        string.data(using: .utf8)?.withUnsafeBytes({ (raw) -> Void in
            let bufferPointer: UnsafePointer<UInt8> = raw.baseAddress!.assumingMemoryBound(to: UInt8.self)
            let rawPtr = UnsafePointer<UInt8>(bufferPointer)
            self.write(rawPtr, maxLength: string.count)
        })
        return string.count
    }
}
