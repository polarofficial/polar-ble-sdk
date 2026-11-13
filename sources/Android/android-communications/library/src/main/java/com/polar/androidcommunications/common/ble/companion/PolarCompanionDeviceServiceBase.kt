// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.androidcommunications.common.ble.companion

import android.companion.CompanionDeviceService
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Base [CompanionDeviceService] implementation that forwards device presence events to
 * [PolarCompanionDevicePresenceBus], so that [PolarCompanionDeviceManager] based consumers
 * (e.g. `PolarCompanionDeviceApiImpl`) can react to them regardless of process lifecycle.
 *
 * The SDK does not register a companion service automatically. To receive SDK presence callbacks,
 * explicitly register [PolarCompanionDeviceService] or a subclass of this base in the host
 * application's manifest, inside its `<application>` element:
 * ```xml
 * <service
 *     android:name=".MyCompanionDeviceService"
 *     android:permission="android.permission.BIND_COMPANION_DEVICE_SERVICE"
 *     android:exported="true">
 *     <intent-filter>
 *         <action android:name="android.companion.CompanionDeviceService" />
 *     </intent-filter>
 * </service>
 * ```
 * where `MyCompanionDeviceService` extends [PolarCompanionDeviceServiceBase].
 * Overrides of presence callbacks must call `super` to forward events to the SDK.
 *
 * Apps that handle presence independently can keep their own service without registering an SDK
 * service. If multiple companion services are declared, Android binds all of them but delivers
 * presence callbacks only to the primary service. Explicitly select it using the service-level
 * `android.companion.PROPERTY_PRIMARY_COMPANION_DEVICE_SERVICE` property with `android:value="true"`.
 */
@RequiresApi(Build.VERSION_CODES.O)
abstract class PolarCompanionDeviceServiceBase : CompanionDeviceService() {

    override fun onDeviceAppeared(associationInfo: android.companion.AssociationInfo) {
        super.onDeviceAppeared(associationInfo)
        associationInfo.deviceMacAddress?.toString()?.let {
            PolarCompanionDevicePresenceBus.notifyDeviceAppeared(it)
        }
    }

    override fun onDeviceDisappeared(associationInfo: android.companion.AssociationInfo) {
        super.onDeviceDisappeared(associationInfo)
        associationInfo.deviceMacAddress?.toString()?.let {
            PolarCompanionDevicePresenceBus.notifyDeviceDisappeared(it)
        }
    }

    @Suppress("DEPRECATION")
    override fun onDeviceAppeared(address: String) {
        super.onDeviceAppeared(address)
        PolarCompanionDevicePresenceBus.notifyDeviceAppeared(address)
    }

    @Suppress("DEPRECATION")
    override fun onDeviceDisappeared(address: String) {
        super.onDeviceDisappeared(address)
        PolarCompanionDevicePresenceBus.notifyDeviceDisappeared(address)
    }
}

