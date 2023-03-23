# Offline Recording

The offline recording makes it possible to record data into Polar device internal memory. The recording continues even though the BLE connection is lost while recording. 

- [Basic usage](#features)
- [Advanced usage](#advanced-usage)
- [Memory management](#memory-management)
- [Security](#security)
- [Availability](#availability)
- [Considerations](#considerations)

## Basic usage

To enable the offline recording the feature `FEATURE_POLAR_OFFLINE_RECORDING` must be opted in at the time of Polar BLE SDK instantiation. Once the BLE connection is created to the Polar device the `bleSdkFeatureReady` callback is called by the SDK with feature `FEATURE_POLAR_OFFLINE_RECORDING` indicating the offline recording functionality is available in Polar device and it is ready to be used. From this point onwards the offline recording API functions, exposed in the `PolarOfflineRecordingApi` interface, are available and usable. 

To know which data types are supported in the particular Polar device, the `getAvailableOfflineRecordingDataTypes` function can used to check the availability. 

The API `PolarOfflineRecordingApi` has the basic functions to start `startOfflineRecording` and stop `stopOfflineRecording` offline recording. Status query of API function `getOfflineRecordingStatus` indicates if device is already having active recordings on going.

The listing of recordings saved in device memory can be done with `listOfflineRecordings`. The listing returns all the entries (`PolarOfflineRecordingEntry`) in the device or empty list if no recordings found. Using the `PolarOfflineRecordingEntry` it is possible to read the record with the `getOfflineRecord`  and delete it with `removeOfflineRecord`.

To know what are the capabilities of each `PolarDeviceDataType` in offline recording, the settings shall be queried by the `requestOfflineRecordingSettings`. The wanted settings are then provided as parameter for `startOfflineRecording`. 

## Advanced usage 

**SDK Mode**

The offline recording can be used in [SDK mode](SdkModeExplained.md). The SDK mode provides wider range of settings to be used, to know the available settings in SDK mode for offline recording the settings can be queried by the `requestFullOfflineRecordingSettings`. Please note that in SDK Mode, it is not possible to perform operations such as listing offline recordings (`listOfflineRecordings`), reading specific offline recording (`getOfflineRecord`), or deleting offline recordings (`removeOfflineRecord`). 

**Offline recording triggers**

Triggers are the way to automatically start the offline recording. The options are `TRIGGER_SYSTEM_START` and `TRIGGER_EXERCISE_START`. In case the trigger is set to `TRIGGER_SYSTEM_START` the offline recording is started every time the Polar device is switched on, e.g. when VeritySense device power button is pressed by the user to turn device on. In case the trigger is set to `TRIGER_EXERCISE_START` the offline recording is started every time exercise is started in Polar device. With the VeritySense `TRIGER_EXERCISE_START` will trigger offline recording if either the [exercise](https://support.polar.com/en/how-to-use-polar-verity-sense-in-recording-mode) or [swimming](https://support.polar.com/en/how-to-use-polar-verity-sense-in-swimming-mode) mode is started by the user of VeritySense.

The API `setOfflineRecordingTrigger` is used to setup the trigger. When the offline recording is automatically started by the trigger the recording will end in two conditions, either recording is stopped by `stopOfflineRecording` or user switch off the device. To disable the trigger the `setOfflineRecordingTrigger` function is called with the option `TRIGGER_DISABLED`

## Memory management

The device does not automatically erase offline recordings to make space for new ones. If there is less than 2 MB of memory left when a new recording is started, the device will respond with the `DISK_FULL` error when `startOfflineRecording` is subscribed. Similarly, the same 2 MB memory limit prevents the setting of new triggers with `setOfflineRecordingTrigger`.

In addition, please note that the device will automatically stop the offline recording when there is less than 300 kB of free space remaining. At this point, all offline recording triggers will also be disabled.


## Security

The offline record saved in device memory can be encrypted by providing the 128bit AES key as parameter for `startOfflineRecording` request. If providing the key with the  `startOfflineRecording` request, the exact same key must be provided in `getOfflineRecord` to be able to read out the offline record. Each of the started recoding may be started with different key. The key management is not provided by the SDK, but the application developer shall implement the key management. It is recommended the security option is used, otherwise it might be possible by others to read out recordings in VeritySense. 

## Availability

| Device             | Version onwards |
|:-------------------|:---------------:|
| Polar Verity Sense |2.1.0            |


## Considerations

The online streaming and offline recording do not work same time for the same data type. For example if either accelerometer offline recording or the accelerometer online streaming is started, the attempt to start the online streaming or offline recording at this state will return the error ERROR_ALREADY_IN_STATE.

The offline recording read `getOfflineRecord` and delete `removeOfflineRecord` can be called while the offline recording is recording, but that is not recommended. It is recommended to stop the offline recording by `stopOfflineRecording` before trying to access the recording with `getOfflineRecord` or  `removeOfflineRecord` functions. 
