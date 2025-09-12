[Back to main page](../../README.md)

# Polar 360 & Polar Loop

- [Polar 360 & Polar Loop](#polar-360)
  - [Polar 360 and Polar Loop features available by the SDK](#polar-360-and-polar-loop-features-available-by-the-sdk)
    - [Online streaming and offline recording](#online-streaming-and-offline-recording)
    - [Data export](#data-export)
    - [Device management](#device-management)
    - [Battery management](#battery-management)
    - [PPG description](#ppg-description)
  - [Important considerations](#important-considerations)
    - [First time use](#first-time-use)
    - [BLE functionnality](#ble-functionnality)
    - [Memory management](#memory-management)
  - [SDK Mode available settings](#sdk-mode-available-settings)
  - [Polar 360 and Polar Loop UI animations](#polar-360-and-polar-loop-ui-animations)
    - [Start](#start)
    - [Charging](#charging)
    - [Charging completed](#charging-completed)
    - [Waiting for First time use](#waiting-for-first-time-use)
    - [Firmware update](#firmware-update)

Polar 360 is a new stylish wearable that is designed for individuals but made for business.  It can be customized by companies and integrated into their own applications and solutions.

It is a device designed to increase general well-being and to make the lives of end users healthier and happier. 
[Store page](https://www.polar.com/en/business/polar-360)

POLAR Loop is a screen-free wearable that automatically tracks daily activity, training, sleep, and recovery. For developers, it offers continuous, reliable data and long-term health insights for seamless integration into their own ecosystems.

## Polar 360 and Polar loop features available by the SDK

### Online streaming and offline recording
* Heart rate as beats per minute.
* Acceleration: 50Hz, 16bit resolution, Range 8G
* [PP interval](./../PPIData.md)  representing cardiac pulse-to-pulse interval extracted from PPG signal.
* Skin temperature: 1Hz, 2Hz, 4Hz options, 32 bit resolution
* Photoplethysmograpy (PPG): 22Hz, 24bit resolution, Green channels
* [Offline recording](../SdkOfflineRecordingExplained.md)

### Data export
* Sleep data [iOS](https://polarofficial.github.io/polar-ble-sdk/polar-sdk-ios/Protocols/PolarSleepApi.html), [Android](https://polarofficial.github.io/polar-ble-sdk/polar-sdk-android/com/polar/sdk/api/PolarSleepApi.html)
* Training sessions [iOS](https://polarofficial.github.io/polar-ble-sdk/polar-sdk-ios/Protocols/PolarTrainingSessionApi.html), [Android](https://polarofficial.github.io/polar-ble-sdk/polar-sdk-android/com/polar/sdk/api/PolarTrainingSessionApi.html)
* Activity data [iOS](https://polarofficial.github.io/polar-ble-sdk/polar-sdk-ios/Protocols/PolarActivityApi.html), [Android](https://polarofficial.github.io/polar-ble-sdk/polar-sdk-android/com/polar/sdk/api/PolarActivityApi.html)
  * Steps
  * Active time
  * Calory data (activity/training/BMR)
  * 24/7 HR samples
  * Nightly recharge data
  * Skin temperature data
  * 24/7 PPi samples
  * Activity sample data
      * step count with one minute interval (1440 samples per day)
      * MET samples with 30 sec interval (2880 samples per day)
      * Activity levels

### Device management
* get battery level
* Get/set time
* Get Disc space
* Turn off device
* Restart device
* Factory reset
* [Firmware update](../FirmwareUpdate.md)
* [SDK mode](../SdkModeExplained.md)
* Set physical data (gender,birth date, height, weight, max HR, resting HR, VO2max, training background level, typical daily activity level, sleep goal)
* Delete data from device

### Battery management

Through the SDK, it is possible to retrieve the **Polar 360** battery level and charging status.

The device operates in three battery modes:

| Mode        | Battery Level | Operations                                                                 |
|-------------|----------------|----------------------------------------------------------------------------|
| **Normal**    | 100% – 5%        | All functionalities are available.                                        |
| **Critical**  | 5% – 2%          | All functionalities are disabled. Only BLE remains active to indicate low battery. |
| **Hibernate** | 2% – 0%          | BLE is turned off. The device maintains timekeeping for approximately one week before full discharge. |

> [!NOTE]  
> When Polar 360 is turned off, it enters a mode known as **storage mode**, where the battery circuit is disconnected to prevent discharge.  
> In this mode, **timekeeping is disabled**.  
> It is recommended to **sync with the app and reset the time** when the device is turned back on.

Starting from **firmware version 2.0.8**, Polar 360 can notify changes in charging status.

With **SDK version 6.4.0**, the following charging events are available:
- **CHARGING** → Device is connected to a power source and charging.
- **DISCHARGING ACTIVE** → Device is operating and discharging the battery.


### PPG description
Starting from **firmware version 2.0.8**, Polar 360 supports streaming of raw PPG data.

The PPG signal consists of two channels, both derived from the **central green LED** in the optical interface, sampled  simultaneously from two different photodiodes. 
For this initial version of PPG streaming, only the central green LED is used because it remains active even when the device enters low-power mode.  
This ensures that whether you're streaming or recording PPG data, you’ll consistently receive a signal.

<img src="./../images/Polar360/Central_LEDs.svg" alt="Central LEDs" width="100"/>

> [!IMPORTANT]  
> Firmware version 2.0.8 provides raw data directly from the optics analog front end, without any processing.  
> Due to internal clock drift in the AFE (Analog Front End), you may experience a higher offset in the sampling rate.  
> It is therefore recommended to resample the data before feeding it into your own algorithm.

> [!NOTE]  
> The PPG streaming feature is subject to change without notice in future firmware releases


## Important considerations

### First time use

>[!IMPORTANT]
>Polar 360 as a passive 24/7 wellness tracker relies on user physical data to be able to compute activity and sleep data. It means that it is mandatory to properly set it up using `doFirstTimeUse()` function before it can be used, see [here](./../FirstTimeUse.md) for more details.
When FTU has not been performed and the device isn't being charged, this animation will show up : 

![](./../images/Polar360/Search.gif) 

The following state chart describes how to bring the device into use : 

![](./../images/Polar360/FTU.svg)

### BLE functionnality

Polar 360, as of version 1.1, is designed to be used with 1 peer device only. When the device is in factory defaults state (when leaving factory or explicitely resetting it via either `doFactoryReset()` or by pressing its needle reset button (hidden under the strap) while being connected to charger), it will perform casual advertising that all scanners can see. 

Once pairing is made with a peer device, the latter will be the only device that will get handled by Polar 360 when it receives scan requests or connections request. Device will become invisible to any device that is not the paired device.

>[!IMPORTANT]
>
>If it is needed to pair the Polar 360 to an other peer, it is required to perform factory reset to it so it can accept new connections and pairing requets again.

>[!NOTE]
>
>For security reasons, Polar 360 doesn't accept pairing overwrite. It means that if pairing is removed from the phone settings, trying to re-establish a new pairing from the same phone will get refused by the device as this could open up spoofing attack vector. Device must be put back to factory defaults before it can be paired again.

>[!IMPORTANT]
>
>Pairing on the Polar 360 side is checking for proximity of the phone. Please be within 1 meter of range of your phone when attempting to pair your device to make sure the pairing is accepted by Polar 360.

### Memory management

Polar 360 has 15 MB of space that it can use for storing all kinds of data, whether it is activity, sleep or [SDK offline recordings](./../SdkOfflineRecordingExplained.md). 

Activity and sleep data is passively recorded by the device overtime and will start slowly filling up the memory if the data is never deleted by the SDK.

> [!IMPORTANT]
> It is highly recommended to synchronize the data from Polar 360 and to delete it afterwards on a regular basis to avoid the device running out of memory.

> [!TIP]
> `getDiskSpace()` function can be used at any time to see what is the remaining space inside the device. Default values are bolded.

## SDK Mode available settings

| Data             |Operation mode     | Sampling Rate                         | Range (+-)                                           | Resolution |
|:----------------:|:-----------------:|:-------------------------------------:|:----------------------------------------------------:|:----------:|
| Acc              | Online streaming  | 12Hz, 25Hz, **50Hz**, 100Hz, 200Hz, 400Hz | 2g, 4g, **8g**, 16g                                      |16          |
| Acc              | Offline recording | 12Hz, 26Hz, **52Hz**                      | 2g, 4g, **8g**, 16g                                      |16          |
| PPG              | Online streaming  | **22Hz**, 50Hz, [1] 100Hz                 | -                                                        |24          |
| PPG              | Offline recording | **22Hz**, 50Hz, [1] 100Hz                 | -                                                        |24          |
| Skin temperature | Online streaming  | **1Hz**, 2Hz, 4Hz                         | -                                                    |32          |
| Skin temperature | Offline recording | **1Hz**, 2Hz, 4Hz                         | -                                                    |32          |
| PPI              | PPI online stream or offline recording is not supported in SDK MODE             |
| HR               | HR online stream or offline recording is not supported in SDK MODE              |

[1] Sampling rate of 100Hz in PPG is not confirmed yet (FW. 2.0.8)
   
## Polar 360 and Polar Loop UI animations 

As a headless device, Polar 360 uses the optical heart rate sensor LEDs to communicate information back to the user : 

### Start
![Start](./../images/Polar360/Start.gif#center)

This is the start up animation that happens when the device is turned on after being powered off.

### Charging
![Charging](./../images/Polar360/Charge.gif)

This indicates that the device is charging. Animation will go on until charging is completed.

### Charging completed

![Charging completed](./../images/Polar360/Charging-completed.png)

This indicates that charging is fully completed.

### Waiting for First time use
![Waiting for first time use](./../images/Polar360/Search.gif)

This indicates that the device needs to be setup using `doFirstTimeUse()` function. Note that the device can be setup when it is charging. In that case, it will show the charging (completed) animations defined right above instead.

### Firmware update
![Firmware update](./../images/Polar360/FWU.gif)

This indicates that firmware update process is ongoing


