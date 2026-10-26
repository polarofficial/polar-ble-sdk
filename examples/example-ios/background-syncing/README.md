# Polar Background Sync Sample for iOS

The code example demonstrates techniques for automatically syncing data from Polar devices. It includes functionality of deleting syncable data from either device or mobile side, using simple data age criteria. Polar 360 has been used as test device while developing this example. 

The example code is provided as is. Provided code has not necessarily been tested by Polar for extended periods of time -- continous usage spanning several days. Testing results, issue reports,and improvement ideas are welcome. 

This source distribution contains app version **0.21.0** from commit **9c5a8e7** and uses the public Polar BLE SDK **8.5.0**, pinned to an exact Swift Package Manager version.

## Requirements

- A Mac with Xcode 26 or later (Swift 6.2 or later), required by the pinned SwiftProtobuf dependency. This distribution was validated with Xcode 27.1.
- An iPhone running iOS 16 or later and a valid Apple development team for device signing.
- A Polar device that supports the data features you want to sync.
- Internet access to resolve the public Swift packages. XcodeGen is needed only if you regenerate the included project from `project.yml`.

## Build and run

1. Unzip this distribution and open `BgSyncSample.xcodeproj` in Xcode.
2. Select the `BgSyncSample` scheme and your connected iPhone as the run destination.
3. In **Signing & Capabilities**, select your Apple development team and use a bundle identifier registered to that team. Xcode will resolve the pinned public SDK package.
4. Build and run the app. Approve Bluetooth access and, if you want completion alerts, notification permission on the iPhone.
5. In **Devices**, enter the Polar device ID shown by the device and tap **Add**. Select the data types you want, then tap **Start Background Sync**.

Alternatively, from this directory run:

```sh
xcodebuild -resolvePackageDependencies -project BgSyncSample.xcodeproj -scheme BgSyncSample
xcodebuild -project BgSyncSample.xcodeproj -scheme BgSyncSample -configuration Release -destination 'generic/platform=iOS' -disableAutomaticPackageResolution -onlyUsePackageVersionsFromResolvedFile CODE_SIGNING_ALLOWED=NO build
```

To install on a physical iPhone, open the project in Xcode, select a development team and device, then use **Product > Run**.

## App controls

The dashboard lets you add and remove devices, start or stop background sync, request **Synchronize Now**, and choose the data types to fetch. **Device Cleanup** can delete successfully synced recordings from the sensor; leave it off until you intentionally want that behavior. **Sync Triggers** controls foreground and timed sync. **Local Storage** controls optional retention of files saved by the app. **Browse Synced Data** views and exports saved JSON; **Sync Log** shows diagnostic activity.

Background Bluetooth behavior is managed by iOS and depends on device support, permissions, and system scheduling. Keep the app open during first-time setup and verify a successful sync before enabling cleanup.

## Verification and limitations

The included unit tests run on an iOS 17 or later simulator using the `BgSyncSample` scheme. Bluetooth sync itself requires a physical iPhone and sensor.

For background testing, launch without a debugger, confirm initialization and notification listening, then background and lock the phone. Failed data types are retried after remaining work. A background-limited cycle reports saved results and logs postponed types for the next sync trigger; a completion alert does not necessarily mean every requested type succeeded. Timed triggers are best-effort while iOS permits execution, not guaranteed periodic wakeups.

Some large synced data files may crash the app when viewed. 

The source includes one-shot simulation hooks guarded by `BGSYNC_VERIFICATION`. That flag is not enabled in the distributed project or normal Release build. Do not enable it for ordinary use. Simulations do not prove actual iOS expiration handling.

Saved sensor data and diagnostic logs may contain personal or health information. Review exported files before sharing them.

## Rights and dependencies

This archive does not grant a license to the sample code or Polar branding. Obtain the applicable permission or agreement from Polar before use or redistribution. The public Polar BLE SDK, SwiftProtobuf, and Zip retain their respective license terms; consult the license files in their resolved package repositories. Their source code is fetched by SPM, not bundled in this ZIP.

Copyright © 2026 Polar Electro Oy. All rights reserved.
