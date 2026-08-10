# Polar BLE SDK — Device Error Codes & D2H Notifications

This document describes all error codes that Polar360 can send to the host application over BLE. Use these codes to diagnose issues in your SDK integration and to provide appropriate feedback to end-users.

---

## Table of Contents

- [Polar BLE SDK — Device Error Codes \& D2H Notifications](#polar-ble-sdk--device-error-codes--d2h-notifications)
  - [Table of Contents](#table-of-contents)
  - [Polar Measurement Data Service (PMDS)](#polar-measurement-data-service-pmds)
  - [Polar File Transfer Protocol (PFTP)](#polar-file-transfer-protocol-pftp)
    - [Success and Transient Codes (0–2)](#success-and-transient-codes-02)
    - [Host-originated Errors (100–108)](#host-originated-errors-100108)
    - [Device-originated Errors (200–208)](#device-originated-errors-200208)
  - [Device-to-Host (D2H) Notifications](#device-to-host-d2h-notifications)

---

## Polar Measurement Data Service (PMDS)

These error codes are returned in the **PMD Control Point response** (characteristic `0xFB01`), in the `error_code` field of the response packet. They are produced whenever a command written to the Control Point cannot be fulfilled.

| Error Code | Value | Error Name | Description | Hint |
|:---:|:---:|:---|:---|:---|
| `0x00` | 0 | `PMD_ERROR_SUCCESS` | The command was processed successfully. No error occurred. | — |
| `0x01` | 1 | `PMD_ERROR_INVALID_OP_CODE` | The command sent to the device is not a recognised control-point operation. | — |
| `0x02` | 2 | `PMD_ERROR_INVALID_MEASUREMENT_TYPE` | The requested measurement type is out of range or cannot be directly started. | Call `getAvailableOnlineStreamDataTypes()` (online) or `getAvailableOfflineRecordingDataTypes()` (offline) first to retrieve the measurement types supported by this device. |
| `0x03` | 3 | `PMD_ERROR_NOT_SUPPORTED` | The requested operation or measurement type is not supported by this device, or offline recording/SDK mode is not available. | Call `getAvailableOnlineStreamDataTypes()` or `getAvailableOfflineRecordingDataTypes()` to confirm which features are available before starting. |
| `0x04` | 4 | `PMD_ERROR_INVALID_LENGTH` | The command packet length does not match the expected length for the given op-code. | — |
| `0x05` | 5 | `PMD_ERROR_INVALID_PARAMETER` | One or more measurement settings in the request are invalid or not recognised by the device. | Construct the settings using the values returned by `requestStreamSettings()` or `requestOfflineRecordingSettings()` rather than using hard-coded values. |
| `0x06` | 6 | `PMD_ERROR_ALREADY_IN_STATE` | The requested measurement or SDK mode is already active. Trying to start a measurement that is already running, or activating SDK mode when it is already on, returns this error. | Call `stopStreaming()` (online) or `stopOfflineRecording()` (offline) to stop the active measurement before starting a new one. |
| `0x07` | 7 | `PMD_ERROR_INVALID_RESOLUTION` | The resolution value in the start-measurement settings is not among the valid options for this measurement type in the current device state. | Call `requestStreamSettings()` (online) or `requestOfflineRecordingSettings()` (offline) to retrieve the currently available resolutions, and select one of them before starting. |
| `0x08` | 8 | `PMD_ERROR_INVALID_SAMPLE_RATE` | The sample rate value in the start-measurement settings is not among the valid options for this measurement type in the current device state. | Call `requestStreamSettings()` (online) or `requestOfflineRecordingSettings()` (offline) to retrieve the currently available sample rates, and select one of them before starting. |
| `0x09` | 9 | `PMD_ERROR_INVALID_RANGE` | The range value in the start-measurement settings is not among the valid options for this measurement type in the current device state. | Call `requestStreamSettings()` (online) or `requestOfflineRecordingSettings()` (offline) to retrieve the currently available ranges, and select one of them before starting. |
| `0x0A` | 10 | `PMD_ERROR_INVALID_MTU` | The BLE ATT MTU negotiated for this connection is smaller than the minimum required by the device for online streaming. Some devices require a minimum MTU (typically 64 bytes or more) to deliver measurement data packets reliably. This check applies only to online streaming; offline recording is not affected. | The SDK cannot directly control BLE MTU negotiation — this is handled automatically by the OS and BLE communication libraries. If you consistently encounter this error, please open an issue on the [Polar BLE SDK GitHub repository](https://github.com/polarofficial/polar-ble-sdk/issues) with details of your OS, BLE stack version, and the device you are connecting to. |
| `0x0B` | 11 | `PMD_ERROR_INVALID_NUMBER_OF_CHANNELS` | The number of channels specified in the start-measurement settings is not among the valid options for this measurement type. | Call `requestStreamSettings()` (online) or `requestOfflineRecordingSettings()` (offline) to retrieve the available channel counts, and select a valid value. |
| `0x0C` | 12 | `PMD_ERROR_INVALID_STATE` | The device is in a state that does not allow the requested operation. This can occur when: the device is not yet ready to accept commands; PPI or offline HR measurement is requested while SDK mode is active; SDK mode is requested while measurements are active; an offline recording start was attempted while another operation was in progress; **or first-time-use (FTU) setup has not been completed on the device**. | Ensure FTU has been completed (`doFirstTimeUse()`), the device is fully connected, and no conflicting modes are active. If SDK mode is on and you need PPI/HR offline data, disable SDK mode first. |
| `0x0D` | 13 | `PMD_ERROR_DEVICE_IN_CHARGER` | The device is currently connected to a USB charger. Certain measurements cannot be started while the device is charging. | Disconnect the charger and retry. |
| `0x0E` | 14 | `PMD_ERROR_DISK_FULL` | Offline recording cannot be started because the device's storage is full; there is no space to write new data. | Call `getDiskSpace()` to check available space, and remove old recordings with `removeOfflineRecord()` to free up storage before starting an offline recording. |
| `0x0F` | 15 | `PMD_ERROR_INVALID_DERIVED_MEASUREMENT_METHOD` | The derived measurement method (computation algorithm) specified in the start-measurement request is not recognised by the device. | Call 'requestDerivedMeasurementGroupIds() with source type to retrieve available derived measurement settings group identifiers for a source measurement type. Call 'requestDerivedMeasurementSettingsGroup() with valid group identifier to retrieve actual supported derived measurement settings, including the derived measurement method.  Call 'startDerivedOfflineRecording() for derived measurement offline recording with valid returned derived measurement settings. |
| `0x10` | 16 | `PMD_ERROR_INVALID_SOURCE_MEASUREMENT_TYPE` | The source measurement type specified for the derived measurement is incompatible: the requested derivation cannot be applied to that source type. | Check which source measurement types are compatible with the intended derived measurement by querying the available settings with `requestStreamSettings()` (online) or `requestOfflineRecordingSettings()` (offline), and select a source type from those results. |
| `0x11` | 17 | `PMD_ERROR_INVALID_SOURCE_MEASUREMENT_RATE` | The sample rate specified for the source measurement in the derived measurement request is not supported by the device in the current state. | Call `requestStreamSettings()` (online) or `requestOfflineRecordingSettings()` (offline) to retrieve the available sample rates for the source measurement type, and select one of the returned values before starting. |
| `0x12` | 18 | `PMD_ERROR_INVALID_DERIVED_MEASUREMENT_SETTINGS_GROUP` | The settings group ID in the start-measurement or Get Derived Measurement Settings Group request is not recognised or not supported by the device. | Query the valid settings groups by calling `requestStreamSettings()` (online) or `requestOfflineRecordingSettings()` (offline) and use only the group IDs returned. |

---

## Polar File Transfer Protocol (PFTP)

These error codes are transmitted as part of the **Polar File Transfer Protocol** (PFTP), used for file operations such as reading activity data, pushing configuration files, or synchronising recordings with the host application. They appear in PFTP response packets as a two-byte little-endian field.

### Success and Transient Codes (0–2)

| Error Code | Value | Error Name | Description | Hint |
|:---:|:---:|:---|:---|:---|
| `0` | 0 | `RFC9_ERROR_CODE_OPERATION_SUCCEEDED` | The file operation completed successfully. | — |
| `1` | 1 | `RFC9_ERROR_CODE_REBOOTING` | The device is currently rebooting. The operation was not completed. | Wait for the device to reconnect after the reboot and retry. |
| `2` | 2 | `RFC9_ERROR_CODE_TRY_AGAIN` | The operation could not be completed right now. The device is temporarily unable to fulfil the request. | Retry the operation after a short delay. |

### Host-originated Errors (100–108)

These codes indicate that the error was caused by an invalid or unexpected command from the host.

| Error Code | Value | Error Name | Description | Hint |
|:---:|:---:|:---|:---|:---|
| `100` | 100 | `RFC9_ERROR_CODE_UNIDENTIFIED_HOST_ERROR` | An unclassified error was caused by the host. | Review the command sequence and ensure the protocol is implemented correctly. |
| `101` | 101 | `RFC9_ERROR_CODE_INVALID_COMMAND` | The command identifier sent by the host is not valid or not recognised by the device. | — |
| `102` | 102 | `RFC9_ERROR_CODE_INVALID_PARAMETER` | A parameter within the command (e.g., a file path or flag) is invalid. | Check the parameter values and ensure they conform to the PFTP protocol specification. |
| `103` | 103 | `RFC9_ERROR_CODE_NO_SUCH_FILE_OR_DIRECTORY` | The file or directory specified in the command does not exist on the device. | Verify the path by listing the directory contents before attempting to open or read a file. |
| `104` | 104 | `RFC9_ERROR_CODE_DIRECTORY_EXISTS` | A directory with the specified name already exists. The device refused to create a duplicate. *Not sent by Polar360* — the current filesystem implementation does not return this code. | — |
| `105` | 105 | `RFC9_ERROR_CODE_FILE_EXISTS` | A file with the specified name already exists. The operation was refused to avoid overwriting. *Not sent by Polar360* — the current filesystem implementation does not return this code. | — |
| `106` | 106 | `RFC9_ERROR_CODE_OPERATION_NOT_PERMITTED` | The requested file operation is not permitted, e.g., due to access rights on a read-only system path. | Do not attempt to write to read-only device paths. |
| `107` | 107 | `RFC9_ERROR_CODE_NO_SUCH_USER` | The specified user does not exist on the device. This error code is defined in the protocol specification but *is never sent by Polar360*. | — |
| `108` | 108 | `RFC9_ERROR_CODE_TIMEOUT` | The operation timed out on the device side. | Retry the operation. If timeouts persist, check the BLE connection quality. |

### Device-originated Errors (200–208)

These codes indicate that the error was caused by a condition on the device side.

| Error Code | Value | Error Name | Description | Hint |
|:---:|:---:|:---|:---|:---|
| `200` | 200 | `RFC9_ERROR_CODE_UNIDENTIFIED_DEVICE_ERROR` | An unclassified internal device error occurred. | Retry the operation. If the error persists, reconnect to the device. |
| `201` | 201 | `RFC9_ERROR_CODE_NOT_IMPLEMENTED` | The requested operation is not implemented in this firmware version. | Check the firmware version and consult the device's feature list. |
| `202` | 202 | `RFC9_ERROR_CODE_SYSTEM_BUSY` | The device is currently busy with another operation and cannot process the request. | Wait for the ongoing operation to finish and retry. Use `sendInitializationAndStartSyncNotifications()` before a sync session to signal the device to prepare. |
| `203` | 203 | `RFC9_ERROR_CODE_INVALID_CONTENT` | The content of the data written to the device is invalid (e.g., malformed protobuf, bad checksum, or wrong file format). | Verify the data being written is correctly formatted and encoded. |
| `204` | 204 | `RFC9_ERROR_CODE_CHECKSUM_FAILURE` | The checksum of a transferred data block did not match the expected value. The transfer was rejected to prevent data corruption. | Retry the transfer. If failures persist, check the host-side serialisation logic. |
| `205` | 205 | `RFC9_ERROR_CODE_DISK_FULL` | The device's storage is full and the write operation cannot be completed. | Call `getDiskSpace()` to verify available space, and remove old recordings or files to free up storage before retrying. |
| `206` | 206 | `RFC9_ERROR_CODE_PREREQUISITE_NOT_MET` | A prerequisite condition for the operation was not met (e.g., first-time-use setup not completed, or a required prior step was skipped). | Ensure all required setup steps have been completed. Refer to the SDK's operation sequence documentation. |
| `207` | 207 | `RFC9_ERROR_CODE_INSUFFICIENT_BUFFER` | The device's internal buffer is too small to complete the requested operation. *Not returned as a direct PFTP response* — this code is only recorded inside the device's SYNCERR error file (readable via sync), never sent as a PFTP response packet error code. | — |
| `208` | 208 | `RFC9_ERROR_CODE_WAIT_FOR_IDLING` | The device is not yet in an idle state. The operation requires the device to be idle before it can be executed. *Not sent by Polar360* — this code is defined in the specification but is not returned by the current implementation. | — |

---

## Device-to-Host (D2H) Notifications

D2H notifications are unsolicited messages that the device sends to the host application to communicate important state changes. They are delivered over BLE. The host subscribes to them using `observeDeviceToHostNotifications(identifier:)` from `PolarDeviceToHostNotificationsApi`.

The table below lists all defined D2H notification types together with a **Polar360** column that indicates whether a Polar device running the Polar360 firmware configuration will emit that notification.

> **Polar360** column legend:
> - ✅ Sent — Polar360 emits this notification in normal operation
> - ❌ Not sent — Polar360 does not send this notification
> - 🔧 Manufacturing only — sent exclusively during factory / production tests, not during end-user use

| Value | SDK Enum (`PolarDeviceToHostNotification`) — iOS: `lowerCamelCase` · Android: `UPPER_SNAKE_CASE` | Description | Polar360 |
|:---:|:---|:---|:---:|
| 0 | `filesystemModified` | The device filesystem was modified (a file or directory was created, updated, or removed). Signals the host that new data may be available for sync. | ❌ |
| 1 | `internalTestEvent` | A production test event occurred on the device. Not relevant to end-user applications. | 🔧 |
| 2 | `idling` | The device has transitioned to an idle state and is ready to accept file-transfer operations. | ❌ |
| 3 | `batteryStatus` | The device battery level or charging state has changed. | ❌ |
| 4 | `inactivityAlert` | The device has detected that the user has been inactive for too long and is alerting the host. | ✅ |
| 5 | `trainingSessionStatus` | The status of a training session on the device has changed (e.g., started, stopped). | ❌ |
| 7 | `syncRequired` | The device requests that the host initiates a data synchronisation. This is sent when the device determines that pending data needs to be transferred. | ✅ |
| 8 | `autosyncStatus` | The outcome of an automatic sync attempt initiated by the device is reported back to the host. | ❌ |
| 9 | `pnsDhNotificationResponse` | The user has responded to a push notification displayed on the device (e.g., accepted, dismissed, or acted on a phone notification). Carries the notification ID and the action taken. | ❌ |
| 10 | `pnsSettings` | The device reports its current push notification settings to the host (e.g., whether previews are enabled). | ❌ |
| 11 | `startGpsMeasurement` | The device requests the host to start providing GPS location data (used on devices without built-in GNSS). May include minimum update interval and accuracy requirements. | ❌ |
| 12 | `stopGpsMeasurement` | The device requests the host to stop providing GPS location data. | ❌ |
| 13 | `keepBackgroundAlive` | The device requests that the host application remain active in the background (e.g., to maintain a live GPS feed). | ❌ |
| 14 | `polarShellDhData` | An internal diagnostic/debug message from the device. Not intended for end-user applications. | ❌ |
| 15 | `mediaControlRequestDh` | The device is requesting media information from the host (e.g., current track metadata or playback state). | ❌ |
| 16 | `mediaControlCommandDh` | The device is sending a media playback command to the host (e.g., play, pause, next, previous, volume up/down). | ❌ |
| 17 | `mediaControlEnabled` | The device reports that media control has been enabled or disabled. | ❌ |
| 18 | `restApiEvent` | A REST API event has occurred on the device. Used to push sleep analysis results or data-collection (24/7 activity) events to the host for processing via the device's REST API. | ✅ |
| 19 | `exerciseStatus` | The status of an exercise session on the device has changed (e.g., an exercise was started or completed). The host should sync exercise data after receiving this notification. | ✅ |