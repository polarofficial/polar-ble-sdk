[Back to main page](../../README.md)

# Polar Verity Sense Optical heart rate sensor

Optical heart rate sensor is a rechargeable device that measures user’s heart rate with LED technology.
[Store page](https://www.polar.com/en/products/accessories/polar-verity-sense)

## Polar Verity Sense features available by the SDK

* Heart rate as beats per minute. 
* Heart rate broadcast.
* Photoplethysmograpy (PPG) values.
* [PP interval](./../PPIData.md) (milliseconds) representing cardiac pulse-to-pulse interval extracted from PPG signal.
* Accelerometer data with sample rate of 52Hz and range of 8G. Axis specific acceleration data in mG.
* Gyroscope data with sample rate of 52Hz and ranges of 2000dps. Axis specific gyroscope data in dps.
* Magnetometer data with sample rates of 10Hz, 20Hz, 50HZ and 100Hz and range of +/-50 Gauss. Axis specific magnetometer data in Gauss.
* [SDK mode](../SdkModeExplained.md) from version 1.1.5 onwards.
* [Offline recording](../SdkOfflineRecordingExplained.md) from version 2.1.0 onwards.

## Important considerations

> [!IMPORTANT]
>Any file transfer is **prohibited** when Verity Sense is in internal recording or swimming mode. Attempting to list, fetch or delete any offline recording
will return `SYSTEM_BUSY` error. Device must be in **sensor mode** (the "heart" on the optical leds, blue side LED) to be able to interact with its files. Reason behind this behavior is to prevent attemps to sync a training session while it's still ongoing.

> [!WARNING]
>
> Verity Sense PPI algorithm is a separate algorithm than the HR one used when PPI data is not being requested. When PPI recording is enabled, HR is only updated every 5 seconds. PPI algorithm is also very sensitive to movement, and should only be used at complete rest to output good data. As PPI recording is incompatible with the notion of training, enabling PPI recording **will abort any ongoing training** (internal training or swimming).
>
> If movement is detected, the heart rate is fixed to the last reliable value.
> 
> Also, attempting to set `TRIGER_EXERCISE_START` with PPI measurement type when using the offline recording triggered settings will return `ERROR_NOT_SUPPORTED`

## SDK Mode capabilities in Polar Verity Sense

| Data        |Operation mode     | Sampling Rate                   | Range (+-)                                           | Resolution |
|:-----------:|:-----------------:|:-------------------------------:|:----------------------------------------------------:|:----------:|
| Acc         | Online streaming  | 26Hz, 52Hz, 104Hz, 208Hz, 416Hz | 2g, 4g, 8g, 16g                                      |16          |
| Acc         | Offline recording | 13Hz, 26Hz, 52Hz                | 2g, 4g, 8g, 16g                                      |16          |
| Gyro        | Online streaming  | 26Hz, 52Hz, 104Hz, 208Hz, 416Hz | 250 deg/sec, 500 deg/sec, 1000 deg/sec, 2000 deg/sec |16          |
| Gyro        | Offline recording | 13Hz, 26Hz, 52Hz                | 250 deg/sec, 500 deg/sec, 1000 deg/sec, 2000 deg/sec |16          |
| Magnetometer| Online streaming  | 10Hz, 20Hz, 50Hz, 100Hz         | 50 Gauss                                             |16          |
| Magnetometer| Offline recording | 10Hz, 20Hz, 50Hz                | 50 Gauss                                             |16          |
| PPG         | Online streaming  | 28Hz, 44Hz, 55Hz,  135Hz, 176Hz | -                                                    |22          |
| PPG         | Offline recording | 28Hz, 44Hz, 55Hz                | -                                                    |22          |
| PPI         | PPI online stream or offline recording is not supported in SDK MODE             |
| HR          | HR online stream or offline recording is not supported in SDK MODE              |
