# About iOS Communications

The iOS Communications library provides three functionalities for communication with Polar devices and sensors over Bluetooth LE:
 
* **Functionality 1 - iOS Communications:** This is the base functionality of the communication library, which provides connection and communication with Polar devices and sensors over Bluetooth LE. It is used by many applications developed by Polar. The source code for this functionality can be found in `ios-communications/Sources/iOSCommunications/`.
* **Functionality 2 - Polar BLE SDK:** This functionality provides connection and communication with Polar devices and sensors over Bluetooth LE for 3rd party developers. The functionality is achieved by wrapping the base iOS Communications library with an SDK layer. The source code for this functionality is located in the `ios-communications/Sources/PolarBleSdk/`.
* **Functionality 3 - Polar BLE SDK PROPRIETARY:** The proprietary SDK is intended for internal development at Polar. It is separated into its own branch,  `feature/proprietary-polar-ble-sdk`. The proprietary SDK is useful for experimenting with future features that are not meant to be publicly available or for implementing features that are never intended to be made public.

iOS Communications XCode project (i.e. `iOSCommunications.xcworkspace`) contains three targets `iOSCommunications`, `PolarBleSdk` and `PolarBleSdkWatchOs`. `iOSCommunications` target implements the `Functionality 1`. The targets `PolarBleSdk` and `PolarBleSdkWatchOs` implements the `Functionality 2`, both targets dependents on `iOSCommunications` target.

* [Environment Requirements](#environment-requirements)
* [Dependencies](#dependencies)
    * [To update dependencies](#to-update-dependencies)
* [Install](#install)
    * [... using Swift Package Manager](#...-using-Swift-Package-Manager)
    * [... using CocoaPods](#...-using-CocoaPods)
    * [... using XCFramework](#...-using-XCFramework)
    * [... using git submodules](#...-using-git-submodules)
    * [... using Carthage](#...-using-Carthage)
* [Usage](#usage)
* [Releasing](#releasing)
* [Debugging](#debugging)

## Environment Requirements

* Xcode 13.2 +
* Swift 5.x +

## Dependencies
* iOS Communications project is dependent on following libraries
   * [RxSwift](https://github.com/ReactiveX/RxSwift)
   * [SwiftProtobuf](https://github.com/apple/swift-protobuf). `SwiftProtobuf` dependency is only required by the targets `PolarBleSdk` and `PolarBleSdkWatchOs`
   
### To update dependencies
 * the dependent libraries are referenced by Cocoapods. To update dependencies please modify the `Podfile`
 
## Install 

### ... using Swift Package Manager 
* iOS Communications project is made available via Swift Package. Please get familiar with [XCode help](https://developer.apple.com/documentation/swift_packages/adding_package_dependencies_to_your_app) how to take Swift Package into use on your project.

### ... using CocoaPods
* iOS Communications is shared privately within Polar via [internal podspec repository](https://git.polar.grp/projects/CS/repos/ios-podspecs)
* In your `Podfile` add
```
source 'https://github.com/CocoaPods/Specs.git'
source 'ssh://git@git.polar.grp:7999/cs/ios-podspecs.git'

use_frameworks!

target 'YOUR_TARGET_NAME' do
    pod 'iOSCommunications', '~> 14.1.6'
end
```

And run

```
$ pod install
```

### ... using XCFramework
* From `/scripts` folder run the `./build_ios_communications.sh` or  `./build_sdk.sh` depending on target of your choice. Both scripts builds the XCFrameworks. 
* Copy generated XCFramework from `iOSCommunicationsBuild/`  or from `SdkBuild/` to your project

### ... using git submodules
* `git submodule add ssh://git@git.polar.grp:7999/cs/ios-communications.git`
* Drag and drop the iOS communications project file (i.e. `iOSCommunications.xcodeproj`) to yours Xcode project Project Navigator
*  In your project choose the `Target → General → Frameworks, Libraries and Embedded Content → Press "+"`. From opened dialog select the target `iOSCommunications`, `PolarBleSdk` or `PolarBleSdkWatchOs` depending on your needs.  

### ... using Carthage 
* Not supported at the moment

## Usage
* **RxSwift**: 
    * To use iOS Communications API's you must have [RxSwift](https://github.com/ReactiveX/RxSwift) added to your project. 
* **Permissions**
    * In project target settings enable __Background Modes__, add  __Uses Bluetooth LE accessories__
    * In your project target property list add the key  [NSBluetoothAlwaysUsageDescription](https://developer.apple.com/documentation/bundleresources/information_property_list/nsbluetoothalwaysusagedescription)
    ```xml
    <key>NSBluetoothAlwaysUsageDescription</key>
    <string>Needs BLE permission</string>
    ```

## Releasing 
### ... iOS Communications
* https://wiki.polar.grp/display/B2CMA/iOS+Communications+-+Release+Steps
### ... iOS SDK
* https://wiki.polar.grp/display/B2CMA/iOS+SDK+-+Release+Steps

## Debugging

To debug the iOS communications or Polar BLE SDK target follow the steps provided below:

1. Clone the iOS communications repository (this repository) by running `git clone <
   repository_url>.
2. Follow the steps below depending on which dependency your app uses.

### If you are using CocoaPods as a dependency in your app

#### iOS Communications

Add the following line to your Podfile:

```ruby
target '<target>' do
    ...
    pod 'iOSCommunications', :path => '<relative_path_to_cloned_repo>/ios-communications/'
    ...
end    
```
Then, run:

```bash
    pod update iOSCommunications
```

#### Polar BLE SDK

Add the following line to your Podfile:

```ruby
      pod 'PolarBleSdk', :path => '<relative_path_to_cloned_repo>/ios-communications/'
```

Then, run:

```bash
    pod update PolarBleSdk
```

### If you are using Swift Package Manager as a dependency in your app

1. Open your app’s Xcode project or workspace.

2. Select the Swift package’s folder (i.e. `/ios-communications/`) in Finder and drag it into the Project navigator. This action adds your dependency’s Swift package as a local package to your project.
