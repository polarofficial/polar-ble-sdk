[Back to main page](../../README.md)

# Polar OH1 Optical heart rate sensor

Optical heart rate sensor is a rechargeable device that measures user’s heart rate with LED technology.
[Store page](https://www.polar.com/us-en/products/accessories/oh1-optical-heart-rate-sensor)

## Polar OH1 features available by the SDK

* Heart rate as beats per minute.
* Heart rate broadcast.
* Photoplethysmograpy (PPG) values.
* [PP interval](./../PPIData.md) (milliseconds) representing cardiac pulse-to-pulse interval extracted from PPG signal.
* Accelerometer data with samplerate of 50Hz and range of 8G. Axis specific acceleration data in mG.

## Important considerations

> [!WARNING]
>
> Polar OH1 PPI algorithm is a separate algorithm than the HR one used when PPI data is not being requested. When PPI recording is enabled, HR is only updated every 5 seconds. Also it takes around 25 seconds for the first sample batch to be sent over BLE for streaming.

> [!IMPORTANT]
>
> Skin contact detection is very unreliable in Polar OH1. Skin contact of PPI packets should not be trusted, and it might be possible for the device to output a heart rate that is not 0 even the device is not worn. That is a limitation of the older generation of optical heart rate solution.