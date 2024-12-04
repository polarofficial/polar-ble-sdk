[Back to main page](../README.md)

# PolarBleSDK 5.0.0 Migration Guide

PolarBleSDK 5.0.0 is a major release that includes API-breaking changes. This guide aims to make the transition easier for developers who are updating their existing applications from PolarBleSDK 4.x.x to the latest APIs

## New Features
PolarBleSDK 5.0.0 introduces a new feature that enables offline recording functionality in VeritySense. To learn more about offline recording, please see the [documentation](OfflineRecordingExplained.md)  

## Terminology update
In previous versions of the Polar BLE SDK, the core functionality provided online data streams over the BLE connection. This functionality was typically referred to as "streaming" in the PolarBleSdk vocabulary and APIs. With the introduction of new features for recording data to device storage in PolarBleSDK 5.0.0, the terminology has been updated.

The updated terminology now distinguishes between "online streaming" and "offline recording". "Online streaming" refers to the functionality for streaming data in real time over the BLE connection. "Offline recording" refers to the new feature that allows data to be saved directly to the device storage. This change in terminology reflects the new capabilities of the Polar BLE SDK, and helps to clarify the distinction between the two modes of data handling.

## Breaking API Changes Android
- The Polar BLE SDK previously defined feature names such as `FEATURE_HR`, `FEATURE_DEVICE_INFO`, `FEATURE_BATTERY_INFO`, `FEATURE_POLAR_SENSOR_STREAMING`, `FEATURE_POLAR_FILE_TRANSFER`, and `ALL_FEATURES` as constants. In the new version of the SDK, the features are defined in an enum class called `PolarBleSdkFeature`, with feature names that better describe their functionality. This change allows the Polar BLE SDK to optimize resource usage by only enabling features that are needed by the API user. Additionally, the new feature names are not tied to the BLE service names that provide the feature functionality, making them more understandable to the API user. Overall, this change simplifies the use of the Polar BLE SDK for developers who may not have a deep understanding of BLE service names and the related features they provide.

- In the previous version of the Polar BLE SDK, the available data types for online streaming were defined in an enum class called `DeviceStreamingFeature`.  In the new version of the SDK this enum is renamed to `PolarDeviceDataType`, as it now includes data types for both online streaming and offline recording. This change simplifies the naming convention for developers, and makes it easier to understand the different types of data available from the Polar device, both when streaming live data and when recording data for later analysis.

- In the previous version of the Polar BLE SDK, the `backgroundEntered` function was deprecated, and it has now been removed from the API. This function was previously used to detect when the app was sent to the background, but the information is not needed by SDK anymore. 

- In previous versions of the Polar BLE SDK, the callbacks `polarFtpFeatureReady`, `hrFeatureReady`, and `sdkModeFeatureAvailable` were used to determine when certain features were ready for use. In the latest version of the SDK, these callbacks have been deprecated and replaced with a new callback called `bleSdkFeatureReady`. The `bleSdkFeatureReady` callback is now used to indicate when any feature of the Polar BLE SDK is ready for use. When a feature becomes ready, the `bleSdkFeatureReady` callback is called with the `PolarBleSdkFeature` parameter, which indicates the specific feature that is ready for use. nThis change allows for a more efficient and consistent way of handling feature readiness, as all features can now be managed through a single callback. Developers who are updating their apps to the latest version of the Polar BLE SDK should ensure that they are using the new `bleSdkFeatureReady` callback to manage feature readiness.

- In the latest version of the Polar BLE SDK, the `streamingFeaturesReady` callback in `PolarBleApiCallback` has been deprecated. Instead, the new `bleSdkFeatureReady` callback can be used to determine whether a Polar device supports online streaming and if the online streaming feature is ready for use. When the `bleSdkFeatureReady` callback is called with the feature parameter set to `FEATURE_POLAR_ONLINE_STREAMING`, it indicates that the device is ready to stream online data, and the `PolarOnlineStreamingApi` can be used to access the data. The `getAvailableOnlineStreamDataTypes` function in `PolarOnlineStreamingApi` can be used to retrieve a list of available data types that can be streamed from the connected device.

