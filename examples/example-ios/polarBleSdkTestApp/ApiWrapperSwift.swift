
import Foundation
import PolarBleSdk
import RxSwift

@objc class PolarBroadcastData : NSObject {
    let name: String
    let hr: Int
    let battery: Bool
    
    init(_ name: String, hr: Int, battery: Bool){
        self.name = name
        self.hr = hr
        self.battery = battery
    }
}

@objc class PolarSensorSettings: NSObject {
    let settings: PolarSensorSetting
    
    init(_ settings: PolarSensorSetting) {
        self.settings = settings
    }
}

@objc class AccData: NSObject {
    let timeStamp: UInt64
    let samples: [(Int32,Int32,Int32)]
    
    init(_ timeStamp: UInt64, samples: [(Int32,Int32,Int32)]) {
        self.timeStamp = timeStamp
        self.samples = samples
    }
}

@objc class PpgData: NSObject {
    let timeStamp: UInt64
    let samples: [(Int32,Int32,Int32,Int32)]
    
    init(_ timeStamp: UInt64, samples: [(Int32,Int32,Int32,Int32)]) {
        self.timeStamp = timeStamp
        self.samples = samples
    }
}

@objc class PolarDisposable: NSObject {
    var disposable: Disposable?
    init(_ disposable: Disposable?) {
        self.disposable = disposable
    }
    
    @objc func dispose() {
        self.disposable?.dispose()
        self.disposable = nil
    }
}

@objc class ApiWrapperSwift: NSObject {
    var api: PolarBleApi
    var broadcast: Disposable?
    var autoConnect: Disposable?
    
    override init() {
        self.api = PolarBleApiDefaultImpl.polarImplementation(DispatchQueue.main, features: Features.allFeatures.rawValue);
    }
    
    @objc func startListenPolarHrBroadcast(_ next: @escaping (PolarBroadcastData) -> Void) {
        stopListenPolarHrBroadcast()
        broadcast = self.api.startListenForPolarHrBroadcasts(nil)
            .observe(on: MainScheduler.instance)
            .subscribe{ e in
                switch e {
                case .completed:
                    break
                case .error(let err):
                    print("\(err)")
                case .next(let value):
                    next(PolarBroadcastData(value.deviceInfo.name, hr: Int(value.hr), battery: value.batteryStatus))
                }
            }
    }
    
    @objc func stopListenPolarHrBroadcast() {
        broadcast?.dispose()
        broadcast = nil
    }
    
    
    @objc func startAutoConnectToPolarDevice(_ rssi: Int, polarDeviceType: String?) {
        stopAutoConnectToPolarDevice()
        autoConnect = self.api.startAutoConnectToDevice(rssi, service: nil, polarDeviceType: polarDeviceType)
            .subscribe()
    }
    
    @objc func stopAutoConnectToPolarDevice() {
        autoConnect?.dispose()
        autoConnect = nil
    }
    
    @objc func connectToPolarDevice(_ identifier: String) {
        do{
            try self.api.connectToDevice(identifier)
        } catch {}
    }
    
    @objc func disconnectFromPolarDevice(_ identifier: String) {
        do{
            try self.api.disconnectFromDevice(identifier)
        } catch {}
    }
    
    @objc func isFeatureReady(_ identifier: String, feature: Int) -> Bool {
        return self.api.isFeatureReady(identifier, feature: Features.init(rawValue: feature) ?? Features.allFeatures)
    }
    
    @objc func setLocalTime(_ identifier: String, time: Date, success: @escaping () -> Void, error: @escaping (Error) -> Void ) {
        _ = api.setLocalTime(identifier, time: time, zone: TimeZone.current)
            .observe(on: MainScheduler.instance)
            .subscribe{ e in
                switch e {
                case .completed:
                    success()
                case .error(let err):
                    error(err)
                @unknown default:
                    fatalError()
                }
            }
    }
    
    @objc func startRecording(_ identifier: String, exerciseId: String, interval: Int, sampleType: Int, success: @escaping () -> Void, error: @escaping (Error) -> Void ) {
        _ = api.startRecording(identifier, exerciseId: exerciseId, interval: RecordingInterval.init(rawValue: interval) ?? RecordingInterval.interval_5s, sampleType: SampleType.init(rawValue: sampleType) ?? SampleType.hr)
            .observe(on: MainScheduler.instance)
            .subscribe { e in
                switch e {
                case .completed:
                    success()
                case .error(let err):
                    error(err)
                @unknown default:
                    fatalError()
                }
            }
    }
    
    @objc func stopRecording(_ identifier: String, success: @escaping () -> Void, error: @escaping (Error) -> Void ) {
        _ = api.stopRecording(identifier)
            .observe(on: MainScheduler.instance)
            .subscribe{ e in
                switch e {
                case .completed:
                    success()
                case .error(let err):
                    error(err)
                @unknown default:
                    fatalError()
                }
            }
    }
    
    @objc func requestRecordingStatus(_ identifier: String, success:@escaping (Bool,String) -> Void, error: @escaping (Error) -> Void ) {
        _ = api.requestRecordingStatus(identifier)
            .observe(on: MainScheduler.instance)
            .subscribe{ e in
                switch e {
                case .failure(let err):
                    error(err)
                case .success(let value):
                    success(value.ongoing,value.entryId)
                }
            }
    }
    
