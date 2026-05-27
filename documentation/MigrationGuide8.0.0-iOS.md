# Polar BLE SDK — RxSwift to Swift Concurrency Migration Guide

> **Status: Migration complete.**  
> This document describes the changes that were made to the Polar BLE SDK public API and provides
> instructions for updating consumer code. It is structured to serve as a prompt/reference for
> agentic AI tools performing automated migration of call sites.

---

## 1. Overview of What Changed

The Polar BLE SDK public API has been fully migrated away from RxSwift. The replacement
strategy follows these rules, which an AI agent must apply at every call site:

| Old pattern (RxSwift) | New pattern |
|---|---|
| `-> Observable<T>` (finite, single value) | `async throws -> T` |
| `-> Single<T>` | `async throws -> T` |
| `-> Completable` | `async throws` (returns `Void`) |
| `-> Observable<T>` (infinite stream) | `-> AsyncThrowingStream<T, Error>` |
| `-> Observable<T>` (multi-cast / hot stream) | `-> AnyPublisher<T, Error>` (Combine) |
| `.subscribe(onNext:onError:onCompleted:)` | `for try await value in stream { }` |
| `.subscribe(onSuccess:onError:)` | `try await` |
| `DisposeBag` / `.disposed(by:)` | `Task` cancellation (`task.cancel()`) |

**`import RxSwift` must be removed** from all consumer files. Replace with `import Combine`
where `AnyPublisher` is used, and ensure `import Foundation` is present for async/await.

---

## 2. Dependency Changes

### Remove
```swift
// Podfile — remove these lines
pod 'RxSwift', '~> 6.8.0'
pod 'RxBlocking', '~> 6.8.0'   // test targets
pod 'RxTest', '~> 6.8.0'       // test targets
```
```swift
// Package.swift — remove
.package(name: "RxSwift", url: "https://github.com/ReactiveX/RxSwift.git", ...)
```

### Keep / Add
```swift
// Combine is built into the platform — no pod/package needed
import Combine
```

---

## 3. Method-by-Method Migration Reference

### 3.1 `PolarBleApi` — Core API

#### `searchForDevice()`
```swift
// BEFORE
api.searchForDevice()
    .subscribe(onNext: { info in ... }, onError: { error in ... })
    .disposed(by: disposeBag)

// AFTER — AsyncThrowingStream; iterate with for-await, cancel via Task
let task = Task {
    do {
        for try await info in api.searchForDevice() {
            // handle PolarDeviceInfo
        }
    } catch {
        // handle error
    }
}
// To stop: task.cancel()
```

#### `searchForDevice(withRequiredDeviceNamePrefix:)`
Same pattern as `searchForDevice()` above — returns `AsyncThrowingStream<PolarDeviceInfo, Error>`.

#### `startAutoConnectToDevice(_:service:polarDeviceType:)`
```swift
// BEFORE
api.startAutoConnectToDevice(-55, service: nil, polarDeviceType: "H10")
    .subscribe(onCompleted: { ... }, onError: { ... })
    .disposed(by: disposeBag)

// AFTER — async throws, returns Void
Task {
    do {
        try await api.startAutoConnectToDevice(-55, service: nil, polarDeviceType: "H10")
    } catch {
        // handle error
    }
}
```

#### `startListenForPolarHrBroadcasts(_:)` — **AsyncThrowingStream**
```swift
// BEFORE (RxSwift)
api.startListenForPolarHrBroadcasts(nil)
    .subscribe(onNext: { data in ... })
    .disposed(by: disposeBag)

// AFTER — AsyncThrowingStream; iterate with for-await, cancel via Task
let task = Task {
    do {
        for try await data in api.startListenForPolarHrBroadcasts(nil) {
            // data: PolarHrBroadcastData
        }
    } catch {
        // handle error
    }
}
// To stop:
task.cancel()
```

#### `setLocalTime`, `getLocalTime`, `getDiskSpace`, `setLedConfig`, and all other one-shot commands
```swift
// BEFORE
api.setLocalTime("deviceId", time: Date(), zone: .current)
    .subscribe(onCompleted: { ... }, onError: { ... })
    .disposed(by: disposeBag)

// AFTER
Task {
    do {
        try await api.setLocalTime("deviceId", time: Date(), zone: .current)
    } catch {
        // handle error
    }
}
```

#### `waitForConnection(_:)`
```swift
// BEFORE
api.waitForConnection("deviceId")
    .subscribe(onCompleted: { ... }, onError: { ... })
    .disposed(by: disposeBag)

// AFTER
Task {
    try await api.waitForConnection("deviceId")
}
```

---

### 3.2 `PolarOnlineStreamingApi` — Streaming

All streaming methods now return `AsyncThrowingStream<T, Error>`. Cancel by calling `.cancel()`
on the `Task` that iterates the stream.

