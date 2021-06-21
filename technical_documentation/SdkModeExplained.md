# SDK Mode

The SDK mode is the mode of the sensor in which the wider range of stream capabilities are offered, i.e higher sampling rates, wider (or narrow) ranges etc. For example, in the SDK mode the accelerometer sampling rate can be chosen from values 26Hz, 52Hz, 104Hz, 208Hz or 416Hz compared to 52Hz available in normal operation mode. 

[Polar Verity Sense](https://www.polar.com/en/products/accessories/polar-verity-sense) (starting from firmware 1.1.5) is the first sensor to support the SDK Mode. 

***How to use SDK Mode in Polar Verity Sense***
- first of all you need to update the Polar Verity Sense sensor to firmware [1.1.5](https://support.polar.com/en/updates/polar-verity-sense-11-firmware-update). Which can be done by registering the sensor to Polar Flow, see the [help](https://support.polar.com/e_manuals/verity-sense/polar-verity-sense-user-manual-english/firmware-update.htm)
- secondly you will enable or disable SDK Mode using API's provided in Polar BLE SDK library
- once the SDK mode is enabled you may start the data streams as in normal operation mode 

***Good to know about SDK Mode in Polar Verity Sense***
- you may read all the capabilities from the device with the `requestFullStreamSettings` function in any operation mode. However, before starting the stream it is good practice to read currently available settings with `requestStreamSettings` function. 
- if starting many streams on high frequency at the same time, it cannot be guaranteed that all the data is sent over the Bluetooth as traffic may get too high
- when SDK mode is enabled then any existing measurement and stream is closed. In SDK mode the optical sensor is shut down and it is turned on again if the requested stream needs the optical signal. 
- sample code on how to use SDK mode can seen from [Android](../examples/example-android)  and [iOS](../examples/example-ios) examples

***SDK Mode capabilities in Polar Verity Sense***

| Data        | Sampling Rate                  | Range (+-)                                           |    Resolution |
|:------------|:------------------------------:|:----------------------------------------------------:|:----------:|
| Acc         |26Hz, 52Hz, 104Hz, 208Hz, 416Hz | 2g, 4g, 8g, 16g                                      |16          |
| Gyro        |26Hz, 52Hz, 104Hz, 208Hz, 416Hz | 250 deg/sec, 500 deg/sec, 1000 deg/sec, 2000 deg/sec |16          |
| Magnetometer|10Hz, 20Hz, 50Hz, 100Hz         | 50 Gauss                                             |16          |
| PPG        |28Hz, 44Hz, 135Hz, 176Hz        | -                                                    |22          |
| PPI          |Not supported in SDK MODE      | -                                                    |-          |
