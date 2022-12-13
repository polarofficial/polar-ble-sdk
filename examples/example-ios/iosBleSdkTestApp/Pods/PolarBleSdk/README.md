# SDK for Polar sensors

This is the official repository of Polar's software development kit. With this SDK you are able to develop your own applications for sensors made by Polar.

This SDK uses ReactiveX. You can read more about ReactiveX from their website [reactivex](http://reactivex.io)

3rd party software license listing [ThirdPartySoftwareListing](ThirdPartySoftwareListing.txt)

By exploiting the SDK, you indicate your acceptance of [License](Polar_SDK_License.txt).

If you wish to collaborate with Polar commercially, [click here](https://www.polar.com/en/developers)

### Quick License Summary / Your rights to use the SDK
You may use, copy and modify the SDK as long as you
include the original copyright and license notice in any copy of the
software/source and you comply with the license terms. You are
allowed to use the SDK for the development of software for your
private as well as for commercial use for as long as you use the SDK
in compliance with the license terms.

### H10 Heart rate sensor
Most accurate Heart rate sensor in the markets. The H10 is used in the Getting started section of this page. 
[Store page](https://www.polar.com/en/sensors/h10-heart-rate-sensor)

#### H10 heart rate sensor available data types
* From version 3.0.35 onwards. 
* Heart rate as beats per minute. RR Interval in ms and 1/1024 format.
* Heart rate broadcast.
* Electrocardiography (ECG) data in µV with sample rate 130Hz. Default epoch for timestamp is 1.1.2000
* Accelerometer data with sample rates of 25Hz, 50Hz, 100Hz and 200Hz and range of 2G, 4G and 8G. Axis specific acceleration data in mG. Default epoch for timestamp is 1.1.2000
* Start and stop of internal recording and request for internal recording status. Recording supports RR, HR with one second sampletime or HR with five second sampletime.
* List, read and remove for stored internal recording (sensor supports only one recording at the time).

### H9 Heart rate sensor 
Reliable high quality heart rate chest strap.
[Store page](https://www.polar.com/en/sensors/h9-heart-rate-sensor)

#### H9 heart rate sensor available data types
* Heart rate as beats per minute. RR Interval in ms and 1/1024 format.
* Heart rate broadcast.

### Polar Verity Sense Optical heart rate sensor
Optical heart rate sensor is a rechargeable device that measures user’s heart rate with LED technology.
[Store page](https://www.polar.com/en/products/accessories/polar-verity-sense)

#### Polar Verity Sense Optical heart rate sensor available data types
* Heart rate as beats per minute. 
* Heart rate broadcast.
* Photoplethysmograpy (PPG) values.
* PP interval (milliseconds) representing cardiac pulse-to-pulse interval extracted from PPG signal.
* Accelerometer data with sample rate of 52Hz and range of 8G. Axis specific acceleration data in mG.
* Gyroscope data with sample rate of 52Hz and ranges of 250dps, 500dps, 1000dps and 2000dps. Axis specific gyroscope data in dps.
* Magnetometer data with sample rates of 10Hz, 20Hz, 50HZ and 100Hz and range of +/-50 Gauss. Axis specific magnetometer data in Gauss.
* List, read and remove stored exercise. Recording of exercise requires that sensor is registered to Polar Flow account. Stored sample data contains HR with one second sampletime.
* [SDK mode](technical_documentation/SdkModeExplained.md) (from version 1.1.5 onwards)

### OH1 Optical heart rate sensor
Optical heart rate sensor is a rechargeable device that measures user’s heart rate with LED technology.
[Store page](https://www.polar.com/us-en/products/accessories/oh1-optical-heart-rate-sensor)

#### OH1 Optical heart rate sensor available data types
* From version 2.0.8 onwards.
* Heart rate as beats per minute.
* Heart rate broadcast.
* Photoplethysmograpy (PPG) values.
* PP interval (milliseconds) representing cardiac pulse-to-pulse interval extracted from PPG signal.
* Accelerometer data with samplerate of 50Hz and range of 8G. Axis specific acceleration data in mG.
* List, read and remove stored exercise. Recording of exercise requires that sensor is registered to Polar Flow account. Stored sample data contains HR with one second sampletime. 

### Project structure
* [polar-sdk-ios](polar-sdk-ios/) contains documentation for the iOS SDK source
* [polar-sdk-android](polar-sdk-android/) contains  documentation for the Android SDK source
* [demos](demos/) contains Android ecg demo application 
* [examples](examples/) contains both android and ios example app utilizing most of the features from sdk 
* [technical_documentation](technical_documentation/) contains documentation related to SDK

# Android: Getting started
Detailed documentation: [Documentation]((polar-sdk-android/docs/html/)
## Installation

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

3. Add the dependency Polar BLE SDK library. Also you will need the dependencies to [RxJava](https://github.com/ReactiveX/RxJava) to use the Polar BLE SDK Library
```gradle
dependencies {
    implementation 'com.github.polarofficial:polar-ble-sdk:${sdk_version}'
    implementation 'io.reactivex.rxjava3:rxjava:3.1.3'
    implementation 'io.reactivex.rxjava3:rxandroid:3.0.0'
}
```

4. Finally, to let the SDK use the bluetooth it needs [Bluetooth related permissions](https://developer.android.com/guide/topics/connectivity/bluetooth/permissions). On your application `AndroidManifest.xml` following permissions need to be listed:

```xml
   <!-- Polar SDK needs Bluetooth scan permission to search for BLE devices. Polar BLE SDK doesn't use the scan
    to decide the location so "neverForLocation" permission flag can be used.-->
    <uses-permission
        android:name="android.permission.BLUETOOTH_SCAN"
        android:usesPermissionFlags="neverForLocation" />

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


## Code example: Heart rate
See the [example](examples/example-android) folder for the full project. 

#### Key things

1. Load the default api implementation and add callback.
```kt
// NOTICE in this code snippet all the features are enabled, if only interested on particular feature(s) like Heart rate and Battery info then
// PolarBleApiDefaultImpl.defaultImplementation(this, PolarBleApi.FEATURE_HR or PolarBleApi.FEATURE_BATTERY_INFO) 
// batteryLevelReceived callback is invoked after connection

val api: PolarBleApi = PolarBleApiDefaultImpl.defaultImplementation(applicationContext, PolarBleApi.ALL_FEATURES)

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

    override fun streamingFeaturesReady(identifier: String, features: Set<PolarBleApi.DeviceStreamingFeature>) {
        for (feature in features) {
            Log.d("MyApp", "Streaming feature $feature is ready")
        }
    }
    
    override fun hrFeatureReady(identifier: String) {
        Log.d("MyApp", "HR READY: $identifier")
    }
    
    override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
        Log.d("MyApp", "DIS INFO uuid: $uuid value: $value")
    }

    override fun batteryLevelReceived(identifier: String, level: Int) {
        Log.d("MyApp", "BATTERY LEVEL: $level")
    }

    override fun hrNotificationReceived(identifier: String, data: PolarHrData) {
        Log.d("MyApp", "HR value: ${data.hr} rrsMs: ${data.rrsMs} rr: ${data.rrs} contact: ${data.contactStatus} , ${data.contactStatusSupported}")
    }

    override fun polarFtpFeatureReady(s: String) {
        Log.d("MyApp", "FTP ready")
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

3. Add foreground and cleanup functionality on desired callbacks e.g.
```kt
public override fun onResume() {
    super.onResume()
    api.foregroundEntered()
}

public override fun onDestroy() {
    super.onDestroy()
    api.shutDown()
}
```

4.  Connect to a Polar device using  `api.connectToDevice(<DEVICE_ID>)` where <DEVICE_ID> is the deviceID printed to your sensor,  using  `api.autoConnectToDevice(-50, null, null).subscribe()`  to connect nearby device or  `api.searchForDevice()` to scan and then select the device

# iOS: Getting started
Detailed documentation: [Documentation](polar-sdk-ios/docs/). Minimum iOS version is 13.
## Requirements
* Xcode 12.x
* Swift 5.x
## Dependencies
*  [RxSwift 6.0](https://github.com/ReactiveX/RxSwift) or above
*  [Swift Protobuf 1.18.0](https://github.com/apple/swift-protobuf) or above
## Installation
#### CocoaPods

If you use [CocoaPods](https://guides.cocoapods.org/using/using-cocoapods.html) to manage your dependencies, add PolarBleSdk to your `Podfile`:

```rb
# Podfile

use_frameworks!

target 'YOUR_TARGET_NAME' do
    pod 'PolarBleSdk', '~> 3.3'

end
```

#### Swift Package Manager
Add PolarBleSdk as a dependency to your `Package.swift` manifest

```swift
dependencies: [
    .package(name: "PolarBleSdk", url: "https://github.com/polarofficial/polar-ble-sdk.git", .upToNextMajor(from: "3.3.0"))
]
```
or alternatively use [XCode package manager](https://developer.apple.com/documentation/swift_packages/adding_package_dependencies_to_your_app) to add Swift package to your project. 

#### Carthage
If you use [Cathage](https://github.com/Carthage/Carthage) to manage your dependencies, add PolarBleSdk to your `Cartfile`

```
github "polarofficial/polar-ble-sdk" ~> 3.3
```

```bash
$ carthage update --use-xcframeworks
```

## Setup your application
* In your project target settings enable __Background Modes__, add  __Uses Bluetooth LE accessories__
* In your project target property list add the key  [NSBluetoothAlwaysUsageDescription](https://developer.apple.com/documentation/bundleresources/information_property_list/nsbluetoothalwaysusagedescription)

## Code example: Heart rate
See the [example](examples/example-ios) folder for the full project

### Key things
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
