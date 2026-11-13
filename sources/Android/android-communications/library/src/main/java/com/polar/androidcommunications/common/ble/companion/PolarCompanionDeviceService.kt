// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.androidcommunications.common.ble.companion

/**
 * Ready-to-use [CompanionDeviceService][android.companion.CompanionDeviceService] that forwards
 * presence callbacks to the SDK. It is not registered in the SDK manifest: host applications
 * must explicitly declare this service, or a subclass of [PolarCompanionDeviceServiceBase],
 * with the permission and intent filter shown in [PolarCompanionDeviceServiceBase].
 * Apps using their own companion service do not need to register this service.
 */
class PolarCompanionDeviceService : PolarCompanionDeviceServiceBase()


