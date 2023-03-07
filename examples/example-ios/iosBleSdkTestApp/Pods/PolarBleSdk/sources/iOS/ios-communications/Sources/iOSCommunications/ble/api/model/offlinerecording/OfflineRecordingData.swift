//  Copyright © 2023 Polar. All rights reserved.

import Foundation

fileprivate let SECURITY_STRATEGY_INDEX:Int = 0
fileprivate let SECURITY_STRATEGY_LENGTH:Int = 1

fileprivate let OFFLINE_HEADER_MAGIC:UInt = 0x3D7C4C2B

fileprivate let OFFLINE_HEADER_LENGTH = 16
fileprivate let OFFLINE_SETTINGS_SIZE_FIELD_LENGTH = 1
fileprivate let DATE_TIME_LENGTH = 20
fileprivate let PACKET_SIZE_LENGTH = 2

public struct OfflineRecordingMetaData {
    let offlineRecordingHeader: OfflineRecordingHeader
    let startTime: Date
    let recordingSettings: PmdSetting?
    let securityInfo: PmdSecret
    let dataPayloadSize: Int
}

public struct OfflineRecordingHeader {
    let magic: UInt
    let version: UInt
    let free: UInt
    let eswHash: UInt
}

public struct OfflineRecordingData<DataType> {
    let offlineRecordingHeader: OfflineRecordingHeader
    let startTime: Date
    let recordingSettings: PmdSetting?
    let data: DataType
    
    public static func parseDataFromOfflineFile(fileData: Data, type: PmdMeasurementType, secret: PmdSecret? = nil) throws -> OfflineRecordingData<Any> {
        BleLogger.trace("Start offline file parsing. File size is \(fileData.count) and type \(type)")
        
        guard !fileData.isEmpty else {
            throw OfflineRecordingError.emptyFile
        }
        
        let (metaData, metaDataLength):(OfflineRecordingMetaData, Int)
        do {
            (metaData, metaDataLength) = try parseMetaData(fileData, secret)
        } catch {
            throw OfflineRecordingError.offlineRecordingErrorMetaDataParseFailed(description: "\(error)")
        }
        
        let payloadDataBytes = fileData.subdata(in: metaDataLength..<fileData.count)
        
        guard !payloadDataBytes.isEmpty else {
            throw OfflineRecordingError.offlineRecordingNoPayloadData
        }
        
        let parsedData = try parseData(
            dataBytes: payloadDataBytes,
            metaData: metaData,
            builder: try getDataBuilder(type: type)
        )
        
        return OfflineRecordingData<Any>(offlineRecordingHeader: metaData.offlineRecordingHeader, startTime: metaData.startTime, recordingSettings: metaData.recordingSettings,  data: parsedData)
    }
    
    private static func getDataBuilder(type: PmdMeasurementType) throws -> Any {
        switch(type) {
        case .ecg:
            return EcgData()
        case .ppg:
            return PpgData()
        case .acc:
            return AccData()
        case .ppi:
            return PpiData()
        case .gyro:
            return GyrData()
        case .mgn:
            return MagData()
        case .offline_hr:
            return OfflineHrData()
        default:
            throw OfflineRecordingError.offlineRecordingErrorNoParserForData
        }
    }
    
    private static func parseMetaData(_ fileBytes: Data,_  secret: PmdSecret?) throws -> (OfflineRecordingMetaData, Int) {
        var securityOffset = 0
        let offlineRecordingSecurityStrategy = try parseSecurityStrategy(strategyBytes: fileBytes.subdataSafe(in: SECURITY_STRATEGY_INDEX..<SECURITY_STRATEGY_LENGTH))
        securityOffset += SECURITY_STRATEGY_LENGTH
        
        let metaDataBytes = try decryptMetaData(offlineRecordingSecurityStrategy: offlineRecordingSecurityStrategy, metaData: fileBytes.subdataSafe(in: securityOffset..<fileBytes.count), secret: secret)
        
        let offlineRecordingHeader = try parseHeader(headerBytes: metaDataBytes.subdataSafe(in: 0..<OFFLINE_HEADER_LENGTH))
        var metaDataOffset = OFFLINE_HEADER_LENGTH
        
        // guard
        guard offlineRecordingHeader.magic == OFFLINE_HEADER_MAGIC else {
            throw OfflineRecordingError.offlineRecordingHasWrongSignature
        }
        
        let offlineRecordingStartTime = try parseStartTime(startTimeBytes: metaDataBytes.subdataSafe(in: metaDataOffset..<metaDataOffset + DATE_TIME_LENGTH))
        metaDataOffset += DATE_TIME_LENGTH
        
        let (pmdSetting, settingsLength) = try parseSettings(metaDataBytes: metaDataBytes.subdataSafe(in: metaDataOffset..<metaDataBytes.count))
        metaDataOffset += settingsLength
        
        let (payloadSecurity, securityInfoLength) = try parseSecurityInfo(securityInfoBytes: metaDataBytes.subdataSafe(in: metaDataOffset ..< metaDataBytes.count), secret: secret)
        metaDataOffset += securityInfoLength
        
        // padding bytes
        let paddingBytes1Length = parsePaddingBytes(metaDataOffset: metaDataOffset, offlineRecordingSecurityStrategy: offlineRecordingSecurityStrategy)
        metaDataOffset += paddingBytes1Length
        
        let dataPayloadSize = try parsePacketSize(packetSize: metaDataBytes.subdataSafe(in: metaDataOffset..<(metaDataOffset + PACKET_SIZE_LENGTH)))
        metaDataOffset += PACKET_SIZE_LENGTH
        
        let paddingBytes2Length = parsePaddingBytes(metaDataOffset: metaDataOffset, offlineRecordingSecurityStrategy: offlineRecordingSecurityStrategy)
        metaDataOffset += paddingBytes2Length
        
        let metaData = OfflineRecordingMetaData(
            offlineRecordingHeader: offlineRecordingHeader,
            startTime: offlineRecordingStartTime,
            recordingSettings: pmdSetting,
            securityInfo: payloadSecurity,
            dataPayloadSize: dataPayloadSize
        )
        return (metaData, securityOffset + metaDataOffset)
    }
    
