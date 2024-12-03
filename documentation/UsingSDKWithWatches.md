[Back to main page](../README.md)

# Using SDK with Polar watches

Polar watches such as Polar Ignite 3, Polar Pacer (Pro), Polar Grit X Pro 2 or Polar Vantage V3 support online streaming of some data types,
altough some special user action is required to be able to start the streaming feature and get the data.

> [!IMPORTANT]
>
> Although the namings might be confusing, enabling the SDK on the Polar watches is not starting [SDK Mode](./SdkModeExplained.md) with `startSdkMode()` function. SDK Mode is currently not supported in Polar watches.
>
> In Polar watches, it is required to give permission to your watch to be usable with the BLE Polar Measurement Data Service used for data streaming. This is the process of enabling the SDK in the watches. See below for step by step guide.

## Step by step how to

1. Make sure FlowApp is completely shutdown and that your watch is not connected to any other mobile phone.
2. From the watch menu, open `Settings` -> `General settings` -> `Pair and sync` -> `Paired devices`
3. If you see the phone you want to use with the SDK in the list, select it and press `Unpair` -> `Remove pairing` -> `Yes`
4. From the `Pair and sync` view, press `Pair and sync phone`
5. From SDK based application, scan for devices and connect to your Polar watch and accept the pairing if the numerical comparison matches between your watch and your phone. 
6. Once pairing is done, press back button to cancel the `Connecting to phone` animation on the screen.
7. Go back to `Settings` -> `General settings` -> `Pair and sync` -> `Paired devices`, click on your phone in the list
8. Press the `SDK` button and then `Share` button
9. Go back to any watch face, open menu and press `Start training`
10. From any "exercise wait" view (sport profile selection) you can now stream supported data types (acclerometer, PPI, ...) over BLE Polar Measurement Data service.

>[!CAUTION]
> Starting any BLE streaming over the BLE Polar Measurement Data service only possible from the exercise wait view.
>
> Starting a training with PPI streaming ongoing will stop the PPI streaming automatically, as this cannot work during training.