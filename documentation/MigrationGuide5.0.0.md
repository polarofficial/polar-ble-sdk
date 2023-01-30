# PolarBleSDK 5.0.0 Migration Guide
PolarBleSDK 5.0.0 is the major release of PolarBleSDK. As a major release, following Semantic Versioning conventions, 5.0.0 introduces API-breaking changes.

This guide is provided in order to ease the transition of existing applications using PolarBleSDK 4.x.x to the latest APIs.

## New Features
- offline recording API to enable offline recording functionalities in VeritySense. More about offline recording in [documentation](OfflineRecordingExplained.md)  

## Terminology update
- earlier the core functionality of the SDK provided online data streams over the BLE connection. Usually referred as "streaming" in PolarBleSdk vocabulary and APIs. As the PolarBleSDK 5.0.0 now brings the new feature to record data to device storage, the wording used for the "streaming" is now referred as "online streaming" and "offline recording" refers to functionality when the data is saved into the device storage. 

## Breaking API Changes Android
- Earlier the feature names `FEATURE_HR`, `FEATURE_DEVICE_INFO`, `FEATURE_BATTERY_INFO`, `FEATURE_POLAR_SENSOR_STREAMING`, `FEATURE_POLAR_FILE_TRANSFER` and `ALL_FEATURES` were defined as constant. Now the features are defined in enum class `PolarBleSdkFeature`. Furthermore, the feature names are changed to better describe what the each feature offers. The benefit of this change is that Polar BLE SDK can better save resources in case some feature is not needed by the API user. Also the old feature names were tightly coupled with the BLE service names providing the feature functionality, that information is not relevant or not always understandable by API user.

- Earlier the data types available by online streaming were defined in `DeviceStreamingFeature` enum class. Now the enum is renamed as `PolarDeviceDataType` because the enumeration is not only related to online streaming but also to offline recording. 

- `backgroundEntered` was deprecated in SDK release 3.2.8. Now it is removed from the API. 

- `hrNotificationReceived` callback parameter `data: PolarHrData` is replaced with `data: PolarHrData.PolarHrSample` 

## Deprecated APIs on Android
- `streamingFeaturesReady` is deprecated in `PolarBleApiCallback`. Instead the `bleSdkFeatureReady` callback will indicate whether the Polar device is supporting online streaming and online streaming feature is ready. When `bleSdkFeatureReady` callback is called with `feature` parameter  being `FEATURE_POLAR_ONLINE_STREAMING` the `PolarOnlineStreamingApi` can be used. In `PolarOnlineStreamingApi` the `getAvailableOnlineStreamDataTypes` returns data types available in connected device.

- `polarFtpFeatureReady`, `hrFeatureReady`, and `sdkModeFeatureAvailable` are all deprecated, instead use the `bleSdkFeatureReady`. The `bleSdkFeatureReady` is called with the parameter `PolarBleSdkFeature` when the feature is ready. 

- `hrNotificationReceived` is deprecated in `PolarBleApiCallback`. The recommended way to receive heart rate is the `startHrStreaming` API, similar to streaming of other data types.


## Breaking API Changes iOS
- TODO

## Deprecated APIs on iOS
- TODO