    @objc func requestEcgSettings(_ identifier: String, success: @escaping ((PolarSensorSettings)) -> Void, error: @escaping (Error) -> Void ) {
        _ = api.requestEcgSettings(identifier)
            .observe(on: MainScheduler.instance)
            .subscribe{ e in
                switch e {
                case .failure(let err):
                    error(err)
                case .success(let value):
                    success(PolarSensorSettings(value))
                }
            }
    }
    
    @objc func requestAccSettings(_ identifier: String, success: @escaping ((PolarSensorSettings)) -> Void, error: @escaping (Error) -> Void ) {
        _ = api.requestAccSettings(identifier)
            .observe(on: MainScheduler.instance)
            .subscribe{ e in
                switch e {
                case .failure(let err):
                    error(err)
                case .success(let value):
                    success(PolarSensorSettings(value))
                }
            }
    }
    
    @objc func requestPpgSettings(_ identifier: String, success: @escaping ((PolarSensorSettings)) -> Void, error: @escaping (Error) -> Void ) {
        _ = api.requestPpgSettings(identifier)
            .observe(on: MainScheduler.instance)
            .subscribe{ e in
                switch e {
                case .failure(let err):
                    error(err)
                case .success(let value):
                    success(PolarSensorSettings(value))
                }
            }
    }
    
    @objc func startEcgStreaming(_ identifier: String, settings: PolarSensorSettings, next: @escaping (UInt64,[Int32]) -> Void, error: @escaping (Error) -> Void ) -> PolarDisposable {
        return PolarDisposable(api.startEcgStreaming(identifier, settings: settings.settings.maxSettings())
                                .observe(on: MainScheduler.instance)
                                .subscribe{ e in
                                    switch e {
                                    case .completed:
                                        break
                                    case .error(let err):
                                        error(err)
                                    case .next(let value):
                                        next(value.timeStamp, value.samples)
                                    }
                                })
    }
    
    @objc func startAccStreaming(_ identifier: String, settings: PolarSensorSettings, next: @escaping ((AccData)) -> Void, error: @escaping (Error) -> Void ) -> PolarDisposable {
        return PolarDisposable(api.startAccStreaming(identifier, settings: settings.settings.maxSettings())
                                .observe(on: MainScheduler.instance)
                                .subscribe{ e in
                                    switch e {
                                    case .completed:
                                        break
                                    case .error(let err):
                                        error(err)
                                    case .next(let value):
                                        next(AccData.init(value.0, samples: value.1))
                                    }
                                })
    }
    
    @objc func startOhrPPGStreaming(_ identifier: String, settings: PolarSensorSettings, next: @escaping ((PpgData)) -> Void, error: @escaping (Error) -> Void ) -> PolarDisposable {
        return PolarDisposable(api.startOhrPPGStreaming(identifier, settings: settings.settings.maxSettings())
                                .observe(on: MainScheduler.instance)
                                .subscribe{ e in
                                    switch e {
                                    case .completed:
                                        break
                                    case .error(let err):
                                        error(err)
                                    case .next(let value):
                                        next(PpgData.init(value.0, samples: value.1))
                                    }
                                })
    }
    
    @objc func startOhrPPIStreaming(_ identifier: String, next: @escaping (UInt64,[UInt16]) -> Void, error: @escaping (Error) -> Void ) -> PolarDisposable {
        return PolarDisposable(api.startOhrPPIStreaming(identifier)
                                .observe(on: MainScheduler.instance)
                                .subscribe{ e in
                                    switch e {
                                    case .completed:
                                        break
                                    case .error(let err):
                                        error(err)
                                    case .next(let value):
                                        let samples = value.1.compactMap({ (sample) -> UInt16 in
                                            sample.ppInMs
                                        })
                                        next(value.0,samples)
                                    }
                                })
    }
    
    @objc func fetchStoredExerciseList(_ identifier: String, next: @escaping (String,Date,String) -> Void, error: @escaping (Error) -> Void ) {
        _ = api.fetchStoredExerciseList(identifier)
            .observe(on: MainScheduler.instance)
            .subscribe { e in
                switch e {
                case .completed:
                    break
                case .next(let entry):
                    next(entry.path,entry.date,entry.entryId)
                case .error(let err):
                    error(err)
                }
            }
    }
    
    @objc func fetchExercise(_ identifier: String, path: String, date: Date, entryId: String, success: @escaping (UInt32,[UInt32]) -> Void, error: @escaping (Error) -> Void ) {
        _ = api.fetchExercise(identifier, entry: (path, date: date, entryId: entryId))
            .observe(on: MainScheduler.instance)
            .subscribe{ e in
                switch e {
                case .failure(let err):
                    error(err)
                case .success(let value):
                    success(value.interval,value.samples)
                }
            }
    }
    
    @objc func removeExercise(_ identifier: String, path: String, date: Date, entryId: String, success: @escaping () -> Void, error: @escaping (Error) -> Void ) {
        _ = api.removeExercise(identifier, entry: (path, date: date, entryId: entryId))
            .observe(on: MainScheduler.instance)
            .subscribe{ e in
                switch e {
                case .completed:
                    success()
                case .error(let err):
                    error(err)
                @unknown default:
                    fatalError()
                }
            }
    }
}
