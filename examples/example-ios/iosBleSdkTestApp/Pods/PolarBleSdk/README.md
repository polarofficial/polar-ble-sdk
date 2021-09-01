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
The needed SDK .aar can be found from [polar-sdk-android](polar-sdk-android/libs/)

1.  In `build.gradle` make sure the __minSdkVersion__ is set to __21__ or higher.
```
android {
	...
	defaultConfig {
		...
		minSdkVersion 21
	}
}
```
2.  Copy the contents of [polar-sdk-android](polar-sdk-android/libs) folder into your project's __libs__ folder e.g `YourProjectName/app/libs/`
The `YourProjectName/app/libs/` should now contain the files
```
polar-ble-sdk.aar
```

3. Add the following dependencies to  `build.gradle` inside the dependencies clause:
```
dependencies {
    implementation files('libs/polar-ble-sdk.aar')    
    implementation 'io.reactivex.rxjava3:rxjava:3.0.4'
    implementation 'io.reactivex.rxjava3:rxandroid:3.0.0'
    implementation 'commons-io:commons-io:2.8.0' // Only needed if FEATURE_POLAR_FILE_TRANSFER used
    implementation 'com.google.protobuf:protobuf-javalite:3.14.0' // Only needed if FEATURE_POLAR_FILE_TRANSFER used
}
```
4. Finally, add the following permissions to  `AndroidManifest.xml`:
```
<uses-permission android:name="android.permission.BLUETOOTH"/>
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

## Code example: Heart rate
See the [example](examples/example-android) folder for the full project. 

#### Key things

1. Load the default api implementation and add callback.
```
// NOTICE all features are enabled, if only interested on particular feature(s) like info Heart rate and Battery info then
// e.g. PolarBleApiDefaultImpl.defaultImplementation(this, PolarBleApi.FEATURE_HR |
// PolarBleApi.FEATURE_BATTERY_INFO); 
// batteryLevelReceived callback is invoked after connection
PolarBleApi api = PolarBleApiDefaultImpl.defaultImplementation(this,  PolarBleApi.ALL_FEATURES);

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
2.  Request permissions if needed
```
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    this.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
}
// callback is invoked after granted or denied permissions
@Override
public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
}
```

3. Add background, foreground and cleanup functionality on desired callbacks e.g.
```
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
Detailed documentation: [Documentation](polar-sdk-ios/docs/). Minimum iOS version is 12.
## Requirements
* Xcode 12.x
* Swift 5.x
## Dependencies
*  [RxSwift 6.0](https://github.com/ReactiveX/RxSwift) or above
## Installation
#### CocoaPods

If you use [CocoaPods](https://guides.cocoapods.org/using/using-cocoapods.html) to manage your dependencies, add PolarBleSdk to your `Podfile`:

```
# Podfile

use_frameworks!

target 'YOUR_TARGET_NAME' do
    pod 'PolarBleSdk', '~> 3.2'

end

post_install do |installer|
        installer.pods_project.targets.each do |target|
          target.build_configurations.each do |config|
            config.build_settings['BUILD_LIBRARY_FOR_DISTRIBUTION'] = 'YES'
          end
        end
      end
```

> **Note**:  `post_install` section is required in the `Podfile`.  CocoaPods are not yet working fluently with the XCFrameworks. The progress is followed by [issue 141](https://github.com/polarofficial/polar-ble-sdk/issues/141)

#### Carthage

If you use [Cathage](https://github.com/Carthage/Carthage) to manage your dependencies, add PolarBleSdk to your `Cartfile`

```
github "polarofficial/polar-ble-sdk" ~> 3.2
```

```bash
$ carthage update --use-xcframeworks
```

#### Swift Package Manager
Not supported yet. Reported in [issue 132](https://github.com/polarofficial/polar-ble-sdk/issues/132)

#### XCFrameworks
1. **PolarBLE SDK**: Download the PolarBLE SDK XCFramework from [polar-sdk-ios](polar-sdk-ios/) or from the [releases](https://github.com/polarofficial/polar-ble-sdk/releases). 
2. **RxSwift**: To use PolarBLE SDK XCFramework then you need to use [RxSwift](https://github.com/ReactiveX/RxSwift) added to your project as XCFramework too.

For detailed information how to add XCFramework to XCode project, see the [tutorial](https://developer.apple.com/videos/play/wwdc2019/416/). 

## Setup your application
In project target settings enable __Background Modes__, add  __Uses Bluetooth LE accessories__

## Code example: Heart rate
See the [example](examples/example-ios) folder for the full project

### Key things
`deviceId` is your Polar device's id.
This is not required if you are using automatic connection.

1. Import needed packages.
```
import PolarBleSdk
import RxSwift
```

2. Load the default api implementation and implement desired protocols.
```

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