#### `startHrStreaming(_:)`
```swift
// BEFORE
api.startHrStreaming("deviceId")
    .subscribe(onNext: { data in ... }, onError: { error in ... })
    .disposed(by: disposeBag)

// AFTER
let streamTask = Task {
    do {
        for try await data in api.startHrStreaming("deviceId") {
            // data: PolarHrData
        }
    } catch {
        // handle error
    }
}
// To stop streaming:
streamTask.cancel()
```

#### `startEcgStreaming`, `startAccStreaming`, `startGyroStreaming`, `startMagnetometerStreaming`, `startPpgStreaming`, `startPpiStreaming`, `startTemperatureStreaming`, `startPressureStreaming`, `startSkinTemperatureStreaming`
All follow the **identical pattern** as `startHrStreaming` above. Replace the stream type annotation:
- `PolarEcgData`, `PolarAccData`, `PolarGyroData`, `PolarMagnetometerData`, `PolarPpgData`,
  `PolarPpiData`, `PolarTemperatureData`, `PolarPressureData`, `PolarTemperatureData`

#### `requestStreamSettings`, `requestFullStreamSettings`, `getAvailableOnlineStreamDataTypes`, `getAvailableHRServiceDataTypes`
```swift
// AFTER — all are async throws -> value
let settings = try await api.requestStreamSettings("deviceId", feature: .ecg)
let types = try await api.getAvailableOnlineStreamDataTypes("deviceId")
```

---

### 3.3 `PolarOfflineRecordingApi`

#### `listOfflineRecordings(_:)` — AsyncThrowingStream
```swift
// BEFORE
api.listOfflineRecordings("deviceId")
    .subscribe(onNext: { entry in ... }, onCompleted: { ... }, onError: { ... })
    .disposed(by: disposeBag)

// AFTER
Task {
    do {
        for try await entry in api.listOfflineRecordings("deviceId") {
            // entry: PolarOfflineRecordingEntry
        }
    } catch {
        // handle error
    }
}
```

#### `getOfflineRecord`, `startOfflineRecording`, `stopOfflineRecording`, `removeOfflineRecord`, `getOfflineRecordingStatus`, `requestOfflineRecordingSettings`, `requestFullOfflineRecordingSettings`, `getAvailableOfflineRecordingDataTypes`
All are `async throws`. Wrap in `Task` or call from an `async` context:
```swift
let data = try await api.getOfflineRecord("deviceId", entry: entry, secret: nil)
try await api.startOfflineRecording("deviceId", feature: .acc, settings: settings, secret: nil)
```

---

### 3.4 `PolarH10OfflineExerciseApi`

```swift
// BEFORE
api.startRecording("deviceId", exerciseId: "ex1", interval: .interval_1s, sampleType: .hr)
    .subscribe(onCompleted: { ... }, onError: { ... })
    .disposed(by: disposeBag)

// AFTER
try await api.startRecording("deviceId", exerciseId: "ex1", interval: .interval_1s, sampleType: .hr)

// List exercises — AsyncThrowingStream
for try await entry in api.listExercises("deviceId") { ... }

// Fetch — async throws
let data = try await api.fetchExercise("deviceId", entry: entry)
```

---

### 3.5 `PolarTrainingSessionApi`

```swift
// All methods are async throws
let refs = try await api.getTrainingSessionReferences(identifier: "deviceId", fromDate: nil, toDate: nil)
let session = try await api.getTrainingSession(identifier: "deviceId", trainingSessionReference: refs[0])
try await api.deleteTrainingSession(identifier: "deviceId", reference: refs[0])
```

---

### 3.6 `PolarSdkModeApi`

```swift
try await api.enableSDKMode("deviceId")
try await api.disableSDKMode("deviceId")
let isEnabled = try await api.isSDKModeEnabled("deviceId")
```

---

### 3.7 `PolarFirmwareUpdateApi`

```swift
// Firmware update — AsyncThrowingStream of status updates
for try await status in api.updateFirmware("deviceId", fwZipData: data) {
    // status: PolarFirmwareUpdateStatus
}
```

---

### 3.8 Observers — **Unchanged (delegate pattern)**

The following observer protocols are **not changed** and remain delegate-based:
- `PolarBleApiObserver` — `deviceConnecting`, `deviceConnected`, `deviceDisconnected`
- `PolarBleApiPowerStateObserver` — `blePowerOn`, `blePowerOff`
- `PolarBleApiDeviceInfoObserver` — `batteryLevelReceived`, `disInformationReceived`, etc.
- `PolarBleApiDeviceFeaturesObserver` — `bleSdkFeatureReady`, `bleSdkFeaturesReadiness`
- `PolarBleApiDeviceHrObserver` — `hrValueReceived` (deprecated; use `startHrStreaming` instead)

