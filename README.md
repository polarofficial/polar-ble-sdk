# Polar BLE SDK for sensors and watches

Official SDK for Polar sensors and watches on **Android** (minSdk 24) and **iOS** (14.0+). Build apps that connect via Bluetooth LE and stream real-time heart rate, ECG, accelerometer, PPG, and more from Polar devices.

The SDK API uses [ReactiveX](http://reactivex.io) for asynchronous operations.

---

## Contents

- [Supported products](#supported-products)
- [Documentation](#documentation)
  - [API reference](#api-reference)
- [Quickstart](#quickstart)
  - [Android getting started](#android-getting-started)
  - [iOS getting started](#ios-getting-started)
- [Examples and demos](#examples-and-demos)
- [Migration guides](#migration-guides)
- [Troubleshooting and known issues](#troubleshooting-and-known-issues)
- [Collaboration](#collaboration)
- [License](#license)
- [Third-party code and licenses](#third-party-code-and-licenses)

---

### Project structure
```
├── sources/            # SDK source code (Android + iOS)
├── examples/           # Example apps for Android and iOS utilizing most of the features from sdk
├── demos/              # Contains Android ecg demo application
├── documentation/      # SDK documentation and product guides
├── technical_documentation/  # Technical specs and diagrams
└── docs/               # Generated API documentation
```
---
## Supported products

| Sensor/Watch | Documentation |
|--------------|---------------|
| Polar 360 / Polar Loop | [Polar360.md](./documentation/products/Polar360.md) |
| Polar H10 | [PolarH10.md](./documentation/products/PolarH10.md) |
| Polar H9 | [PolarH9.md](./documentation/products/PolarH9.md) |
| Polar Verity Sense | [PolarVeritySense.md](./documentation/products/PolarVeritySense.md) |
| Polar OH1 | [PolarOH1.md](./documentation/products/PolarOH1.md) |
| Polar Ignite 3 | [PolarIgnite3.md](./documentation/products/PolarIgnite3.md) |
| Polar Vantage V3, M3, Grit X2 Pro, Grit X2 | [PolarVantageV3andGritX2Pro.md](./documentation/products/PolarVantageV3andGritX2Pro.md) |
| Polar Pacer / Pacer Pro | [PolarPacerAndPacerPro.md](./documentation/products/PolarPacerAndPacerPro.md) |

For watch-specific guidance, see [Using SDK with Watches](./documentation/UsingSDKWithWatches.md).

---

## Documentation

### API reference
- [Android API docs](https://polarofficial.github.io/polar-ble-sdk/polar-sdk-android/index.html)
- [iOS API docs](https://polarofficial.github.io/polar-ble-sdk/polar-sdk-ios/index.html)

### Guides
| Topic | Link |
|-------|------|
| SDK Mode explained | [SdkModeExplained.md](./documentation/SdkModeExplained.md) |
| Offline recording | [SdkOfflineRecordingExplained.md](./documentation/SdkOfflineRecordingExplained.md) |
| Time system | [TimeSystemExplained.md](./documentation/TimeSystemExplained.md) |
| First time use / device setup | [FirstTimeUse.md](./documentation/FirstTimeUse.md) |
| Firmware updates | [FirmwareUpdate.md](./documentation/FirmwareUpdate.md) |
| PPI data | [PPIData.md](./documentation/PPIData.md) |
| Sync implementation | [SyncImplementationGuideline.md](./documentation/SyncImplementationGuideline.md) |

### Technical documentation
See the [technical_documentation](technical_documentation/) folder for detailed specifications (ECG, online/offline measurement formats).

[↑ Back to contents](#contents)

---

# Quickstart

Get up and running with the Polar BLE SDK.

### Android getting started

### Installation

1.  In `build.gradle` make sure the __minSdk__ is set to __33__ or higher.
```gradle
android {
    ...
    defaultConfig {
        ...
        minSdk 33
    }
}
```

2. Add the JitPack repository to your repositories settings

```gradle
   ...
    repositories {
        ...
        maven { url 'https://jitpack.io' }
        ...
    }
}
```

3. Add the dependency to Polar BLE SDK library. Also you will need the dependencies to [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) to use the Polar BLE SDK Library
```gradle
dependencies {
    implementation 'com.github.polarofficial:polar-ble-sdk:${sdk_version}'
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2"
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-rx3:1.10.2"
}
```


4. Finally, to let the SDK use the bluetooth it needs [Bluetooth related permissions](https://developer.android.com/guide/topics/connectivity/bluetooth/permissions). On your application `AndroidManifest.xml` following permissions need to be listed:

```xml
   <!-- Polar SDK needs Bluetooth scan permission to search for BLE devices. Polar BLE SDK doesn't use the scan
    to decide the location so "neverForLocation" permission flag can be used.-->
    <uses-permission
        android:name="android.permission.BLUETOOTH_SCAN"
        android:usesPermissionFlags="neverForLocation"
        tools:targetApi="s" />

    <!-- Polar SDK needs Bluetooth connect permission to connect for found BLE devices.-->
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

    <!-- Allows Polar SDK to connect to paired bluetooth devices. Legacy Bluetooth permission,
     which is needed on devices with API 30 (Android Q) or older. -->
    <uses-permission
        android:name="android.permission.BLUETOOTH"
        android:maxSdkVersion="30" />

    <!-- Allows Polar SDK to discover and pair bluetooth devices. Legacy Bluetooth permission,
     which is needed on devices with API 30 (Android Q) or older. -->
    <uses-permission
        android:name="android.permission.BLUETOOTH_ADMIN"
        android:maxSdkVersion="30" />

    <!-- Polar SDK needs the fine location permission to get results for Bluetooth scan. Request
    fine location permission on devices with API 30 (Android Q). Note, if your application 
    needs location for other purposes than bluetooth then remove android:maxSdkVersion="30"-->
    <uses-permission
        android:name="android.permission.ACCESS_FINE_LOCATION"
        android:maxSdkVersion="30" />

   <!-- The coarse location permission is needed, if fine location permission is requested. Request
     coarse location permission on devices with API 30 (Android Q). Note, if your application 
    needs location for other purposes than bluetooth then remove android:maxSdkVersion="30" -->
    <uses-permission
        android:name="android.permission.ACCESS_COARSE_LOCATION"
        android:maxSdkVersion="30" />

      <!-- Allow Polar SDK to check and download firmware updates. -->
    <uses-permission android:name="android.permission.INTERNET" />

```

On your application you must request for the [permissions](https://developer.android.com/guide/topics/permissions). Here is the example how could you request the needed permissions for the SDK:

```kt
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), PERMISSION_REQUEST_CODE)
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_CODE)
        }
    } else {
        requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), PERMISSION_REQUEST_CODE)
    }
```


### Code example: Heart rate
See the [example](examples/example-android) folder for the full project. 

#### Key things

1. Load the default api implementation and add callback.
```kt
// NOTICE in this code snippet all the features are enabled. 
// You may enable only the features you are interested
val api: PolarBleApi = PolarBleApiDefaultImpl.defaultImplementation(applicationContext, 
        setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE,
                PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP,
                PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO)
        )
    )
    
api.setApiCallback(object : PolarBleApiCallback() {
   
    override fun blePowerStateChanged(powered: Boolean) {
        Log.d("MyApp", "BLE power: $powered")
    }
    
    override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
        Log.d("MyApp", "CONNECTED: ${polarDeviceInfo.deviceId}")
    }
    
    override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
        Log.d("MyApp", "CONNECTING: ${polarDeviceInfo.deviceId}")
    }
    
    override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
        Log.d("MyApp", "DISCONNECTED: ${polarDeviceInfo.deviceId}")
    }

    override fun bleSdkFeatureReady(identifier: String, feature: PolarBleApi.PolarBleSdkFeature) {
        Log.d(TAG, "Polar BLE SDK feature $feature is ready")
    }
           
    override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
        Log.d("MyApp", "DIS INFO uuid: $uuid value: $value")
    }

    override fun batteryLevelReceived(identifier: String, level: Int) {
        Log.d("MyApp", "BATTERY LEVEL: $level")
    }
})
```
2.  Request permissions
```kt
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), PERMISSION_REQUEST_CODE)
    } else {
        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_CODE)
    }
} else {
    requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), PERMISSION_REQUEST_CODE)
}
  

// callback is invoked after granted or denied permissions
override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
}
```

3. Cleanup functionality when desired, e.g.
```kt
public override fun onDestroy() {
    super.onDestroy()
    api.shutDown()
}
```

4.  Connect to a Polar device using  `api.connectToDevice(<DEVICE_ID>)` where <DEVICE_ID> is the deviceID printed to your sensor,  using  `api.autoConnectToDevice(-50, null, null).subscribe()`  to connect nearby device or  `api.searchForDevice()` to scan and then select the device



**Full example:** [examples/example-android](examples/example-android)

[↑ Back to contents](#contents)


---

### iOS getting started

**Requirements:**
- iOS 14.0+
- Xcode 13.2+
- Swift 5.x

### Dependencies
*  [RxSwift 6.0](https://github.com/ReactiveX/RxSwift) or above
*  [Swift Protobuf 1.18.0](https://github.com/apple/swift-protobuf) or above
### Installation
#### CocoaPods

If you use [CocoaPods](https://guides.cocoapods.org/using/using-cocoapods.html) to manage your dependencies, add PolarBleSdk to your `Podfile`:

```rb
# Podfile

use_frameworks!

target 'YOUR_TARGET_NAME' do
    pod 'PolarBleSdk', '~> 7.0.1'
end
```

#### Swift Package Manager
Add PolarBleSdk as a dependency to your `Package.swift` manifest

```swift
dependencies: [
    .package(name: "PolarBleSdk", url: "https://github.com/polarofficial/polar-ble-sdk.git", .upToNextMajor(from: "7.0.1"))
]
```
or alternatively use [XCode package manager](https://developer.apple.com/documentation/swift_packages/adding_package_dependencies_to_your_app) to add Swift package to your project.

> **Note:** Carthage is not supported.

### Setup your application
* In your project target settings enable __Background Modes__, add  __Uses Bluetooth LE accessories__
* In your project target property list add the key  [NSBluetoothAlwaysUsageDescription](https://developer.apple.com/documentation/bundleresources/information_property_list/nsbluetoothalwaysusagedescription)

### Code example: Heart rate
See the [example](examples/example-ios) folder for the full project

#### Key things
`deviceId` is your Polar device's id.
This is not required if you are using automatic connection.

1. Import needed packages.
```swift
import PolarBleSdk
import RxSwift
```

2. Load the default api implementation and implement desired protocols.
```swift

class MyController: UIViewController,
                    PolarBleApiObserver,
                    PolarBleApiPowerStateObserver,
                    PolarBleApiDeviceInfoObserver,
                    PolarBleApiDeviceFeaturesObserver {
    // NOTICE only FEATURE_HR is enabled, to enable more features add them to the Set
    // e.g. [.feature_hr, .feature_battery_info]
    var api = PolarBleApiDefaultImpl.polarImplementation(DispatchQueue.main, 
                                                          features: [.feature_hr])
    let disposeBag = DisposeBag()
    var deviceId = "0A3BA92B" // TODO replace this with your device id

    override func viewDidLoad() {
        super.viewDidLoad()
        api.observer = self
        api.powerStateObserver = self
        api.deviceFeaturesObserver = self
        api.deviceInfoObserver = self
    }

    func deviceConnecting(_ polarDeviceInfo: PolarDeviceInfo) {
        print("DEVICE CONNECTING: \(polarDeviceInfo)")
    }
    
    func deviceConnected(_ polarDeviceInfo: PolarDeviceInfo) {
        print("DEVICE CONNECTED: \(polarDeviceInfo)")
    }
    
    func deviceDisconnected(_ polarDeviceInfo: PolarDeviceInfo, pairingError: Bool) {
        print("DISCONNECTED: \(polarDeviceInfo)")
    }
    
    func batteryLevelReceived(_ identifier: String, batteryLevel: UInt) {
        print("battery level updated: \(batteryLevel)")
    }
    
    func disInformationReceived(_ identifier: String, uuid: CBUUID, value: String) {
        print("dis info: \(uuid.uuidString) value: \(value)")
    }
    
    func bleSdkFeatureReady(_ identifier: String, feature: PolarBleSdkFeature) {
        print("Feature \(feature) is ready.")
        if feature == .feature_hr {
            // Start HR streaming when feature is ready
            api.startHrStreaming(identifier)
                .observe(on: MainScheduler.instance)
                .subscribe(onNext: { hrData in
                    for sample in hrData.samples {
                        print("HR: \(sample.hr) rrsMs: \(sample.rrsMs)")
                    }
                })
                .disposed(by: disposeBag)
        }
    }
    
    func blePowerOn() {
        print("BLE ON")
    }
    
    func blePowerOff() {
        print("BLE OFF")
    }
        
}
```

3. Connect to a Polar device using  `api.connectToDevice(id)` ,  `api.startAutoConnectToDevice(_ rssi: Int, service: CBUUID?, polarDeviceType: String?)` to connect nearby device or  `api.searchForDevice()` to scan and select the device

**Full example:** [examples/example-ios](examples/example-ios)

[↑ Back to contents](#contents)

---

## Examples and demos

| Folder | Description |
|--------|-------------|
| [examples/example-android](examples/example-android) | Full-featured Android app demonstrating all SDK capabilities |
| [examples/example-ios](examples/example-ios) | Full-featured iOS app demonstrating all SDK capabilities |
| [demos/Android-Demos](demos/Android-Demos) | Android ECG + HR demo application |

[↑ Back to contents](#contents)

---

## Migration guides
- [Polar BLE SDK 5.0.0 Migration Guide](./documentation/MigrationGuide5.0.0.md) – Breaking changes from 4.x to 5.x

[↑ Back to contents](#contents)

---

## Troubleshooting and known issues

See [Known Issues](./documentation/KnownIssues.md) for device-specific issues and workarounds.

Common issues:
- **Connection drops on H10:** Keep the sensor attached to the strap during data transfer. See [KnownIssues.md](./documentation/KnownIssues.md#polar-h10).
- **Incorrect PPG sample rate on Verity Sense:** Fixed in firmware 2.1.0. See [KnownIssues.md](./documentation/KnownIssues.md#polar-verity-sense).

[↑ Back to contents](#contents)

---

## Collaboration

For commercial collaboration with Polar, visit [polar.com/en/business/developers](https://www.polar.com/en/business/developers).

[↑ Back to contents](#contents)

---

## License

### Quick License Summary / Your rights to use the SDK
You may use, copy and modify the SDK as long as you
include the original copyright and license notice in any copy of the
software/source and you comply with the license terms. You are
allowed to use the SDK for the development of software for your
private as well as for commercial use for as long as you use the SDK
in compliance with the license terms.

By exploiting the SDK, you indicate your acceptance of the [License](Polar_SDK_License.txt).

[↑ Back to contents](#contents)

---

## Third-party code and licenses

Third-party code and licenses used in Polar BLE SDK: [ThirdPartySoftwareListing.txt](ThirdPartySoftwareListing.txt)
