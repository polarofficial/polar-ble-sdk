[Back to main page](../README.md)

# SDK Mode

The SDK mode is the mode of the device in which a wider range of stream capabilities are offered, i.e higher sampling rates, wider (or narrow) ranges etc. 

> [!WARNING]
> When SDK mode is enabled, all algorithms on the device are disabled as they work with only certain device sensor settings. It means that any computed data such as heart rate, PP intervals, RR intervals, etc. is not available anymore. Any passively gathered data such as activity or sleep data will not be gathered anymore as well.

> [!NOTE]
>Enabling the SDK mode shutdowns all sensors (accelerometer, gyroscope, PPG, ...) as they are not needed anymore by the device. These will only be enabled when explictely requested for streaming or offline recording (if supported, see [here](OfflineRecordingExplained)). Using SDK mode could also be a way to extend device lifetime to much longer time for example if only some of the sensors are enabled with low sampling rates.

## How to use SDK Mode

If SDK Mode is not available for your Polar device, attempting to send any of the commands below will result in `ERROR_NOT_SUPPORTED`, otherwise :

- SDK Mode is started by calling the `startSdkMode` function.
- SDK Mode is stopped but turning the device off, or calling the `stopSdkMode` function.
- SDK Mode status can be read from the device at any time by using `getSdkModeStatus`.

## Good to know about SDK Mode

- You may read the capabilities available in SDK from the device with the `requestFullStreamSettings` or `requestFullOfflineRecordingSettings` function in any operation mode.
- If starting many online streams on high frequency at the same time, it cannot be guaranteed that all the data is sent over the Bluetooth as traffic may get too high.
- If the online stream or offline recording is currently running on the device, the SDK Mode cannot be changed. Attempting to change the SDK Mode in will result in an `ERROR_INVALID_STATE` error. Recordings must be stopped beforehand.
- Sample code on how to use SDK mode can seen from [Android](../examples/example-android)  and [iOS](../examples/example-ios) examples