    private static func parseSecurityStrategy(strategyBytes: Data) throws -> PmdSecret.SecurityStrategy {
        guard strategyBytes.count == 1, let strategyByte = strategyBytes.first else {
            throw OfflineRecordingError.offlineRecordingErrorMetaDataParseFailed(description:  "security strategy parse failed. Strategy bytes size was \(strategyBytes.count)")
        }
        return try PmdSecret.SecurityStrategy.fromByte(strategyByte: strategyByte)
    }
    
    private static func decryptMetaData(offlineRecordingSecurityStrategy: PmdSecret.SecurityStrategy, metaData: Data, secret: PmdSecret?) throws -> Data {
        switch (offlineRecordingSecurityStrategy) {
            
        case .none:
            return metaData
        case .xor:
            guard let s = secret else {
                throw OfflineRecordingError.offlineRecordingErrorSecretMissing
            }
            return try s.decryptArray(cipherArray: metaData)
            
        case .aes128, .aes256:
            guard let s = secret else {
                throw OfflineRecordingError.offlineRecordingErrorSecretMissing
            }
            
            guard s.strategy == offlineRecordingSecurityStrategy else {
                throw OfflineRecordingError.offlineRecordingSecurityStrategyMissMatch(description: "Offline file is encrypted using \(offlineRecordingSecurityStrategy). The key provided is \(s.strategy)")
            }
            
            let endOffset = (metaData.count / 16 * 16)
            let metaDataChunk = try metaData.subdataSafe(in: 0..<endOffset)
            return try s.decryptArray(cipherArray: metaDataChunk)
        }
    }
    
    private static func parseHeader(headerBytes: Data) -> OfflineRecordingHeader {
        var offset = 0
        let step = 4
        let magic = TypeUtils.convertArrayToUnsignedInt(headerBytes, offset: offset, size: step)
        offset += step
        let version = TypeUtils.convertArrayToUnsignedInt(headerBytes, offset: offset, size: step)
        offset += step
        let free = TypeUtils.convertArrayToUnsignedInt(headerBytes, offset: offset, size: step)
        offset += step
        let eswHash = TypeUtils.convertArrayToUnsignedInt(headerBytes, offset: offset, size: step)
        offset += step
        
        return OfflineRecordingHeader(magic: magic, version: version, free: free, eswHash: eswHash)
    }
    
    private static func parseStartTime(startTimeBytes: Data) throws -> Date {
        let expectedStartTime: String = String(decoding: startTimeBytes, as: UTF8.self)
        let trimmedString = String(expectedStartTime.replacingOccurrences(of: " ", with: "T").dropLast() + "Z")
        
        let dateFormatter = ISO8601DateFormatter()
        dateFormatter.formatOptions = [.withInternetDateTime]
        
        guard let result = dateFormatter.date(from: trimmedString) else {
            throw OfflineRecordingError.offlineRecordingErrorMetaDataParseFailed(description: "Couldn't parse start time from \(trimmedString)")
        }
        return result
    }
    
    private static func parseSettings(metaDataBytes: Data) throws -> (PmdSetting?, Int) {
        var offset = 0
        let settingsLength = Int(metaDataBytes[offset])
        offset += OFFLINE_SETTINGS_SIZE_FIELD_LENGTH
        let settingBytes = try metaDataBytes.subdataSafe(in:offset..<(offset + settingsLength))
        var pmdSetting: PmdSetting? = nil
        
        if (!settingBytes.isEmpty) {
            pmdSetting  = PmdSetting(settingBytes)
        }
        return (pmdSetting, offset + settingsLength)
    }
    
