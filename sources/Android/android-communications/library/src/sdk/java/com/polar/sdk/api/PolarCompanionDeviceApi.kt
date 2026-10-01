// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api

import android.app.Activity
import android.content.IntentSender

/**
 * API for integrating Android's Companion Device Manager (CDM) with a Polar device connection.
 *
 * Once a device is associated through this API, the app can request relaxed background
 * execution privileges and receive device presence notifications (device entering/leaving BLE
 * range) via a declared `CompanionDeviceService`, without needing a continuous BLE scan.
 *
 * This API complements, and does not replace, [PolarBleApi.searchForDevice] /
 * [PolarBleApi.connectToDevice]. Typical flow:
 * 1. Call [createAssociationRequest] and [associate] to have the user pick/approve a Polar device.
 * 2. On success, call [startObservingDevicePresence] with the returned MAC address.
 * 3. When [PolarCompanionDeviceCallback.onDeviceAppeared] fires, call [PolarBleApi.connectToDevice].
 */
interface PolarCompanionDeviceApi {

    /**
     * Callback describing device presence / association events surfaced through the wrapped
     * Companion Device Manager APIs.
     */
    interface PolarCompanionDeviceCallback {
        /** The device is now in BLE range (system observed presence). */
        fun onDeviceAppeared(macAddress: String) {}

        /** The device is no longer in BLE range (system observed absence). */
        fun onDeviceDisappeared(macAddress: String) {}

        /** Association with the device succeeded and can now be used with [startObservingDevicePresence]. */
        fun onAssociationSucceeded(macAddress: String) {}

        /** Association attempt or presence observation failed. */
        fun onError(message: String?) {}
    }

    /**
     * @return true if the Companion Device Manager feature is available on this OS
     * version/device.
     */
    fun isCompanionDeviceManagerAvailable(): Boolean

    /**
     * Builds an association request that will match nearby Polar devices.
     *
     * @param namePrefix required BLE advertised name prefix, default "Polar"
     * @param singleDevice restrict user selection to a single device
     */
    fun createAssociationRequest(
        namePrefix: String? = "Polar",
        singleDevice: Boolean = true
    ): android.companion.AssociationRequest

    /**
     * Starts the association flow for [request]. If user confirmation is required, the resulting
     * [IntentSender] is delivered through [onIntentSender] and the caller (Activity) must launch
     * it, e.g. with `ActivityResultContracts.StartIntentSenderForResult`.
     *
     * @param onIntentSender invoked with the [IntentSender] to launch for user consent
     * @param callback invoked with the outcome of the association
     */
    fun associate(
        request: android.companion.AssociationRequest,
        onIntentSender: (IntentSender) -> Unit,
        callback: PolarCompanionDeviceCallback
    )

    /**
     * Convenience helper to launch an association [IntentSender] using the legacy
     * `startIntentSenderForResult` Activity API.
     */
    fun launchAssociation(activity: Activity, intentSender: IntentSender, requestCode: Int)

    /**
     * @return BT MAC addresses of devices already CDM-associated with this app.
     */
    fun getAssociatedDeviceAddresses(): List<String>

    /**
     * @return true if the device behind [identifier] (Polar device id or BT address) is currently
     * CDM-associated.
     */
    fun isAssociated(identifier: String): Boolean

    /**
     * Removes the CDM association for the device behind [identifier].
     */
    fun removeAssociation(identifier: String)

    /**
     * Starts observing device presence for the device behind [identifier] (must already be
     * associated). Presence changes are reported via [callback].
     *
     * @return true if observation was started successfully
     */
    fun startObservingDevicePresence(identifier: String, callback: PolarCompanionDeviceCallback): Boolean

    /**
     * Stops observing device presence previously started with [startObservingDevicePresence].
     */
    fun stopObservingDevicePresence(identifier: String): Boolean

    /**
     * Requests the "run in background" companion privilege (API 33+), which relaxes background
     * execution / BLE scan throttling limits for companion apps. Must be called from an
     * [Activity]; result is delivered to that activity's `onActivityResult`.
     *
     * @return true if the request was launched
     */
    fun requestBackgroundExecutionPrivilege(activity: Activity, requestCode: Int): Boolean
}

