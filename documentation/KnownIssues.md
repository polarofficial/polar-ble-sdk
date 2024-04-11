# Known issues
## Polar Verity Sense
#### Issue 1
- **Firmware:** Starting from firmware 1.1.5
- **Feature:** PPG Stream 
- **Problem:** PPG stream settings returns incorrect sampling rate when read with `requestStreamSettings()` function in normal operation mode. The returned value for sampling rate is 135Hz, the returned sampling rate should be 55Hz. 
- **Workaround:** 
    - The PPG stream is working  even the `startOhrStreaming` request is made using the 135Hz as sampleRate parameter in `PolarSensorSetting`. However, the received PPG stream is sampled with 55Hz.
    - If [SDK mode](SdkModeExplained.md) is enabled then PPG settings is read correctly.
- **FIX:** FIXED IN VERITY SENSE FIRMWARE 2.1.0      

#### Issue 2
- **Firmware:** Fixed available from 2.2.6
- **Feature:** Battery status
- **Problem:** When the Polar Verity Sense is plugged into the charger (i.e. connected with the USB), the battery status is not correctly reported by the Polar Verity Sense. The reported battery level is the battery level at the time charging was started, but even though the charging is progressing the status of the battery level is not updated. 
- **Workaround:** 
    - after unplugging from charger the battery status is correctly reported

#### Issue 3
- **Firmware:** all firmwares
- **Feature:** time stamp in streams
- **Problem:** When the time is set by the api call `setLocalTime` the time change is not changing the time stamp received by the streams until device is once powered off and powered on
- **Workaround:** 
    - power off and power on the device once since the time set to get correct time stamps

## Polar H10
#### Issue 1
- **Firmware:** all firmwares
- **Feature:** Stored internal recording
- **Problem:** H10 disconnects the BLE connection after 45 seconds timeout when H10 is removed from strap. The BLE disconnect cancels the reading of the internal recording saved to H10 memory. This is problematic in cases when long recording is saved to H10 memory, and reading of it may take tens of seconds.   
- **Workaround:** 
    - keep H10 attached on strap and strap worn by the user
      
#### Issue 2
- **Firmware:** all firmwares
- **Feature:** Terminate data streaming
- **Problem:** Streaming of ECG and ACC data shall always be terminated by the phone application. If this is not done , H10 stays on until battery is removed or empty. This happens also when removing the sensor from the strap.
- **Workaround:** 
    - Make sure to terminate connection by the phone before removing sensor from the strap

## Polar OH1
#### Issue 1
- **Firmware:** all firmwares
- **Feature:** time stamp in streams
- **Problem:** When the time is set by the api call `setLocalTime` the time change is not changing the time stamp received by the streams until device is once powered off and powered on
- **Workaround:** 
    - power off and power on the device once since the time set to get correct time stamps

#### Issue 2
- **Firmware:** all firmwares
- **Feature:** PPG Stream 
- **Problem:** PPG stream settings returns incorrect sampling rate when read with `requestStreamSettings()`. The returned value for sampling rate is 130Hz. The correct sampling rate is 135Hz.
- **Workaround:** 
    - The PPG stream is working correctly even the `startOhrStreaming` request is made using the 130Hz as sampleRate parameter in `PolarSensorSetting`. The received PPG stream is sampled with 135Hz.
