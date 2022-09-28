# Known issues

#### Polar Verity Sense
- **Firmware:** 1.1.5
- **Feature:** PPG Stream 
- **Problem:** PPG stream settings returns incorrect sampling rate when read with `requestStreamSettings()` function in normal operation mode. The returned value for sampling rate is 135Hz, the returned sampling rate should be 55Hz. The problem is only in Polar Verity Sense firmware v.1.1.5
- **Workaround:** 
    - The PPG stream is working  even the `startOhrStreaming` request is made using the 135Hz as sampleRate parameter in `PolarSensorSetting`. However, the received PPG stream is sampled with 55Hz.
    - If [SDK mode](SdkModeExplained.md) is enabled then PPG settings is read correctly.

#### Polar OH1
- **Firmware:** all firmwares
- **Feature:** PPG Stream 
- **Problem:** PPG stream settings returns incorrect sampling rate when read with `requestStreamSettings()`. The returned value for sampling rate is 130Hz. The correct sampling rate is 135Hz.
- **Workaround:** 
    - The PPG stream is working correctly even the `startOhrStreaming` request is made using the 130Hz as sampleRate parameter in `PolarSensorSetting`. The received PPG stream is sampled with 135Hz.

#### Polar Verity Sense and Polar OH1
- **Firmware:** all firmwares
- **Feature:** time stamp in streams
- **Problem:** When the time is set by the api call `setLocalTime` the time change is not changing the time stamp received by the streams until device is once powered off and powered on
- **Workaround:** 
    - power off and power on the device once since the time set to get correct time stamps

#### Polar Verity Sense
- **Firmware:** all firmwares
- **Feature:** Battery status
- **Problem:** When the Polar Verity Sense is plugged into the charger (i.e. connected with the USB), the battery status is not correctly reported by the Polar Verity Sense. The reported battery level is the battery level at the time charging was started, but even though the charging is progressing the status of the battery level is not updated. 
- **Workaround:** 
    - after unplugging from charger the battery status is correctly reported
