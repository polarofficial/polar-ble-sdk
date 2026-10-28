# Polar Background Sync Sample for Android

The code example demonstrates techniques for automatically syncing
data from Polar devices. It includes functionality of deleting syncable data from either device or mobile side, using simple data age criteria. Polar 360 has been used as test device while developing this example. 

The example code is provided as is. Provided code has not necessarily been tested by Polar for extended periods of time -- continous usage spanning several days. Testing results, issue reports,and improvement ideas are welcome. 

This source distribution contains app version **0.18.0** from commit **9c5a8e7** and uses public Polar BLE SDK **8.5.0**, pinned to this exact release.

## Requirements

- Android Studio with Android SDK Platform 34 and Build Tools installed.
- JDK 21.
- Internet access to Google Maven, Maven Central, the Gradle plugin portal and JitPack for the public dependencies. No sibling SDK checkout, Firebase configuration or release credentials are required.
- An Android phone running Android 13 (API 33) or later, with Bluetooth enabled.
- A Polar device that supports the data features you want to sync.

## Build and run

1. Unzip this distribution and open the extracted directory (containing `settings.gradle`) in Android Studio, or work from this directory in a terminal.
   Configure the Android SDK path through Android Studio, `ANDROID_HOME`, or your own unshared `local.properties` file. Keep `localSdk=false` to use the pinned public SDK.
2. Let Gradle sync, connect and authorize your Android phone, then run the `app` configuration from Android Studio.
3. From a terminal, build and install the debug app with:

   ```sh
   ./gradlew :app:installDebug
   ```

4. Approve the nearby-device/Bluetooth and notification permissions requested by Android.
5. In **Devices**, enter the Polar device ID shown by the device and tap **Add**. Select data types and tap **Start Background Sync**. Approve the system companion-device association if prompted.

## App controls

The dashboard lets you add or remove devices, start or stop background sync, request **Synchronize Now**, and choose data types. **Associate for background sync** can create the system association for an already-bonded device. **Device Cleanup** may delete successfully synced recordings from the sensor. **Sync Triggers** controls foreground and timed sync. **Local Storage** controls optional retention of files saved by the app. **Browse Synced Data** views and exports saved JSON; **Sync Log** and **Graph** show diagnostics.

Android controls background execution and may restrict it based on system settings. Keep the app open during initial setup and verify a successful sync before enabling device cleanup.

## Verification and release signing

Run the included unit tests and build unsigned Release output with:

```sh
./gradlew --dependency-verification strict :sync-library:testDebugUnitTest :app:testDebugUnitTest :app:assembleRelease
```

The archive includes the Gradle wrapper and dependency verification metadata. Do not disable strict verification to resolve unexplained checksum failures. The export passed the dependency advisory/integrity gate at generation time; that is not a permanent guarantee against dependency vulnerabilities.

Debug builds use your local debug key. Release signing credentials and publishing scripts are intentionally excluded: configure your own signing and application ID before distributing your own app. A locally debug-signed build cannot replace the Polar release-signed app with the same application ID; use a distinct ID instead of uninstalling if existing sensor data must be retained. For example:

```sh
./gradlew :app:installDebug -PbgsyncDebugApplicationIdSuffix=.thirdparty
```

Failure retries are bounded per data type and preserve completed work across reconnects. Android's foreground service is still subject to OS and device-vendor restrictions. Validate behavior on the recipient's hardware; the sample is not a production reliability guarantee.

Saved sensor data and diagnostic logs may contain personal or health information. Review exported files and assess the app's backup/storage settings before deployment.

Some large synced data files may crash the app when viewed. 

## Rights and dependencies

This archive does not grant a license to the sample code or Polar branding. Obtain the applicable permission or agreement from Polar before use or redistribution. Public dependencies retain their respective license terms and notice obligations; consult their resolved artifacts and repositories. Dependency binaries and license texts are not bundled, except for the standard Gradle wrapper JAR and its existing notices.

Copyright © 2026 Polar Electro Oy. All rights reserved.
