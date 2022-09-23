# SDK for Polar sensors

This is the official repository of Polar's software development kit. With this SDK you are able to develop your own applications for sensors made by Polar.

This SDK uses ReactiveX. You can read more about ReactiveX from their website [reactivex](http://reactivex.io)

3rd party software license listing [ThirdPartySoftwareListing](ThirdPartySoftwareListing.txt)

By exploiting the SDK, you indicate your acceptance of [License](Polar_SDK_License.txt).

If you wish to collaborate with Polar commercially, [click here](https://polar.com/developers)

### Quick License Summary / Your rights to use the SDK
You may use, copy and modify the SDK as long as you
include the original copyright and license notice in any copy of the
software/source and you comply with the license terms. You are
allowed to use the SDK for the development of software for your
private as well as for commercial use for as long as you use the SDK
in compliance with the license terms.

### H10 Heart rate sensor
Most accurate Heart rate sensor in the markets. The H10 is used in the Getting started section of this page. 
[Store page](https://www.polar.com/en/products/accessories/H10_heart_rate_sensor)

#### H10 heart rate sensor available data types
* From version 3.0.35 onwards. 
* Heart rate as beats per minute. RR Interval in ms and 1/1024 format.
* Heart rate broadcast.
* Electrocardiography (ECG) data in µV. Default epoch for timestamp is 1.1.2000
* Accelerometer data with sample rates of 25Hz, 50Hz, 100Hz and 200Hz and range of 2G, 4G and 8G. Axis specific acceleration data in mG. Default epoch for timestamp is 1.1.2000
* Start and stop of internal recording and request for internal recording status. Recording supports RR, HR with one second sampletime or HR with five second sampletime.
* List, read and remove for stored internal recording (sensor supports only one recording at the time).

### H9 Heart rate sensor 
Reliable high quality heart rate chest strap.
[Store page](https://www.polar.com/en/products/accessories/H9_heart_rate_sensor)

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
* [polar-sdk-ios](polar-sdk-ios/) contains compiled iOS sdk, dependencies and documentation
* [polar-sdk-android](polar-sdk-android/) contains compiled Android sdk and documentation
* [demos](demos/) contains Android ecg demo application 
* [examples](examples/) contains both android and ios example app utilizing all features from sdk 
* [gatt specification](technical_documentation/Polar_Measurement_Data_Specification.pdf) contains gatt specification for polar measurement data streaming
* [H10 ecg technical document](technical_documentation/H10_ECG_Explained.docx)

# Android: Getting started
Detailed documentation  [Full Documentation](polar-sdk-android/docs/html/). 
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
```java
// NOTICE all features are enabled, if only interested on particular feature(s) like info Heart rate and Battery info then
// e.g. PolarBleApiDefaultImpl.defaultImplementation(this, PolarBleApi.FEATURE_HR |
// PolarBleApi.FEATURE_BATTERY_INFO); 
// batteryLevelReceived callback is invoked after connection
PolarBleApi api = PolarBleApiDefaultImpl.defaultImplementation(getApplicationContext(),  PolarBleApi.ALL_FEATURES);

api.setApiCallback(new PolarBleApiCallback() {
    @Override
    public void blePowerStateChanged(boolean powered) {
        Log.d("MyApp","BLE power: " + powered);
    }

    @Override
    public void deviceConnected(@NonNull PolarDeviceInfo polarDeviceInfo) {
        Log.d("MyApp","CONNECTED: " + polarDeviceInfo.deviceId);
    }

    @Override
    public void deviceConnecting(@NonNull PolarDeviceInfo polarDeviceInfo) {
        Log.d("MyApp","CONNECTING: " + polarDeviceInfo.deviceId);
    }

    @Override
    public void deviceDisconnected(@NonNull PolarDeviceInfo polarDeviceInfo) {
        Log.d("MyApp","DISCONNECTED: " + polarDeviceInfo.deviceId);
    }

    @Override
    public void streamingFeaturesReady(@NonNull final String identifier,
                                       @NonNull final Set<PolarBleApi.DeviceStreamingFeature> features) {
            for(PolarBleApi.DeviceStreamingFeature feature : features) {
                Log.d("MyApp", "Streaming feature " + feature.toString() + " is ready");
            }
        }

    @Override
    public void hrFeatureReady(@NonNull String identifier) {
        Log.d("MyApp","HR READY: " + identifier);
    }

    @Override
    public void disInformationReceived(@NonNull String identifier, @NonNull UUID uuid, @NonNull String value) {
    }

    @Override
    public void batteryLevelReceived(@NonNull String identifier, int level) {
    }

    @Override
    public void hrNotificationReceived(@NonNull String identifier, @NonNull PolarHrData data) {
        Log.d("MyApp","HR: " + data.hr);
    }

    @Override
    public void polarFtpFeatureReady(@NonNull String s) {
    }
});
```
2.  Request permissions
```java
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), 1)
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }
    }
    requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 1)
}   

// callback is invoked after granted or denied permissions
@Override
public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
}
```

3. Add background, foreground and cleanup functionality on desired callbacks e.g.
```java
@Override
public void onPause() {
    super.onPause();
    api.backgroundEntered();
}

@Override
public void onResume() {
    super.onResume();
    api.foregroundEntered();
}

@Override
public void onDestroy() {
    super.onDestroy();
    api.shutDown();
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
    pod 'PolarBleSdk', '~> 3.2'

end
```

#### Swift Package Manager
Add PolarBleSdk as a dependency to your `Package.swift` manifest

```swift
dependencies: [
    .package(name: "PolarBleSdk", url: "https://github.com/polarofficial/polar-ble-sdk.git", .upToNextMajor(from: "3.2.0"))
]
```
or alternatively use [XCode package manager](https://developer.apple.com/documentation/swift_packages/adding_package_dependencies_to_your_app) to add Swift package to your project. 

#### Carthage
If you use [Cathage](https://github.com/Carthage/Carthage) to manage your dependencies, add PolarBleSdk to your `Cartfile`

```
github "polarofficial/polar-ble-sdk" ~> 3.2
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
    }

    func polarDeviceConnecting(_ polarDeviceInfo: PolarDeviceInfo) {
        print("DEVICE CONNECTING: \(polarDeviceInfo)")
    }
    
    func polarDeviceConnected(_ polarDeviceInfo: PolarDeviceInfo) {
        print("DEVICE CONNECTED: \(polarDeviceInfo)")
        deviceId = polarDeviceInfo.deviceId
    }
    
    func polarDeviceDisconnected(_ polarDeviceInfo: PolarDeviceInfo) {
        print("DISCONNECTED: \(polarDeviceInfo)")
    }
    
    func batteryLevelReceived(_ identifier: String, batteryLevel: UInt) {
        print("battery level updated: \(batteryLevel)")
    }
    
    func hrValueReceived(_ identifier: String, data: PolarHrData) {
        print("HR notification: \(data.hr) rrs: \(data.rrs)")
    }
    
    func hrFeatureReady(_ identifier: String) {
        print("HR READY")
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
        
    func ftpFeatureReady(_ identifier: String) {
    }
}
```

3. Connect to a Polar device using  `api.connectToDevice(id)` ,  `api.startAutoConnectToDevice(_ rssi: Int, service: CBUUID?, polarDeviceType: String?)` to connect nearby device or  `api.searchForDevice()` to scan and select the device
