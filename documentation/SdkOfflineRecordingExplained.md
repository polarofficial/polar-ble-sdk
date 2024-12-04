[Back to main page](../README.md)

# SDK offline Recording

The SDK offline recording makes it possible to record data into Polar device internal memory. The recording continues even though the BLE connection is lost while recording. 

- [SDK offline Recording](#sdk-offline-recording)
  - [Basic usage](#basic-usage)
  - [Advanced usage](#advanced-usage)
  - [Memory management](#memory-management)
  - [Product specific availability and details](#product-specific-availability-and-details)
  - [Considerations](#considerations)

## Basic usage

To enable the offline recording the feature `FEATURE_POLAR_OFFLINE_RECORDING` must be opted in at the time of Polar BLE SDK instantiation. Once the BLE connection is created to the Polar device the `bleSdkFeatureReady` callback is called by the SDK with feature `FEATURE_POLAR_OFFLINE_RECORDING` indicating the offline recording functionality is available in Polar device and it is ready to be used. From this point onwards the offline recording API functions, exposed in the `PolarOfflineRecordingApi` interface, are available and usable. 

To know which data types are supported in the particular Polar device, the `getAvailableOfflineRecordingDataTypes` function can used to check the availability. 

The API `PolarOfflineRecordingApi` has the basic functions to start `startOfflineRecording` and stop `stopOfflineRecording` offline recording. Status query of API function `getOfflineRecordingStatus` indicates if device is already having active recordings on going.

The listing of recordings saved in device memory can be done with `listOfflineRecordings`. The listing returns all the entries (`PolarOfflineRecordingEntry`) in the device or empty list if no recordings found. Using the `PolarOfflineRecordingEntry` it is possible to read the record with the `getOfflineRecord`  and delete it with `removeOfflineRecord`.

To know what are the capabilities of each `PolarDeviceDataType` in offline recording, the settings shall be queried by the `requestOfflineRecordingSettings`. The wanted settings are then provided as parameter for `startOfflineRecording`. 

## Advanced usage 

**SDK Mode**

The offline recording can be used in [SDK mode](SdkModeExplained.md). The SDK mode provides wider range of settings to be used, to know the available settings in SDK mode for offline recording the settings can be queried by the `requestFullOfflineRecordingSettings`.

> [!NOTE]
> Usually devices don't provide as high sampling rates when doing offline recording as when doing streaming over BLE. The reason is that device internal memory is limited in size and could get filled very quickly, and it's also more power hungry to store data in non-volatile memory instead of streaming it over the BLE radio, which results in much shorter device lifetime.

**Offline recording triggers**

Triggers are the way to automatically start the offline recording. The options are `TRIGGER_SYSTEM_START` and `TRIGGER_EXERCISE_START`. In case the trigger is set to `TRIGGER_SYSTEM_START` the offline recording is started every time the Polar device is switched on, e.g. when VeritySense device power button is pressed by the user to turn device on. In case the trigger is set to `TRIGER_EXERCISE_START` the offline recording is started every time exercise is started in Polar device. 

With the VeritySense `TRIGGER_EXERCISE_START` will trigger offline recording if either the [exercise](https://support.polar.com/en/how-to-use-polar-verity-sense-in-recording-mode) or [swimming](https://support.polar.com/en/how-to-use-polar-verity-sense-in-swimming-mode) mode is started by the user of VeritySense.

The API `setOfflineRecordingTrigger` is used to setup the trigger. When the offline recording is automatically started by the trigger the recording will end in two conditions, either recording is stopped by `stopOfflineRecording` or user switch off the device. To disable the trigger the `setOfflineRecordingTrigger` function is called with the option `TRIGGER_DISABLED`

**Security**

The offline record saved in device memory can be encrypted by providing the 128bit AES key as parameter for `startOfflineRecording` request. If providing the key with the  `startOfflineRecording` request, the exact same key must be provided in `getOfflineRecord` to be able to read out the offline record. Each of the started recoding may be started with different key. The key management is not provided by the SDK, but the application developer shall implement the key management. It is recommended the security option is used, otherwise it might be possible by others to read out recordings in VeritySense. 

## Memory management

The device does not automatically erase offline recordings to make space for new ones. There are 2 different memory limits :

- Memory limit 1 : Once free space is below that limit, attemping to start a recording or enable any trigger will return `ERROR_DISK_FULL` error. 
- Memory limit 2 : Once free space is below that limit, all active offline recordings are automatically stopped and triggered recordings are disabled.

To anticipate this scenario, the disk space can be queried to the device by calling `getDiskSpace` function at any time. 

## Product specific availability and details

| Device             | From ver. onwards | Offline recording triggers | Security | Memory limit 1 | Memory limit 2 |
|:-------------------|:-----------------:|:--------------------------:|:--------:|:--------------:|:--------------:|
| Polar Verity Sense |2.1.0              | Yes                        | Yes      | 2 MB           | 300 KB
| Polar 360          |1.0                | No                         | No       | 2 MB           | 2 MB

## Considerations

The online streaming and offline recording do not work same time for the same data type. For example if either accelerometer offline recording or the accelerometer online streaming is started, the attempt to start the online streaming or offline recording at this state will return the error `ERROR_ALREADY_IN_STATE`.

The offline recording read `getOfflineRecord` and delete `removeOfflineRecord` can be called while the offline recording is recording, but that is not recommended. It is recommended to stop the offline recording by `stopOfflineRecording` before trying to access the recording with `getOfflineRecord` or  `removeOfflineRecord` functions. 
