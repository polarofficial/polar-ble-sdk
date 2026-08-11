# Polar BLE SDK — App Developer Guide

This guide gives AI coding agents and developers the **stable rules** for building apps on the Polar BLE SDK. Everything that changes between SDK versions — supported devices, data types, dependency versions, minimum platform versions, and the async/reactive paradigm — is **not duplicated here**. Instead it points to the authoritative source-of-truth files in the repository, so it stays correct as the SDK evolves.

> **Principle for agents:** Never assume version-specific details from memory. Read the linked source-of-truth files in the current checkout before generating integration code.

## Platform-specific guides

- [Android — App Developer Notes](sources/Android/AGENTS.md)
- [iOS — App Developer Notes](sources/iOS/AGENTS.md)

## Determine the async paradigm before writing code

The SDK's public API style has changed across major versions, and the correct usage pattern depends on the SDK version the app links against:

- **Android** — migrated from RxJava to **Kotlin Coroutines** (`suspend` functions + `Flow`) in 7.0.0.
- **iOS** — migrated from RxSwift to **Swift Concurrency** (`async`/`await`, `AsyncThrowingStream`) plus **Combine** in 8.0.0.

Before writing or reviewing API calls, **confirm the paradigm for the project's SDK version** by checking, in this order:

1. The latest migration guide(s) under [documentation/](documentation/) (`MigrationGuide*.md`).
2. The public API source the app links against:
   - Android: [sources/Android/android-communications/library/src/sdk/java/com/polar/sdk/api/](sources/Android/android-communications/library/src/sdk/java/com/polar/sdk/api/)
   - iOS: [sources/iOS/ios-communications/Sources/PolarBleSdk/sdk/api/](sources/iOS/ios-communications/Sources/PolarBleSdk/sdk/api/)
3. The declared version and dependencies in the build manifests ([build.gradle](sources/Android/android-communications/library/build.gradle), [PolarBleSdk.podspec](PolarBleSdk.podspec), [Package.swift](Package.swift)).
4. The example apps, which always track the current paradigm: [Android](examples/example-android/), [iOS](examples/example-ios/).

Do not copy API patterns from memory or from older guides without confirming against the current source.

## Integration and setup

Supported platforms, minimum OS/SDK versions, dependency coordinates, installation steps, and required permissions are version-specific. Read them from:

- [README.md](README.md) — Quickstart, installation, dependencies, and required permissions for both platforms.
- Build manifests — the authoritative declared versions and dependencies:
  - Android: [sources/Android/android-communications/library/build.gradle](sources/Android/android-communications/library/build.gradle)
  - iOS: [PolarBleSdk.podspec](PolarBleSdk.podspec), [Package.swift](Package.swift)
- The example apps for a complete, working project setup (manifest, permissions, Gradle/SPM config): [Android](examples/example-android/), [iOS](examples/example-ios/).

> The README is a human overview and may briefly lag during a paradigm migration. When the README and the API source disagree, **the API source and migration guides win.**

## Supported devices and capabilities

Device support and per-device feature availability change as new products ship. Do not hardcode a device list. Read:

- [README.md](README.md) — "Supported products" table.
- [documentation/products/](documentation/products/) — per-device capability guides.
- [documentation/products/polar_device_capabilities.json](documentation/products/polar_device_capabilities.json) — machine-readable device capabilities.

