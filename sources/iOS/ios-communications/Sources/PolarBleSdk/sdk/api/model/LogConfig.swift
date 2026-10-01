/// Copyright © 2024 Polar Electro Oy. All rights reserved.

import Foundation

let LOG_CONFIG_PATH = "/SDLOGS.BPB"

public struct LogConfig {

    public var ohrLogEnabled: Bool? = nil
    public var ppiLogEnabled: Bool? = nil
    public var accelerationLogEnabled: Bool? = nil
    public var caloriesLogEnabled: Bool? = nil
    public var gpsLogEnabled: Bool? = nil
    public var gpsNmeaLogEnabled: Bool? = nil
    public var magnetometerLogEnabled: Bool? = nil
    public var tapLogEnabled: Bool? = nil
    public var barometerLogEnabled: Bool? = nil
    public var gyroscopeLogEnabled: Bool? = nil
    public var sleepLogEnabled: Bool? = nil
    public var slopeLogEnabled: Bool? = nil
    public var ambientLightLogEnabled: Bool? = nil
    public var tlrLogEnabled: Bool? = nil
    public var ondemandLogEnabled: Bool? = nil
    public var capsenseLogEnabled: Bool? = nil
    public var fusionLogEnabled: Bool? = nil
    public var metLogEnabled: Bool? = nil
    public var verticalAccLogEnabled: Bool? = nil
    public var amdLogEnabled: Bool? = nil
    public var skinTemperatureLogEnabled: Bool? = nil
    public var compassLogEnabled: Bool? = nil
    public var speed3DLogEnabled: Bool? = nil
    public var retainSettingsOverBoot: Bool? = nil
    public var logTrigger: Int? = nil
    public var magnetometerFrequency: Int? = nil
    public var hrSensorConfig: HrSensorConfig? = nil
    
    
    public init(
        ppiLogEnabled: Bool?,
        accelerationLogEnabled: Bool?,
        caloriesLogEnabled: Bool?,
        gpsLogEnabled: Bool?,
        gpsNmeaLogEnabled: Bool?,
        magnetometerLogEnabled: Bool?,
        tapLogEnabled: Bool?,
        barometerLogEnabled: Bool?,
        gyroscopeLogEnabled: Bool?,
        sleepLogEnabled: Bool?,
        slopeLogEnabled: Bool?,
        ambientLightLogEnabled: Bool?,
        tlrLogEnabled: Bool?,
        ondemandLogEnabled: Bool?,
        capsenseLogEnabled: Bool?,
        fusionLogEnabled: Bool?,
        metLogEnabled: Bool?,
        ohrLogEnabled: Bool?,
        verticalAccLogEnabled: Bool?,
        amdLogEnabled: Bool?,
        skinTemperatureLogEnabled: Bool?,
        compassLogEnabled: Bool?,
        speed3DLogEnabled: Bool?,
        logTrigger: Int?,
        magnetometerFrequency: Int?,
        hrSensorConfig: HrSensorConfig?
    ) {
        
        self.accelerationLogEnabled = accelerationLogEnabled
        self.ambientLightLogEnabled = ambientLightLogEnabled
        self.amdLogEnabled = amdLogEnabled
        self.barometerLogEnabled = barometerLogEnabled
        self.caloriesLogEnabled = caloriesLogEnabled
        self.capsenseLogEnabled = capsenseLogEnabled
        self.compassLogEnabled = compassLogEnabled
        self.fusionLogEnabled = fusionLogEnabled
        self.gpsLogEnabled = gpsLogEnabled
        self.gpsNmeaLogEnabled = gpsNmeaLogEnabled
        self.gyroscopeLogEnabled = gyroscopeLogEnabled
        self.magnetometerLogEnabled = magnetometerLogEnabled
        self.metLogEnabled = metLogEnabled
        self.ohrLogEnabled = ohrLogEnabled
        self.ondemandLogEnabled = ondemandLogEnabled
        self.ppiLogEnabled = ppiLogEnabled
        self.skinTemperatureLogEnabled = skinTemperatureLogEnabled
        self.sleepLogEnabled = sleepLogEnabled
        self.slopeLogEnabled = slopeLogEnabled
        self.speed3DLogEnabled = speed3DLogEnabled
        self.tapLogEnabled = tapLogEnabled
        self.tlrLogEnabled = tlrLogEnabled
        self.verticalAccLogEnabled = verticalAccLogEnabled
        self.logTrigger = logTrigger
        self.magnetometerFrequency = magnetometerFrequency
        self.hrSensorConfig = hrSensorConfig
    }
    