    private static func parseSecurityInfo(securityInfoBytes: Data, secret: PmdSecret?) throws -> (PmdSecret, Int) {
        var offset = 0
        let infoLength = securityInfoBytes[offset]
        offset += 1
        
        if infoLength == 0 {
            if let s = secret {
                return (s, offset)
            } else {
                return try (PmdSecret(strategy : PmdSecret.SecurityStrategy.none, key : Data()), offset)
            }
        }
        
        let strategy = securityInfoBytes[offset]
        offset += 1
        switch(try PmdSecret.SecurityStrategy.fromByte(strategyByte: strategy)) {
            
        case .none:
            return try (PmdSecret(strategy : PmdSecret.SecurityStrategy.none, key : Data()), offset)
        case .xor:
            let indexOfXor = Int(securityInfoBytes[offset])
            guard let xor = secret?.key[indexOfXor] else {
                throw OfflineRecordingError.offlineRecordingErrorSecretMissing
            }
            offset += 1
            return try (PmdSecret(strategy : PmdSecret.SecurityStrategy.xor, key : Data([xor])), offset)
        case .aes128:
            guard let key = secret?.key else {
                throw OfflineRecordingError.offlineRecordingErrorSecretMissing
            }
            return try (PmdSecret(strategy : PmdSecret.SecurityStrategy.aes128, key : key), offset)
        case .aes256:
            guard let key = secret?.key else {
                throw OfflineRecordingError.offlineRecordingErrorSecretMissing
            }
            return try (PmdSecret(strategy : PmdSecret.SecurityStrategy.aes256, key :key), offset)
        }
    }
    
    private static func parsePaddingBytes(metaDataOffset: Int, offlineRecordingSecurityStrategy: PmdSecret.SecurityStrategy) -> Int {
        switch (offlineRecordingSecurityStrategy) {
        case .none, .xor:
            return 0
        case .aes128, .aes256:
            return (16 - metaDataOffset % 16)
        }
    }
    
    private static func parsePacketSize(packetSize: Data) -> Int {
        return Int(TypeUtils.convertArrayToUnsignedInt(packetSize, offset: 0, size: 2))
    }
    
    private static func parseData(dataBytes: Data, metaData: OfflineRecordingMetaData, builder: Any) throws -> Any {
        var previousTimeStamp: UInt64 = 0
        
        var packetSize = metaData.dataPayloadSize
        let sampleRate = UInt(metaData.recordingSettings?.settings[PmdSetting.PmdSettingType.sampleRate]?.first ?? 0)
        
        let factor:Float
        if let data = metaData.recordingSettings?.settings[PmdSetting.PmdSettingType.factor]?.first {
            factor = Float(bitPattern: data)
        } else {
            factor = 1.0
        }
        
        var offset = 0
        let decryptedData = try metaData.securityInfo.decryptArray(cipherArray: dataBytes)
        
        repeat {
            let data = try decryptedData.subdataSafe(in:offset..<(packetSize + offset))
            offset += packetSize
            let dataFrame = try PmdDataFrame(data:data,
                                             { _ in previousTimeStamp }  ,
                                             { _ in factor },
                                             { _ in sampleRate })
            
            previousTimeStamp = dataFrame.timeStamp
            
            switch(builder) {
            case is EcgData:
                let ecgData =  try EcgData.parseDataFromDataFrame(frame: dataFrame)
                (builder as! EcgData).samples.append(contentsOf: ecgData.samples)
            case is AccData:
                let accData =  try AccData.parseDataFromDataFrame(frame: dataFrame)
                (builder as! AccData).samples.append(contentsOf: accData.samples)
            case is GyrData:
                let gyrData =  try GyrData.parseDataFromDataFrame(frame: dataFrame)
                (builder as! GyrData).samples.append(contentsOf: gyrData.samples)
            case is MagData:
                let magData =  try MagData.parseDataFromDataFrame(frame: dataFrame)
                (builder as! MagData).samples.append(contentsOf: magData.samples)
            case is PpgData:
                let ppgData =  try PpgData.parseDataFromDataFrame(frame: dataFrame)
                (builder as! PpgData).samples.append(contentsOf: ppgData.samples)
            case is PpiData:
                let ppiData =  try PpiData.parseDataFromDataFrame(frame: dataFrame)
                (builder as! PpiData).samples.append(contentsOf: ppiData.samples)
            case is OfflineHrData:
                let offlineHrData =  try OfflineHrData.parseDataFromDataFrame(frame: dataFrame)
                (builder as! OfflineHrData).samples.append(contentsOf: offlineHrData.samples)
            default:
                throw OfflineRecordingError.offlineRecordingErrorSecretMissing
            }
            
            if (offset < decryptedData.count) {
                packetSize = try parsePacketSize(packetSize: decryptedData.subdataSafe(in:offset..<(offset + PACKET_SIZE_LENGTH)))
                offset += PACKET_SIZE_LENGTH
            }
        } while (offset < decryptedData.count)
        return builder
    }
}


private extension Data {
    func subdataSafe(in range: Range<Data.Index>) throws -> Data {
        if range.upperBound <= self.count {
            return self.subdata(in: range)
        } else {
             throw OfflineRecordingError.offlineRecordingErrorMetaDataParseFailed(description: "Invalid range")
        }
    }
}
