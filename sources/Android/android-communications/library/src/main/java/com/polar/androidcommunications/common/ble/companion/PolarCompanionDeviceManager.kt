// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.androidcommunications.common.ble.companion

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.le.ScanFilter
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import com.polar.androidcommunications.api.ble.BleLogger
import java.util.concurrent.Executor
import java.util.regex.Pattern

/**
 * Wrapper around Android's [CompanionDeviceManager] (CDM), integrated with the classic BLE
 * scan/connect flow already implemented by
 * [com.polar.androidcommunications.enpoints.ble.bluedroid.host.BDScanCallback] and
 * [com.polar.androidcommunications.enpoints.ble.bluedroid.host.BDDeviceListenerImpl].
 *
 * This class purposefully keeps a narrow surface and delegates the actual `IntentSender` launch
 * (user consent dialog) to the calling Activity, since the base library has no Activity context
 * of its own.
 */
class PolarCompanionDeviceManager(context: Context) {

    companion object {
        private const val TAG = "PolarCompanionDeviceManager"
        private val MAC_ADDRESS_PATTERN: Pattern =
            Pattern.compile("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")

        /**
         * Action string for requesting the "run in background" companion privilege. Mirrors
         * [android.companion.CompanionDeviceManager.REQUEST_COMPANION_RUN_IN_BACKGROUND], which is
         * not part of the public SDK stub jar used at compile time.
         */
        private const val REQUEST_COMPANION_RUN_IN_BACKGROUND_ACTION =
            "android.companion.action.REQUEST_COMPANION_RUN_IN_BACKGROUND"

        /**
         * @return true if the Companion Device Manager APIs are available on this OS version
         * and this device supports the companion device setup feature.
         */
        @JvmStatic
        fun isAvailable(context: Context): Boolean {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)
        }