Feature availability varies by device and firmware — **always check at runtime** (see [Feature readiness](#feature-readiness--critical-rule)).

## Available data types and SDK features

The streamable data types and the SDK feature flags are defined in the API enums — read them directly rather than relying on a static table:

- **Feature flags** — `PolarBleApi.PolarBleSdkFeature` (enable only the features your app needs at initialization).
- **Data types** — `PolarBleApi.PolarDeviceDataType`.

Find them in:

- Android: [PolarBleApi.kt](sources/Android/android-communications/library/src/sdk/java/com/polar/sdk/api/PolarBleApi.kt)
- iOS: [PolarBleApi.swift](sources/iOS/ios-communications/Sources/PolarBleSdk/sdk/api/PolarBleApi.swift)

Only declare the features your app actually uses — unused features reserve unnecessary resources.

## Feature readiness — critical rule

**Never call a feature API before the SDK signals that the feature is ready for the target device.**

- Android: wait for the `bleSdkFeatureReady` callback for that feature and device.
- iOS: wait for the device-features observer callback for that feature and device.

Calling a feature API before readiness fails with `PolarServiceNotAvailable` (Android) / `.serviceNotFound` (iOS).

## Device communication best practices

- **One SDK instance per app.** Create a single `PolarBleApi` instance and share it (e.g. via a ViewModel, AppDelegate, or environment object). Multiple instances cause undefined BLE behavior.
- **Do not issue multiple asynchronous SDK calls to the same device in parallel.** Concurrent operations can saturate the BLE link and cause errors or dropped data. Serialise device operations.
- **Wrap device data fetching and manipulation in a sync session.** Within a start/stop sync session, multiple serialised operations are permitted and should be used to manage device data as a single logical session. See the [Sync Implementation Guidelines](documentation/SyncImplementationGuideline.md).
- **Follow the example apps** for tested patterns of common operations: [Android](examples/example-android/), [iOS](examples/example-ios/).

## Error handling

Errors are never silent — they surface through the active paradigm's error channel (a thrown error from a `suspend`/`async` function, the `catch` operator on a `Flow`, the error path of an `AsyncThrowingStream`, or a Combine completion). **Always handle errors** at every call site and stream collection.

The situations below are stable; confirm the exact type names against the error sources in the current checkout:

| Situation | Android exception | iOS `PolarErrors` case |
|---|---|---|
| Feature not yet ready or unsupported by device | `PolarServiceNotAvailable` | `.serviceNotFound` / `.operationNotSupported` |
| Device disconnected mid-operation | `PolarDeviceDisconnected` | `.deviceNotConnected` |
| Operation on a not-connected device | `PolarDeviceNotConnected` | `.deviceNotConnected` |
| Invalid argument (e.g. empty device ID) | `PolarInvalidArgument` | `.invalidArgument` |
| Operation timed out | — | `.timeout` |
| Offline-recording-specific error | `PolarOfflineRecordingError` | `.polarOfflineRecordingError` |
| Internal SDK error | `PolarBleSdkInternalException` | `.polarBleSdkInternalException` |

Full, current definitions:

- Android: [sdk/api/errors/](sources/Android/android-communications/library/src/sdk/java/com/polar/sdk/api/errors/)
- iOS: [PolarErrors.swift](sources/iOS/ios-communications/Sources/PolarBleSdk/sdk/api/errors/PolarErrors.swift)

Error codes from devices: 

[Device Error Codes](documentation/Device%20Error%20Codes%20&amp;%20D2H%20Notifications.md)

## Lifecycle management

- Release the SDK when its owner is destroyed (`api.shutDown()` on Android; clear observers and drop the instance on iOS).
- Cancel every active stream subscription when it is no longer needed, using the active paradigm's cancellation mechanism (cancel the coroutine `Job`/scope, cancel the `Task`, or release the Combine cancellable).

Failing to cancel active subscriptions keeps BLE resources alive and may deliver data after the component is gone. See the platform guides for the exact pattern for the current paradigm.

## Migration guides

When upgrading the SDK, when API names or signatures don't match, or before generating any non-trivial integration code, **read the migration guides** in [documentation/](documentation/). They are written to be consumed directly by AI agents and describe each breaking change with before/after patterns.

The guides currently published (read the [documentation/](documentation/) folder for the full, current set — this list is not exhaustive and newer guides are added over time):

- **[MigrationGuide5.0.0.md](documentation/MigrationGuide5.0.0.md)** — 4.x → 5.x. Feature constants replaced by the `PolarBleSdkFeature` enum; `DeviceStreamingFeature` renamed to `PolarDeviceDataType`; feature-readiness callbacks unified into `bleSdkFeatureReady`; "online streaming" vs "offline recording" terminology introduced; HR `rrs` → `rrsMs`; `startOhrStreaming` / `startOhrPPIStreaming` renamed to `startPpgStreaming` / `startPpiStreaming`.
- **[MigrationGuide7.0.0-Android.md](documentation/MigrationGuide7.0.0-Android.md)** — Android RxJava → Kotlin Coroutines. `Single` / `Completable` become `suspend` functions; `Observable` / `Flowable` become `Flow`; `Disposable` handling becomes coroutine `Job` / scope cancellation; the dependencies and the minimum SDK requirement change (see [build.gradle](sources/Android/android-communications/library/build.gradle)).
- **[MigrationGuide8.0.0-iOS.md](documentation/MigrationGuide8.0.0-iOS.md)** — iOS RxSwift → Swift Concurrency. One-shot calls become `async throws`; infinite streams become `AsyncThrowingStream`; hot/multicast streams become Combine `AnyPublisher`; `DisposeBag` disposal becomes `Task` cancellation; delegate-based observers are unchanged.

Prefer the migration guide that matches the major version the project is moving to, and verify the resulting code against the public API source.

## Resources

- Example apps: [Android](examples/example-android/), [iOS](examples/example-ios/)
- API reference: [Android](https://polarofficial.github.io/polar-ble-sdk/polar-sdk-android/index.html), [iOS](https://polarofficial.github.io/polar-ble-sdk/polar-sdk-ios/index.html)
- Documentation guides: [documentation/](documentation/)
- Known issues: [documentation/KnownIssues.md](documentation/KnownIssues.md)
- Platform notes: [Android](sources/Android/AGENTS.md), [iOS](sources/iOS/AGENTS.md)