    static func fromProto(proto: Data_PbSensorDataLog) -> LogConfig {

        return LogConfig(
            ppiLogEnabled: proto.hasPpiLogEnabled ? proto.ppiLogEnabled : nil,
            accelerationLogEnabled: proto.hasAccelerationLogEnabled ? proto.accelerationLogEnabled : nil,
            caloriesLogEnabled: proto.hasCaloriesLogEnabled ? proto.caloriesLogEnabled : nil,
            gpsLogEnabled: proto.hasGpsLogEnabled ? proto.gpsLogEnabled : nil,
            gpsNmeaLogEnabled: proto.hasGpsNmeaLogEnabled ? proto.gpsNmeaLogEnabled : nil,
            magnetometerLogEnabled: proto.hasMagnetometerLogEnabled ? proto.magnetometerLogEnabled : nil,
            tapLogEnabled: proto.hasTapLogEnabled ? proto.tapLogEnabled : nil,
            barometerLogEnabled: proto.hasBarometerLogEnabled ? proto.barometerLogEnabled : nil,
            gyroscopeLogEnabled: proto.hasGyroscopeLogEnabled ? proto.gyroscopeLogEnabled : nil,
            sleepLogEnabled: proto.hasSleepLogEnabled ? proto.sleepLogEnabled : nil,
            slopeLogEnabled: proto.slopeLogEnabled ? proto.slopeLogEnabled : nil,
            ambientLightLogEnabled: proto.hasAmbientLightLogEnabled ? proto.ambientLightLogEnabled : nil,
            tlrLogEnabled: proto.hasTlrLogEnabled ? proto.tlrLogEnabled : nil,
            ondemandLogEnabled: proto.hasOndemandLogEnabled ? proto.ondemandLogEnabled : nil,
            capsenseLogEnabled: proto.hasCapsenseLogEnabled ? proto.capsenseLogEnabled : nil,
            fusionLogEnabled: proto.hasFusionLogEnabled ? proto.fusionLogEnabled : nil,
            metLogEnabled: proto.hasMetLogEnabled ? proto.metLogEnabled : nil,
            ohrLogEnabled: proto.hasOhrLogEnabled ? proto.ohrLogEnabled : nil,
            verticalAccLogEnabled: proto.hasVerticalAccLogEnabled ? proto.verticalAccLogEnabled : nil,
            amdLogEnabled: proto.hasAmdLogEnabled ? proto.amdLogEnabled : nil,
            skinTemperatureLogEnabled: proto.hasSkinTemperatureLogEnabled ? proto.skinTemperatureLogEnabled : nil,
            compassLogEnabled: proto.hasCompassLogEnabled ? proto.compassLogEnabled : nil,
            speed3DLogEnabled: proto.hasSpeed3DLogEnabled ? proto.speed3DLogEnabled : nil,
            logTrigger: proto.hasLogTrigger ? proto.logTrigger.rawValue : nil,
            magnetometerFrequency: proto.hasMagnetometerLogFrequency ? proto.magnetometerLogFrequency.rawValue : nil,
            hrSensorConfig: HrSensorConfig.fromProto(proto: proto)
        )
    }
    