        internal fun isValidMacAddress(address: String): Boolean =
            MAC_ADDRESS_PATTERN.matcher(address).matches()
    }

    /**
     * Callback for the outcome of an association request created with [buildAssociationRequest].
     */
    interface AssociationCallback {
        /**
         * Called when the system needs user confirmation. The caller (Activity) must launch this
         * [IntentSender], e.g. via `ActivityResultLauncher<IntentSenderRequest>` or
         * `startIntentSenderForResult`, and on a positive result call [onAssociationCreated] with
         * the address obtained from the resulting Intent (extra [CompanionDeviceManager.EXTRA_DEVICE]
         * or [android.companion.AssociationInfo] on API 33+).
         */
        fun onAssociationPending(intentSender: IntentSender)

        /**
         * Called (API 33+ only) once the device is directly associated without needing user
         * interaction (single device match with high confidence).
         */
        fun onAssociationCreated(macAddress: String) {}

        /**
         * Called if the association attempt failed.
         */
        fun onFailure(error: String?)
    }

    private val appContext = context.applicationContext
    private val companionDeviceManager: CompanionDeviceManager? =
        if (isAvailable(appContext)) {
            appContext.getSystemService(Context.COMPANION_DEVICE_SERVICE) as? CompanionDeviceManager
        } else {
            null
        }

    /** @return true if CDM is available on this device/OS version for this instance. */
    fun isAvailable(): Boolean = companionDeviceManager != null

    /**
     * Builds an [AssociationRequest] that matches nearby BLE devices advertising one of the given
     * [serviceUuids], optionally restricted to devices whose name starts with [namePrefix].
     *
     * @param namePrefix optional required BLE device name prefix, e.g. "Polar"
     * @param serviceUuids BLE advertised service UUIDs to match, e.g. HR service, PFTP service
     * @param singleDevice if true, the system association dialog will only allow single selection
     * and, on API 33+, may skip the dialog entirely if exactly one strong match is found.
     * @param macAddress optional BT MAC address; when set, only that exact device can match, so the
     * chooser can never offer (or auto-pick) a different nearby device.
     */
    fun buildAssociationRequest(
        namePrefix: String? = "Polar",
        serviceUuids: List<java.util.UUID> = emptyList(),
        singleDevice: Boolean = true,
        macAddress: String? = null
    ): AssociationRequest {
        val deviceFilterBuilder = BluetoothLeDeviceFilter.Builder()
        namePrefix?.let { deviceFilterBuilder.setNamePattern(Pattern.compile("^$it.*")) }
        val targetMac = macAddress?.takeIf { isValidMacAddress(it) }?.uppercase()
        if (serviceUuids.isNotEmpty() || targetMac != null) {
            // BluetoothLeDeviceFilter only supports a single ScanFilter directly; when there are
            // multiple candidate service UUIDs we rely on the name prefix + first UUID and leave
            // the manual scanner (BDScanCallback) as the source of truth for the rest.
            val scanFilterBuilder = ScanFilter.Builder()
            serviceUuids.firstOrNull()?.let { scanFilterBuilder.setServiceUuid(ParcelUuid(it)) }
            targetMac?.let { scanFilterBuilder.setDeviceAddress(it) }
            deviceFilterBuilder.setScanFilter(scanFilterBuilder.build())
        }

        return AssociationRequest.Builder()
            .addDeviceFilter(deviceFilterBuilder.build())
            .setSingleDevice(singleDevice)
            .build()
    }

    /**
     * Starts the CDM association flow. Result is delivered asynchronously via [callback].
     */
    @SuppressLint("MissingPermission")
    fun associate(request: AssociationRequest, executor: Executor, callback: AssociationCallback) {
        val cdm = companionDeviceManager
        if (cdm == null) {
            callback.onFailure("CompanionDeviceManager not available on this device/OS version")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            cdm.associate(request, executor, object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(intentSender: IntentSender) {
                    callback.onAssociationPending(intentSender)
                }

                override fun onAssociationCreated(associationInfo: AssociationInfo) {
                    callback.onAssociationCreated(associationInfo.deviceMacAddress?.toString().orEmpty())
                }

                override fun onFailure(error: CharSequence?) {
                    BleLogger.e(TAG, "CDM association failed: $error")
                    callback.onFailure(error?.toString())
                }
            })
        } else {
            @Suppress("DEPRECATION")
            cdm.associate(request, object : CompanionDeviceManager.Callback() {
                override fun onDeviceFound(chooserLauncher: IntentSender) {
                    callback.onAssociationPending(chooserLauncher)
                }

                override fun onFailure(error: CharSequence?) {
                    BleLogger.e(TAG, "CDM association failed: $error")
                    callback.onFailure(error?.toString())
                }
            }, null)
        }
    }

    /**
     * Convenience helper for launching the association [IntentSender] from an [Activity] using
     * the legacy `startIntentSenderForResult` API (for callers not yet using the Activity Result
     * APIs). Prefer `ActivityResultContracts.StartIntentSenderForResult` when possible.
     */
    fun launchAssociation(activity: Activity, intentSender: IntentSender, requestCode: Int) {
        activity.startIntentSenderForResult(intentSender, requestCode, null, 0, 0, 0)
    }

    /**
     * @return list of BT MAC addresses of currently CDM-associated devices for this app.
     */
    @SuppressLint("MissingPermission")
    fun associatedDeviceAddresses(): List<String> {
        val cdm = companionDeviceManager ?: return emptyList()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                cdm.myAssociations.mapNotNull { it.deviceMacAddress?.toString() }
            } else {
                @Suppress("DEPRECATION")
                cdm.associations.toList()
            }
        } catch (e: Exception) {
            BleLogger.e(TAG, "Failed to fetch CDM associations: ${e.message}")
            emptyList()
        }
    }

    /**
     * @return true if [macAddress] is already associated via CDM.
     */
    fun isAssociated(macAddress: String): Boolean =
        associatedDeviceAddresses().any { it.equals(macAddress, ignoreCase = true) }

    /**
     * Removes the CDM association for [macAddress], if any.
     */
    @SuppressLint("MissingPermission")
    fun disassociate(macAddress: String) {
        val cdm = companionDeviceManager ?: return
        try {
            cdm.disassociate(macAddress)
        } catch (e: Exception) {
            BleLogger.e(TAG, "Failed to disassociate $macAddress: ${e.message}")
        }
    }

    /**
     * Starts observing device presence for an already-associated device. Once observing, the
     * system will deliver [android.companion.CompanionDeviceService.onDeviceAppeared] /
     * `onDeviceDisappeared` callbacks (and may even start the app's declared
     * [android.companion.CompanionDeviceService] to handle them), improving reconnection
     * responsiveness without requiring continuous foreground BLE scanning.
     */
    @SuppressLint("MissingPermission")
    fun startObservingDevicePresence(macAddress: String): Boolean {
        val cdm = companionDeviceManager ?: return false
        if (!isValidMacAddress(macAddress)) {
            BleLogger.e(TAG, "startObservingDevicePresence: invalid mac address $macAddress")
            return false
        }
        return try {
            cdm.startObservingDevicePresence(macAddress)
            true
        } catch (e: Exception) {
            BleLogger.e(TAG, "startObservingDevicePresence failed for $macAddress: ${e.message}")
            false
        }
    }

    /**
     * Stops observing device presence previously started with [startObservingDevicePresence].
     */
    @SuppressLint("MissingPermission")
    fun stopObservingDevicePresence(macAddress: String): Boolean {
        val cdm = companionDeviceManager ?: return false
        return try {
            cdm.stopObservingDevicePresence(macAddress)
            true
        } catch (e: Exception) {
            BleLogger.e(TAG, "stopObservingDevicePresence failed for $macAddress: ${e.message}")
            false
        }
    }

    /**
     * Requests the "run in background" companion app privilege (API 33+). This relaxes background
     * execution / BLE scan throttling limits normally imposed on apps once they are backgrounded,
     * for apps that are companion apps of an associated device. Must be called from an [Activity];
     * the result is delivered to `onActivityResult(requestCode, ...)` of that activity (or via an
     * `ActivityResultLauncher<Intent>`).
     *
     * Note: the `REQUEST_COMPANION_RUN_IN_BACKGROUND` action constant is not exposed in the public
     * Android SDK stubs (compileSdk), so the documented action string literal is used directly.
     *
     * @return true if the request intent was launched, false if not supported/available.
     */
    fun requestBackgroundExecutionPrivilege(activity: Activity, requestCode: Int): Boolean {
        if (companionDeviceManager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return false
        }
        return try {
            val intent = android.content.Intent(REQUEST_COMPANION_RUN_IN_BACKGROUND_ACTION)
            @Suppress("DEPRECATION")
            activity.startActivityForResult(intent, requestCode)
            true
        } catch (e: Exception) {
            BleLogger.e(TAG, "requestBackgroundExecutionPrivilege failed: ${e.message}")
            false
        }
    }
}