- `PolarHrSample` no longer has a `rrs` property. Instead, it has a `rrsMs` property that represents the R-wave to R-wave intervals in milliseconds. Update any code that uses `rrs` to use `rrsMs` instead. `PolarHrSample` also has a new `rrAvailable` property that indicates whether RR data is available for the sample.

## Deprecated APIs on Android

- `hrNotificationReceived` is deprecated in `PolarBleApiCallback`. The recommended way to receive heart rate is the `startHrStreaming` API, similar to streaming of other data types.

- the `startOhrStreaming` API has been renamed to `startPpgStreaming`. This change was made to better describe the type of data that will be streamed using the API.

- the `startOhrPPIStreaming` API has been renamed to `startPpiStreaming`. This change was made to better describe the type of data that will be streamed using the API.

- the `PolarOhrPPIData` data class has been renamed to `PolarPpiData` in order to provide a more accurate description of the type of data it represents.


## Breaking API Changes iOS
- The SDK features in the Polar BLE SDK were previously defined in an enum class called `Features`. The new version of the SDK defines the features in an enum class called `PolarBleSdkFeature`, with feature names that better describe their functionality. This change allows the Polar BLE SDK to optimize resource usage by only enabling features that are needed by the API user. Additionally, the new feature names are not tied to the BLE service names that provide the feature functionality, making them more understandable to the API user. Overall, this change simplifies the use of the Polar BLE SDK for developers who may not have a deep understanding of BLE service names and the related features they provide.

- In the previous version of the Polar BLE SDK, the available data types for online streaming were defined in an enum class called `DeviceStreamingFeature`. In the new version of the SDK this enum is renamed to `PolarDeviceDataType`, as it now includes data types for both online streaming and offline recording. This change simplifies the naming convention for developers, and makes it easier to understand the different types of data available from the Polar device, both when streaming live data and when recording data for later analysis.

- In previous versions of the Polar BLE SDK, the callbacks `polarFtpFeatureReady`, `hrFeatureReady`, and `sdkModeFeatureAvailable` were used to determine when certain features were ready for use. In the latest version of the SDK, these callbacks have been deprecated and replaced with a new callback called `bleSdkFeatureReady`. The `bleSdkFeatureReady` callback is now used to indicate when any feature of the Polar BLE SDK is ready for use. When a feature becomes ready, the `bleSdkFeatureReady` callback is called with the `PolarBleSdkFeature` parameter, which indicates the specific feature that is ready for use. nThis change allows for a more efficient and consistent way of handling feature readiness, as all features can now be managed through a single callback. Developers who are updating their apps to the latest version of the Polar BLE SDK should ensure that they are using the new `bleSdkFeatureReady` callback to manage feature readiness.

- In the latest version of the Polar BLE SDK, the `streamingFeaturesReady` callback in `PolarBleApiCallback` has been deprecated. Instead, the new `bleSdkFeatureReady` callback can be used to determine whether a Polar device supports online streaming and if the online streaming feature is ready for use. When the `bleSdkFeatureReady` callback is called with the feature parameter set to `FEATURE_POLAR_ONLINE_STREAMING`, it indicates that the device is ready to stream online data, and the `PolarOnlineStreamingApi` can be used to access the data. The `getAvailableOnlineStreamDataTypes` function in `PolarOnlineStreamingApi` can be used to retrieve a list of available data types that can be streamed from the connected device.

- The `PolarHrData` type has been changed from a tuple to an array of tuples. Update any code that uses this type to reflect this change. `PolarHrData` no longer has a `rrs` property. Instead, it has a `rrsMs` property that represents the R-wave to R-wave intervals in milliseconds. The `rrAvailable` property have been added. 

## Deprecated APIs on iOS

- `hrValueReceived` is deprecated in `PolarBleApiDeviceHrObserver`. The recommended way to receive heart rate is the `startHrStreaming` API, similar to streaming of other data types.

- the `startOhrStreaming` API has been renamed to `startPpgStreaming`. This change was made to better describe the type of data that will be streamed using the API.

- the `startOhrPPIStreaming` API has been renamed to `startPpiStreaming`. This change was made to better describe the type of data that will be streamed using the API.
