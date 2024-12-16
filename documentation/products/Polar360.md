[Back to main page](../../README.md)

# Polar 360

- [Polar 360](#polar-360)
  - [Polar 360 features available by the SDK](#polar-360-features-available-by-the-sdk)
  - [Important considerations](#important-considerations)
    - [First time use](#first-time-use)
    - [BLE functionnality](#ble-functionnality)
    - [Memory management](#memory-management)
  - [SDK Mode available settings](#sdk-mode-available-settings)
  - [Polar 360 UI animations](#polar-360-ui-animations)
    - [Start](#start)
    - [Charging](#charging)
    - [Charging completed](#charging-completed)
    - [Waiting for First time use](#waiting-for-first-time-use)
    - [Firmware update](#firmware-update)

Polar 360 is a new stylish wearable that is designed for individuals but made for business.  It can be customized by companies and integrated into their own applications and solutions.

It is a device designed to increase general well-being and to make the lives of end users healthier and happier. 
[Store page](https://www.polar.com/en/business/polar-360)

## Polar 360 features available by the SDK

* Heart rate as beats per minute.
* Acceleration: 50Hz, 16bit resolution, Range 8G
* [PP interval](./../PPIData.md)  representing cardiac pulse-to-pulse interval extracted from PPG signal.
* Skin temperature: 1Hz, 2Hz, 4Hz options, 32 bit resolution
* Battery level
* [SDK mode](../SdkModeExplained.md)
* [Offline recording](../SdkOfflineRecordingExplained.md)
* 24/7 Steps counting
* 24/7 HR samples
* Sleep duration and sleep stages
* Nightly Recharge data (Android)
* Calory data (Android)
* [Firmware update](../FirmwareUpdate.md)
* Setting user's physical info
* Deleting data from device

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
> `getDiskSpace()` function can be used at any time to see what is the remaining space inside the device. 

## SDK Mode available settings

| Data             |Operation mode     | Sampling Rate                         | Range (+-)                                           | Resolution |
|:----------------:|:-----------------:|:-------------------------------------:|:----------------------------------------------------:|:----------:|
| Acc              | Online streaming  | 12Hz, 25Hz, 50Hz, 100Hz, 200Hz, 400Hz | 2g, 4g, 8g, 16g                                      |16          |
| Acc              | Offline recording | 12Hz, 26Hz, 52Hz                      | 2g, 4g, 8g, 16g                                      |16          |
| Skin temperature | Online streaming  | 1Hz, 2Hz, 4Hz                         | -                                                    |32          |
| Skin temperature | Offline recording | 1Hz, 2Hz, 4Hz                         | -                                                    |32          |
| PPI              | PPI online stream or offline recording is not supported in SDK MODE             |
| HR               | HR online stream or offline recording is not supported in SDK MODE              |

## Polar 360 UI animations 

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