Assign these as before:
```swift
api.observer = self
api.deviceInfoObserver = self
api.deviceFeaturesObserver = self
api.powerStateObserver = self
```

---

## 4. Cancellation Pattern

### Old (RxSwift)
```swift
var disposeBag = DisposeBag()

someObservable
    .subscribe(...)
    .disposed(by: disposeBag)

// Cancel all:
disposeBag = DisposeBag()
```

### New (async streams)
```swift
var streamTasks: [Task<Void, Never>] = []

let task = Task {
    do {
        for try await value in api.startHrStreaming("deviceId") {
            // ...
        }
    } catch { }
}
streamTasks.append(task)

// Cancel all:
streamTasks.forEach { $0.cancel() }
streamTasks.removeAll()
```

### New (Combine publishers)
```swift
var cancellables = Set<AnyCancellable>()

api.startListenForPolarHrBroadcasts(nil)
    .sink(receiveCompletion: { _ in }, receiveValue: { data in ... })
    .store(in: &cancellables)

// Cancel all:
cancellables.removeAll()
```

---

## 5. Error Handling

### Old (RxSwift)
```swift
.subscribe(onError: { error in
    if let polarError = error as? PolarErrors { ... }
})
```

### New
```swift
do {
    try await api.someMethod(...)
} catch let error as PolarErrors {
    // handle typed Polar error
} catch {
    // handle generic error
}
```

For streams:
```swift
do {
    for try await value in api.startHrStreaming("deviceId") { ... }
} catch let error as PolarErrors { ... }
```

---

## 6. Testing Migration

### Remove
```swift
import RxBlocking
import RxTest
```

### Replace RxBlocking waits
```swift
// BEFORE
let result = try! observable.toBlocking().first()

// AFTER — use async XCTest
func testSomething() async throws {
    let result = try await api.someAsyncMethod("deviceId")
    XCTAssertNotNil(result)
}
```

### Replace `TestScheduler` / hot observables
Use `AsyncThrowingStream` with manual `continuation.yield(...)` in test doubles:
```swift
class MockApi {
    var hrContinuation: AsyncThrowingStream<PolarHrData, Error>.Continuation?
    func startHrStreaming(_ identifier: String) -> AsyncThrowingStream<PolarHrData, Error> {
        AsyncThrowingStream { continuation in
            self.hrContinuation = continuation
        }
    }
}
```

---

## 7. Quick Cheat Sheet for AI Agents

When migrating a file, apply the following transformations **in order**:

1. **Remove** `import RxSwift`, `import RxCocoa`, `import RxBlocking`, `import RxTest`.
2. **Add** `import Combine` if the file uses `AnyPublisher` or `AnyCancellable`.
3. **Replace** `DisposeBag` property with `Set<AnyCancellable>` and/or `[Task<Void, Never>]`.
4. **Replace** `.disposed(by: disposeBag)` with `.store(in: &cancellables)` (Combine) or store the `Task`.
5. For each `.subscribe(onNext:onError:onCompleted:)`:
   - If call site is on a **stream method** (returns `AsyncThrowingStream`): wrap in `Task { for try await value in ... { } }`.
   - If call site is on a **Combine method** (returns `AnyPublisher`): use `.sink(receiveCompletion:receiveValue:).store(in:&cancellables)`.
   - If call site is on an **async throws method**: use `Task { try await ... }` or call from an `async` context.
6. For each `.subscribe(onSuccess:onError:)` (Single): use `Task { let result = try await ... }`.
7. For each `.subscribe(onCompleted:onError:)` (Completable): use `Task { try await ... }`.
8. **Replace** `DisposeBag` reset (`disposeBag = DisposeBag()`) with:
   - `cancellables.removeAll()` for Combine.
   - `streamTasks.forEach { $0.cancel() }; streamTasks.removeAll()` for Tasks.
9. Wrap call sites that are not already in an `async` context in `Task { }`.
10. Propagate errors with `do/catch` instead of `onError:` closures.

---

## 8. Which Methods Use Which Pattern

| Return type | How to consume |
|---|---|
| `AsyncThrowingStream<T, Error>` | `for try await value in stream { }` in a `Task` |
| `async throws -> T` | `try await` in a `Task` or `async` func |
| `async throws` (Void) | `try await` in a `Task` or `async` func |
| `AnyPublisher<T, Error>` | `.sink(receiveCompletion:receiveValue:).store(in:&cancellables)` |
| `throws -> T` (sync) | regular `try` call, no Task needed |