    static func toProto(logConfig: LogConfig) -> Data_PbSensorDataLog {
        var pbSensorDataLog = Data_PbSensorDataLog()

        if (logConfig.ohrLogEnabled != nil) {pbSensorDataLog.ohrLogEnabled = logConfig.ohrLogEnabled!}
        if (logConfig.ppiLogEnabled != nil) {pbSensorDataLog.ppiLogEnabled = logConfig.ppiLogEnabled!}
        if (logConfig.accelerationLogEnabled != nil) {pbSensorDataLog.accelerationLogEnabled = logConfig.accelerationLogEnabled!}
        if (logConfig.caloriesLogEnabled != nil) {pbSensorDataLog.caloriesLogEnabled = logConfig.caloriesLogEnabled!}
        if (logConfig.gpsLogEnabled != nil) {pbSensorDataLog.gpsLogEnabled = logConfig.gpsLogEnabled!}
        if (logConfig.gpsNmeaLogEnabled != nil) {pbSensorDataLog.gpsNmeaLogEnabled = logConfig.gpsNmeaLogEnabled!}
        if (logConfig.magnetometerLogEnabled != nil) {pbSensorDataLog.magnetometerLogEnabled = logConfig.magnetometerLogEnabled!}
        if (logConfig.tapLogEnabled != nil) {pbSensorDataLog.tapLogEnabled = logConfig.tapLogEnabled!}
        if (logConfig.barometerLogEnabled != nil) {pbSensorDataLog.barometerLogEnabled = logConfig.barometerLogEnabled!}
        if (logConfig.gyroscopeLogEnabled != nil) {pbSensorDataLog.gyroscopeLogEnabled = logConfig.gyroscopeLogEnabled!}
        if (logConfig.sleepLogEnabled != nil) {pbSensorDataLog.sleepLogEnabled = logConfig.sleepLogEnabled!}
        if (logConfig.slopeLogEnabled != nil) {pbSensorDataLog.slopeLogEnabled = logConfig.slopeLogEnabled!}
        if (logConfig.ambientLightLogEnabled != nil) {pbSensorDataLog.ambientLightLogEnabled = logConfig.ambientLightLogEnabled!}
        if (logConfig.tlrLogEnabled != nil) {pbSensorDataLog.tlrLogEnabled = logConfig.tlrLogEnabled!}
        if (logConfig.ondemandLogEnabled != nil) {pbSensorDataLog.ondemandLogEnabled = logConfig.ondemandLogEnabled!}
        if (logConfig.capsenseLogEnabled != nil) {pbSensorDataLog.capsenseLogEnabled = logConfig.capsenseLogEnabled!}
        if (logConfig.fusionLogEnabled != nil) {pbSensorDataLog.fusionLogEnabled = logConfig.fusionLogEnabled!}
        if (logConfig.metLogEnabled != nil) {pbSensorDataLog.metLogEnabled = logConfig.metLogEnabled!}
        if (logConfig.verticalAccLogEnabled != nil) {pbSensorDataLog.verticalAccLogEnabled = logConfig.verticalAccLogEnabled!}
        if (logConfig.amdLogEnabled != nil) {pbSensorDataLog.amdLogEnabled = logConfig.amdLogEnabled!}
        if (logConfig.skinTemperatureLogEnabled != nil) {pbSensorDataLog.skinTemperatureLogEnabled = logConfig.skinTemperatureLogEnabled!}
        if (logConfig.compassLogEnabled != nil) {pbSensorDataLog.compassLogEnabled = logConfig.compassLogEnabled!}
        if (logConfig.speed3DLogEnabled != nil) {pbSensorDataLog.speed3DLogEnabled = logConfig.speed3DLogEnabled!}
        if let magnetometerFrequency = logConfig.magnetometerFrequency,
           let frequency = Data_PbSensorDataLog.PbMagnetometerLogFrequency(rawValue: magnetometerFrequency) {
            pbSensorDataLog.magnetometerLogFrequency = frequency
        }
        if let logTrigger = logConfig.logTrigger,
           let trigger = Data_PbSensorDataLog.PbLogTrigger(rawValue: logTrigger) {
            pbSensorDataLog.logTrigger = trigger
        }
        guard let hrSensorConfig = logConfig.hrSensorConfig else {
            return pbSensorDataLog
        }
        let sensorConfigProto = Data_PbBleHrSensorConfig.with {
            $0.deviceName = PbBleDeviceName.with {
                $0.name = hrSensorConfig.deviceInformation ?? ""
            }
            $0.waitForConnect = hrSensorConfig.waitUntilConnected ?? false
        }
        pbSensorDataLog.bleHrSensorConfig = sensorConfigProto

        return pbSensorDataLog
    }
}

public struct HrSensorConfig {

    public var deviceInformation: String? = nil
    public var waitUntilConnected: Bool? = nil

    public init(deviceInformation: String?, waitUntilConnected: Bool?) {
        self.deviceInformation = deviceInformation
        self.waitUntilConnected = waitUntilConnected
    }

    static func fromProto(proto: Data_PbSensorDataLog) -> HrSensorConfig? {
        guard proto.hasBleHrSensorConfig else {
            return nil
        }

        let hrConfig = proto.bleHrSensorConfig
        return HrSensorConfig(
            deviceInformation: hrConfig.hasDeviceName ? hrConfig.deviceName.name : nil,
            waitUntilConnected: hrConfig.hasWaitForConnect ? hrConfig.waitForConnect : nil
        )
    }
}
