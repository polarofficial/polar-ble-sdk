[Back to main page](../README.md)

# Time system in Polar devices

The Polar devices keeps track of the time. The device time is used by the device to timestamp the sample data. Most of the data streams from Polar devices are stamped with the timestamps. The timestamp is added to the data stream when the streamed data is of the type where individual sample is taken at precise time, e.g. ECG, Accelerometer or magnetometer streams. On the other hand, some data streams are the type where time of the individual sample cannot be defined, e.g. PPI or HR. If the sample time of the stream cannot be defined then the sample time is either zero or it is missing from the stream.    

***Epoch time***
- In Polar devices (H10, H9, VeritySense and OH1) the [epoch time](https://en.wikipedia.org/wiki/Epoch_(computing)) is chosen to be 2000-01-01T00:00:00Z UTC. The traditional Unix epoch of 1970-01-01T00:00:00Z wasn't chosen because of memory constraints in earlier Polar sensors, nowadays the memory constraint does not exist, but for the consistency the epoch in Polar sensors is kept in 2000-01-01T00:00:00Z.

***Device time***
- the device time is the time sensor keeps in its memory. The device time is used to timestamp the events, like the data samples in data stream. The sensor time is nanoseconds since epoch 2000-01-01T00:00:00Z.
- device time can be set by the `setLocalTime` API function. Android: [setLocalTime()](https://github.com/polarofficial/polar-ble-sdk/blob/f7cbb003e467a5ccac0a62444b5ee7a97021872f/sources/Android/android-communications/library/src/sdk/java/com/polar/sdk/api/PolarBleApi.kt#L145) iOS: [setLocalTime()](https://github.com/polarofficial/polar-ble-sdk/blob/f7cbb003e467a5ccac0a62444b5ee7a97021872f/sources/iOS/ios-communications/Sources/PolarBleSdk/sdk/api/PolarBleApi.swift#L200)
- device time can be read by the `getLocalTime` API function. Android: [getLocalTime()](https://github.com/polarofficial/polar-ble-sdk/blob/f7cbb003e467a5ccac0a62444b5ee7a97021872f/sources/Android/android-communications/library/src/sdk/java/com/polar/sdk/api/PolarBleApi.kt#L156) iOS: [getLocalTime()](https://github.com/polarofficial/polar-ble-sdk/blob/f7cbb003e467a5ccac0a62444b5ee7a97021872f/sources/iOS/ios-communications/Sources/PolarBleSdk/sdk/api/PolarBleApi.swift#L211)

- if the Sensor time is not set by `setLocalTime` function then sensor is running time starting from default time defined by sensor firmware
    - H10, H9: resets back to default time when the sensor shutdowns. Shutdown of sensor happen within in a minute, if sensor is detach from the strap and it has no bluetooth connection. 
    - OH1, Verity Sense: default time is set only when the device runs out of battery, which might take several weeks when the device is powered off.

When a Polar device completely lose track of time, it's time is reset to a default value which might be different for any product. It is usually good practice to get the time of the device regularly to detect this possible scenario. 

***Timestamp*** 
- the timestamp is the time given for each sample taken by the Polar device. 
- timestamp is represented in nanoseconds since 2000-01-01T00:00:00Z UTC time.
- when sensor time is set to your local time, `setLocalTime` API, then timestamp is nanoseconds to the local time. For example if sensor time is set to 2022-11-12T08:00:00 UTC+02:00 the timestamp is 724060800000000000

***Example***
- shutdown the H10 by removing it from the strap and disconnect possible Bluetooth connections to it. Wait 1 minute. 
    -  the time is set to default 2019-01-01T00:00:00Z, in nanoseconds 599616000000000000
- start ECG stream and observe received timestamps
    -  the timestamp received can be for example 599616023171427290. 
- stop ECG stream
- set the time to the sensor with `setLocalTime` API function, Android: [setLocalTime()](https://github.com/polarofficial/polar-ble-sdk/blob/f9a3912d6e6440cca13fcfbb55d6324e480d4e47/sources/Android/android-communications/library/src/sdk/java/com/polar/sdk/api/PolarBleApi.java#L202) iOS: [setLocalTime()](https://github.com/polarofficial/polar-ble-sdk/blob/a51c5c760d06ccf623a853a3a4150332bf69a7e0/sources/iOS/ios-communications/iOSCommunications/sdk/api/PolarBleApi.swift#L182). For example 2022-01-03T08:10:38 UTC+02:00
- start ECG stream and observe received timestamps
    -   the timestamp received can be for example 694512749912654440.
    
**Some considerations:**
- Note 1: one may use the epoch time to human readable time converters in the code. The conversion algorithms are usually based on the unix epoch of 1970-01-01T00:00:00Z. If that is the case then one shall add the offset between 1970-01-01T00:00:00Z and 2000-01-01T00:00:00Z to timestamp before passing the timestamp to converter. Offset in nanoseconds is 946684800000000000 
- there are known issues related to sensor time, please see them [known issues](https://github.com/polarofficial/polar-ble-sdk/blob/master/documentation/KnownIssues.md)
