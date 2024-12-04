# SDK for Polar sensors and watches

This is the official repository of Polar's software development kit. With this SDK you are able to develop your own Android and iOS applications for sensors and watches made by Polar.

The SDK API uses ReactiveX. You can read more about ReactiveX from their website [reactivex](http://reactivex.io)

- [SDK for Polar sensors and watches](#sdk-for-polar-sensors-and-watches)
  - [Supported products](#supported-products)
  - [Project structure](#project-structure)
  - [Android getting started](#android-getting-started)
    - [Installation](#installation)
    - [Code example: Heart rate](#code-example-heart-rate)
      - [Key things](#key-things)
  - [iOS getting started](#ios-getting-started)
    - [Requirements](#requirements)
    - [Dependencies](#dependencies)
    - [Installation](#installation-1)
      - [CocoaPods](#cocoapods)
      - [Swift Package Manager](#swift-package-manager)
      - [Carthage](#carthage)
    - [Setup your application](#setup-your-application)
    - [Code example: Heart rate](#code-example-heart-rate-1)
      - [Key things](#key-things-1)
  - [Migration guides](#migration-guides)
  - [Collaboration](#collaboration)
  - [License](#license)
    - [Quick License Summary / Your rights to use the SDK](#quick-license-summary--your-rights-to-use-the-sdk)
  - [Third-party code and licenses](#third-party-code-and-licenses)

## Supported products

- [Polar 360](./documentation/products/Polar360.md)
- [Polar H10](./documentation/products/PolarH10.md)
- [Polar H9](./documentation/products/PolarH9.md)
- [Polar Verity Sense](./documentation/products/PolarVeritySense.md)
- [Polar OH1](./documentation/products/PolarOH1.md)
- [Polar Ignite 3](./documentation/products/PolarIgnite3.md)
- [Polar Vantage V3 and Polar Grit X2 Pro](./documentation/products/PolarVantageV3andGritX2Pro.md)
- [Polar Pacer and Polar Pacer Pro](./documentation/products/PolarPacerAndPacerPro.md)

## Project structure
* [polar-sdk-ios](polar-sdk-ios/) contains source documentation for the iOS SDK source
* [polar-sdk-android](polar-sdk-android/) contains source documentation for the Android SDK source
* [demos](demos/) contains Android ecg demo application 
* [examples](examples/) contains both android and ios example app utilizing most of the features from sdk 
* [documentation](documentation/) contains documentation related to SDK

## Android getting started
Detailed documentation: [Documentation](polar-sdk-android/docs/)
### Installation

1.  In `build.gradle` make sure the __minSdk__ is set to __24__ or higher.
```gradle
android {
    ...
    defaultConfig {
        ...
        minSdk 24
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

3. Add the dependency to Polar BLE SDK library. Also you will need the dependencies to [RxJava](https://github.com/ReactiveX/RxJava) to use the Polar BLE SDK Library
```gradle
dependencies {
    implementation 'com.github.polarofficial:polar-ble-sdk:${sdk_version}'
    implementation 'io.reactivex.rxjava3:rxjava:3.1.6'
    implementation 'io.reactivex.rxjava3:rxandroid:3.0.2'
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

## iOS getting started
Detailed documentation: [Documentation](polar-sdk-ios/docs/). Minimum iOS version is 14.
### Requirements
* Xcode 12.x
* Swift 5.x
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
    pod 'PolarBleSdk', '~> 5.0'

end
```

#### Swift Package Manager
Add PolarBleSdk as a dependency to your `Package.swift` manifest

```swift
dependencies: [
    .package(name: "PolarBleSdk", url: "https://github.com/polarofficial/polar-ble-sdk.git", .upToNextMajor(from: "5.0.0"))
]
```
or alternatively use [XCode package manager](https://developer.apple.com/documentation/swift_packages/adding_package_dependencies_to_your_app) to add Swift package to your project. 

#### Carthage
If you use [Cathage](https://github.com/Carthage/Carthage) to manage your dependencies, add PolarBleSdk to your `Cartfile`

```
github "polarofficial/polar-ble-sdk" ~> 5.0
```

```bash
$ carthage update --use-xcframeworks
```

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
                    PolarBleApiDeviceFeaturesObserver,
                    PolarBleApiDeviceHrObserver {
    // NOTICE only FEATURE_HR is enabled, to enable more features like battery info
    // e.g. PolarBleApiDefaultImpl.polarImplementation(DispatchQueue.main, features: Features.hr.rawValue | 
    // Features.batteryStatus.rawValue)
    // batteryLevelReceived callback is invoked after connection                   
    var api = PolarBleApiDefaultImpl.polarImplementation(DispatchQueue.main, features: Features.hr.rawValue)
    var deviceId = "0A3BA92B" // TODO replace this with your device id

    override func viewDidLoad() {
        super.viewDidLoad()
        api.observer = self
        api.deviceHrObserver = self
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
    
    func deviceDisconnected(_ polarDeviceInfo: PolarDeviceInfo) {
        print("DISCONNECTED: \(polarDeviceInfo)")
    }
    
    func batteryLevelReceived(_ identifier: String, batteryLevel: UInt) {
        print("battery level updated: \(batteryLevel)")
    }
    
    func disInformationReceived(_ identifier: String, uuid: CBUUID, value: String) {
        print("dis info: \(uuid.uuidString) value: \(value)")
    }
    
    func hrValueReceived(_ identifier: String, data: PolarHrData) {
        print("HR notification: \(data.hr) rrs: \(data.rrs)")
    }
        
    func hrFeatureReady(_ identifier: String) {
        print("HR READY")
    }
    
    func ftpFeatureReady(_ identifier: String) {
        print("FTP ready")
    }
    
    func streamingFeaturesReady(_ identifier: String, streamingFeatures: Set<DeviceStreamingFeature>) {
        for feature in streamingFeatures {
            print("Feature \(feature) is ready.")
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

## Migration guides
- [Polar BLE SDK 5.0.0 Migration Guide](documentation/MigrationGuide5.0.0.md)

## Collaboration
If you wish to collaborate with Polar commercially, [click here](https://www.polar.com/en/business/developers)

## License

### Quick License Summary / Your rights to use the SDK
You may use, copy and modify the SDK as long as you
include the original copyright and license notice in any copy of the
software/source and you comply with the license terms. You are
allowed to use the SDK for the development of software for your
private as well as for commercial use for as long as you use the SDK
in compliance with the license terms.

By exploiting the SDK, you indicate your acceptance of [License](Polar_SDK_License.txt).

## Third-party code and licenses
Third-party code and licenses used in Polar BLE SDK see license listing [ThirdPartySoftwareListing](ThirdPartySoftwareListing.txt)