### Methods returning `AsyncThrowingStream`
- `searchForDevice()` / `searchForDevice(withRequiredDeviceNamePrefix:)`
- `startListenForPolarHrBroadcasts(_:)`
- `startHrStreaming(_:)`
- `startEcgStreaming(_:settings:)`
- `startAccStreaming(_:settings:)`
- `startGyroStreaming(_:settings:)`
- `startMagnetometerStreaming(_:settings:)`
- `startPpgStreaming(_:settings:)`
- `startPpiStreaming(_:)`
- `startTemperatureStreaming(_:settings:)`
- `startPressureStreaming(_:settings:)`
- `startSkinTemperatureStreaming(_:settings:)`
- `listOfflineRecordings(_:)`
- `listExercises(_:)` (H10)
- `updateFirmware(_:fwZipData:)` (firmware update status stream)

### All other public methods — `async throws`
Everything else in `PolarBleApi`, `PolarOnlineStreamingApi`, `PolarOfflineRecordingApi`,
`PolarH10OfflineExerciseApi`, `PolarTrainingSessionApi`, `PolarSdkModeApi`, etc.

---

## 9. Threading Semantics — Scheduler vs. Swift Concurrency

### What changed

In RxSwift, the **scheduler** attached to an observable chain was the contract for which thread
delivered values to the subscriber:

```swift
api.startHrStreaming("deviceId")
    .observe(on: MainScheduler.instance)   // ← guaranteed main thread delivery
    .subscribe(onNext: { data in
        self.label.text = "\(data)"        // safe: always on main thread
    })
    .disposed(by: disposeBag)
```

The `observeOn` / `subscribeOn` operators let call sites declaratively own the thread without
touching the SDK internals.

In the new Swift Concurrency API there is **no equivalent of `observeOn`**. Values are delivered
on whichever thread or actor the SDK's internal `Task` happens to resume on — typically a
cooperative thread-pool thread, **not** the main thread.

### Required actions when migrating

#### 1. UI updates — switch to `@MainActor`

Any code that updates the UI directly inside a `for try await` loop must hop to the main actor
explicitly. The two idiomatic approaches are:

```swift
// Option A — annotate the enclosing function or type with @MainActor
@MainActor
func startStreaming() {
    Task {
        for try await data in api.startHrStreaming("deviceId") {
            self.label.text = "\(data.first?.hr ?? 0)"   // safe: Task inherits @MainActor
        }
    }
}

// Option B — await MainActor.run inside the loop (useful when only part of the
// work needs the main thread, e.g. heavy parsing on background + UI update)
Task {
    for try await data in api.startHrStreaming("deviceId") {
        let formatted = expensiveFormat(data)             // background thread
        await MainActor.run {
            self.label.text = formatted                   // hops to main thread
        }
    }
}
```

#### 2. Background / serial-queue work — use a custom actor

If the old code used a custom scheduler (e.g. a serial `DispatchQueue`) to serialise state
mutations, replace it with a Swift actor:

```swift
// BEFORE
api.startEcgStreaming("deviceId")
    .observe(on: SerialDispatchQueueScheduler(qos: .userInitiated))
    .subscribe(onNext: { data in processSample(data) })
    .disposed(by: disposeBag)

// AFTER
actor EcgProcessor {
    func process(_ data: PolarEcgData) { /* serial, no data races */ }
}

let processor = EcgProcessor()
Task {
    for try await data in api.startEcgStreaming("deviceId", settings: settings) {
        await processor.process(data)
    }
}
```

#### 3. `hrValueReceived` delegate callback

`PolarBleApiDeviceHrObserver.hrValueReceived(_:data:)` is now called from an internal
`Task` running on the cooperative thread pool. If your observer implementation previously
relied on the RxSwift scheduler to receive this callback on the main thread, you must now
dispatch explicitly:

```swift
// BEFORE — scheduler guaranteed main thread, so this was safe
func hrValueReceived(_ identifier: String, data: PolarHrData) {
    label.text = "\(data.hr)"
}

// AFTER — must dispatch to main thread yourself
func hrValueReceived(_ identifier: String, data: PolarHrData) {
    Task { @MainActor in
        label.text = "\(data.hr)"
    }
}
```

> **Recommendation:** Prefer `startHrStreaming(_:)` over the `hrValueReceived` delegate for
> new code — the streaming API naturally composes with `@MainActor` tasks and makes the
> threading intent explicit.

#### 4. Quick reference

| RxSwift pattern | Swift Concurrency equivalent |
|---|---|
| `.observe(on: MainScheduler.instance)` | `Task { @MainActor in ... }` or `@MainActor func` |
| `.observe(on: SerialDispatchQueueScheduler(...))` | `actor` isolation |
| `.subscribe(on: ConcurrentDispatchQueueScheduler(...))` | default `Task` (already on cooperative pool) |
| `.observe(on: CurrentThreadScheduler.instance)` | no direct equivalent; restructure with actors |
