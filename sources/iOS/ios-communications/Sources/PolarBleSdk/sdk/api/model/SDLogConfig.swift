/// Copyright © 2024 Polar Electro Oy. All rights reserved.

import Foundation

let SERVICE_DATALOG_CONFIG_FILEPATH="/SDLOGS.BPB"

public struct SDLogConfig {
    
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
    
    public init(ppiLogEnabled: Bool?, accelerationLogEnabled: Bool?, caloriesLogEnabled: Bool?, gpsLogEnabled: Bool?, gpsNmeaLogEnabled: Bool?, magnetometerLogEnabled: Bool?, tapLogEnabled: Bool?, barometerLogEnabled: Bool?, gyroscopeLogEnabled: Bool?, sleepLogEnabled: Bool?, slopeLogEnabled: Bool?, ambientLightLogEnabled: Bool?, tlrLogEnabled: Bool?, ondemandLogEnabled: Bool?, capsenseLogEnabled: Bool?, fusionLogEnabled: Bool?, metLogEnabled: Bool?, ohrLogEnabled: Bool?, verticalAccLogEnabled: Bool?, amdLogEnabled: Bool?, skinTemperatureLogEnabled: Bool?, compassLogEnabled: Bool?, speed3DLogEnabled: Bool?, logTrigger: Int?, magnetometerFrequency: Int?) {
        
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
    }
    
    static func fromProto(proto: Data_PbSensorDataLog) -> SDLogConfig {
        
        return SDLogConfig(
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
            magnetometerFrequency: proto.hasMagnetometerLogFrequency ? proto.magnetometerLogFrequency.rawValue : nil
        )
    }
    
    static func toProto(sdLogConfig: SDLogConfig) -> Data_PbSensorDataLog{
        var pbSensorDataLog = Data_PbSensorDataLog()
        
        if (sdLogConfig.ohrLogEnabled != nil) {pbSensorDataLog.ohrLogEnabled = sdLogConfig.ohrLogEnabled!}
        if (sdLogConfig.ppiLogEnabled != nil) {pbSensorDataLog.ppiLogEnabled = sdLogConfig.ppiLogEnabled!}
        if (sdLogConfig.accelerationLogEnabled != nil) {pbSensorDataLog.accelerationLogEnabled = sdLogConfig.accelerationLogEnabled!}
        if (sdLogConfig.caloriesLogEnabled != nil) {pbSensorDataLog.caloriesLogEnabled = sdLogConfig.caloriesLogEnabled!}
        if (sdLogConfig.gpsLogEnabled != nil) {pbSensorDataLog.gpsLogEnabled = sdLogConfig.gpsLogEnabled!}
        if (sdLogConfig.gpsNmeaLogEnabled != nil) {pbSensorDataLog.gpsNmeaLogEnabled = sdLogConfig.gpsNmeaLogEnabled!}
        if (sdLogConfig.magnetometerLogEnabled != nil) {pbSensorDataLog.magnetometerLogEnabled = sdLogConfig.magnetometerLogEnabled!}
        if (sdLogConfig.tapLogEnabled != nil) {pbSensorDataLog.tapLogEnabled = sdLogConfig.tapLogEnabled!}
        if (sdLogConfig.barometerLogEnabled != nil) {pbSensorDataLog.barometerLogEnabled = sdLogConfig.barometerLogEnabled!}
        if (sdLogConfig.gyroscopeLogEnabled != nil) {pbSensorDataLog.gyroscopeLogEnabled = sdLogConfig.gyroscopeLogEnabled!}
        if (sdLogConfig.sleepLogEnabled != nil) {pbSensorDataLog.sleepLogEnabled = sdLogConfig.sleepLogEnabled!}
        if (sdLogConfig.slopeLogEnabled != nil) {pbSensorDataLog.slopeLogEnabled = sdLogConfig.slopeLogEnabled!}
        if (sdLogConfig.ambientLightLogEnabled != nil) {pbSensorDataLog.ambientLightLogEnabled = sdLogConfig.ambientLightLogEnabled!}
        if (sdLogConfig.tlrLogEnabled != nil) {pbSensorDataLog.tlrLogEnabled = sdLogConfig.tlrLogEnabled!}
        if (sdLogConfig.ondemandLogEnabled != nil) {pbSensorDataLog.ondemandLogEnabled = sdLogConfig.ondemandLogEnabled!}
        if (sdLogConfig.capsenseLogEnabled != nil) {pbSensorDataLog.capsenseLogEnabled = sdLogConfig.capsenseLogEnabled!}
        if (sdLogConfig.fusionLogEnabled != nil) {pbSensorDataLog.fusionLogEnabled = sdLogConfig.fusionLogEnabled!}
        if (sdLogConfig.metLogEnabled != nil) {pbSensorDataLog.metLogEnabled = sdLogConfig.metLogEnabled!}
        if (sdLogConfig.verticalAccLogEnabled != nil) {pbSensorDataLog.verticalAccLogEnabled = sdLogConfig.verticalAccLogEnabled!}
        if (sdLogConfig.amdLogEnabled != nil) {pbSensorDataLog.amdLogEnabled = sdLogConfig.amdLogEnabled!}
        if (sdLogConfig.skinTemperatureLogEnabled != nil) {pbSensorDataLog.skinTemperatureLogEnabled = sdLogConfig.skinTemperatureLogEnabled!}
        if (sdLogConfig.compassLogEnabled != nil) {pbSensorDataLog.compassLogEnabled = sdLogConfig.compassLogEnabled!}
        if (sdLogConfig.speed3DLogEnabled != nil) {pbSensorDataLog.speed3DLogEnabled = sdLogConfig.speed3DLogEnabled!}
        if (sdLogConfig.magnetometerFrequency != nil) {pbSensorDataLog.magnetometerLogFrequency = Data_PbSensorDataLog.PbMagnetometerLogFrequency.init(rawValue: sdLogConfig.magnetometerFrequency!)! }
        if (sdLogConfig.logTrigger != nil) {pbSensorDataLog.logTrigger = Data_PbSensorDataLog.PbLogTrigger.init(rawValue: sdLogConfig.logTrigger!)! }
        
        return pbSensorDataLog
    }
}
