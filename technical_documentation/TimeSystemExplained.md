# Time system in Polar sensors

The Polar sensors keeps track of the time. The sensor time is used by the sensor to time stamp the sample data. Most of the data sample streams from Polar sensors are stamped with the time stamps. The time stamp is added to the data stream when the streamed data is of the type where individual sample is taken at precise time, e.g. ECG, Accelerometer or magnetometer streams. On the other hand, some data streams are the type where time of the individual sample cannot be defined, e.g. PPI. If the sample time of the stream cannot be defined then the sample time is either zero or it is missing from the stream.    

***Epoch time***
- In Polar sensors (H10, H9, VeritySense and OH1) the [epoch time](https://en.wikipedia.org/wiki/Epoch_(computing)) is chosen to be Jan 01 2000 00:00:00. The traditional Unix epoch of Jan 01 1970 00:00:00 wasn't chosen because of memory constraints in earlier Polar sensors, nowadays the memory constraint does not exist, but for the consistency the epoch in Polar sensors is kept in Jan 01 2000 00:00:00

***Sensor time***
- the sensor time is the time sensor keeps in its memory. The sensor time is used to time stamp the events, like the data samples in data stream. The sensor time is nanoseconds since epoch Jan 01 2000 00:00:00
- sensor time can be set by the `setLocalTime` API function. Android: [setLocalTime()](https://github.com/polarofficial/polar-ble-sdk/blob/f9a3912d6e6440cca13fcfbb55d6324e480d4e47/sources/Android/android-communications/library/src/sdk/java/com/polar/sdk/api/PolarBleApi.java#L202) iOS: [setLocalTime()](https://github.com/polarofficial/polar-ble-sdk/blob/a51c5c760d06ccf623a853a3a4150332bf69a7e0/sources/iOS/ios-communications/iOSCommunications/sdk/api/PolarBleApi.swift#L182)
- the sensor time is set back to default time a bit differently depending on the sensor.
    - H10, H9: default time is set when the sensor shutdowns. Shutdown of sensor happen within in a minute, if sensor is detach from the strap and it has no bluetooth connection. 
    - OH1, Verity Sense: default time is set only when the sensor runs out of battery.    

| Device      | Firmware version |Default time               |Default time in nanoseconds since Jan 01 2000 00:00:00 (GMT)|
|:------------|:----------------:|:-------------------------:|:------------------------------------------:|
| H10         |3.1.1             |Jan 01 2019 00:00:00 (GMT) |599616000000000000                          |
| Verity Sense|1.1.5             |Jan 01 2017 10:00:00 (GMT) |536580000000000000                          |
| OH1         |2.1.20            |Jan 01 2017 10:00:00 (GMT) |536580000000000000                          |
 

***Time stamp*** 
- the time stamp is the sampling time of the sample in the data stream. Time stamp is represented in nanoseconds. 

***Example***
- shutdown the H10 by removing it from the strap and disconnect possible Bluetooth connections to it. Wait 1 minute. 
    -  the time is set to default time Jan 01 2019 00:00:00 (GMT), in nanoseconds 599616000000000000
- start ECG stream and observe received timestamps
    -  the timestamp received can be for example 599616023171427290. 
- stop ECG stream
- set the time to the sensor with `setLocalTime` API function, Android: [setLocalTime()](https://github.com/polarofficial/polar-ble-sdk/blob/f9a3912d6e6440cca13fcfbb55d6324e480d4e47/sources/Android/android-communications/library/src/sdk/java/com/polar/sdk/api/PolarBleApi.java#L202) iOS: [setLocalTime()](https://github.com/polarofficial/polar-ble-sdk/blob/a51c5c760d06ccf623a853a3a4150332bf69a7e0/sources/iOS/ios-communications/iOSCommunications/sdk/api/PolarBleApi.swift#L182). For example Mon Jan 03 08:10:38 2022 GMT+02:00
- start ECG stream and observe received timestamps
    -   the timestamp received can be for example 694512749912654440.
    
**Some considerations:**
- one may use the epoch time to human readable time converters in the code. The conversion algorithms are usually based on the unix epoch of Jan 01 1970 00:00:00. If that is the case then one shall add the offset between Jan 01 1970 00:00:00 and Jan 01 2000 00:00:00 to timestamp before passing the timestamp to converter. Offset in nanoseconds is 946684800000000000
- there are known issues related to sensor time, please see them [known issues](https://github.com/polarofficial/polar-ble-sdk/blob/master/technical_documentation/KnownIssues.md) 

