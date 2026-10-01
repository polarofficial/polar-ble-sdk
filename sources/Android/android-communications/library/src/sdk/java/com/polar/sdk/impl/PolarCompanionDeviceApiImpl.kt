// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.impl

import android.app.Activity
import android.companion.AssociationRequest
import android.content.Context
import android.content.IntentSender
import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.common.ble.companion.PolarCompanionAssociationActivity
import com.polar.androidcommunications.common.ble.companion.PolarCompanionAssociationResultBus
import com.polar.androidcommunications.common.ble.companion.PolarCompanionDeviceManager
import com.polar.androidcommunications.common.ble.companion.PolarCompanionDevicePresenceBus
import com.polar.sdk.api.PolarCompanionDeviceApi
import com.polar.sdk.api.PolarCompanionDeviceApi.PolarCompanionDeviceCallback
import com.polar.sdk.impl.utils.PolarServiceClientUtils
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Implementation of [PolarCompanionDeviceApi], wrapping [PolarCompanionDeviceManager] and
 * resolving Polar `identifier` (device id or BT address) to the BT MAC address.
 *
 * @param listenerProvider supplies the current [BleDeviceListener] lazily, since it may not exist
 * yet when this class is constructed.
 */
internal class PolarCompanionDeviceApiImpl(
    context: Context,
    private val listenerProvider: () -> BleDeviceListener?
) : PolarCompanionDeviceApi {

    companion object {
        private const val TAG = "PolarCompanionDeviceApiImpl"
    }

    private val appContext = context.applicationContext
    private val manager = PolarCompanionDeviceManager(appContext)
    private val executor = Executors.newSingleThreadExecutor()
    private val presenceListeners = mutableMapOf<String, PolarCompanionDevicePresenceBus.Listener>()


    /** Resolves an `identifier` (Polar device id or "AA:BB:CC:DD:EE:FF") to a BT MAC address. */
    private fun resolveMacAddress(identifier: String): String? {
        if (PolarCompanionDeviceManager.isValidMacAddress(identifier)) return identifier
        return PolarServiceClientUtils.fetchSession(identifier, listenerProvider())?.address
    }

    override fun isCompanionDeviceManagerAvailable(): Boolean = manager.isAvailable()

    override fun createAssociationRequest(namePrefix: String?, singleDevice: Boolean): AssociationRequest =
        manager.buildAssociationRequest(namePrefix = namePrefix, singleDevice = singleDevice)

    override fun associate(
        request: AssociationRequest,
        onIntentSender: (IntentSender) -> Unit,
        callback: PolarCompanionDeviceCallback
    ) {
        manager.associate(request, executor, object : PolarCompanionDeviceManager.AssociationCallback {
            override fun onAssociationPending(intentSender: IntentSender) {
                onIntentSender(intentSender)
            }

            override fun onAssociationCreated(macAddress: String) {
                callback.onAssociationSucceeded(macAddress)
            }

            override fun onFailure(error: String?) {
                callback.onError(error)
            }
        })
    }

    override fun launchAssociation(activity: Activity, intentSender: IntentSender, requestCode: Int) {
        manager.launchAssociation(activity, intentSender, requestCode)
    }

    override fun getAssociatedDeviceAddresses(): List<String> = manager.associatedDeviceAddresses()

    override fun isAssociated(identifier: String): Boolean {
        val mac = resolveMacAddress(identifier) ?: return false
        return manager.isAssociated(mac)
    }

    override fun removeAssociation(identifier: String) {
        val mac = resolveMacAddress(identifier) ?: return
        manager.disassociate(mac)
        presenceListeners.remove(mac.uppercase())?.let {
            PolarCompanionDevicePresenceBus.removeListener(mac, it)
        }
    }

    override fun startObservingDevicePresence(identifier: String, callback: PolarCompanionDeviceCallback): Boolean {
        val mac = resolveMacAddress(identifier) ?: run {
            callback.onError("Could not resolve BT address for identifier: $identifier")
            return false
        }
        val started = manager.startObservingDevicePresence(mac)
        if (started) {
            val presenceListener = PolarCompanionDevicePresenceBus.Listener { address, appeared ->
                if (appeared) callback.onDeviceAppeared(address) else callback.onDeviceDisappeared(address)
            }
            presenceListeners[mac.uppercase()] = presenceListener
            PolarCompanionDevicePresenceBus.addListener(mac, presenceListener)
        } else {
            callback.onError("Failed to start observing device presence for $mac")
        }
        return started
    }

    override fun stopObservingDevicePresence(identifier: String): Boolean {
        val mac = resolveMacAddress(identifier) ?: return false
        presenceListeners.remove(mac.uppercase())?.let {
            PolarCompanionDevicePresenceBus.removeListener(mac, it)
        }
        return manager.stopObservingDevicePresence(mac)
    }

    override fun requestBackgroundExecutionPrivilege(activity: Activity, requestCode: Int): Boolean =
        manager.requestBackgroundExecutionPrivilege(activity, requestCode)

    /**
     * If [identifier] is already CDM-associated, starts presence observation directly; otherwise
     * requests association and, if user consent is required, launches the SDK's own trampoline
     * [PolarCompanionAssociationActivity] instead of requiring an Activity from the host app.
     *
     * @param onFinished invoked exactly once when the association flow has ended (succeeded,
     * failed, cancelled or not needed), so the caller can defer work that would otherwise disturb
     * CDM discovery, e.g. opening a GATT connection that stops the device from advertising.
     */
    fun ensureCompanionHandling(
        identifier: String,
        callback: PolarCompanionDeviceCallback,
        onFinished: () -> Unit = {}
    ) {
        val finished = AtomicBoolean(false)
        val finish = { if (finished.compareAndSet(false, true)) onFinished() }
        if (!manager.isAvailable()) { finish(); return }
        if (isAssociated(identifier)) {
            startObservingDevicePresence(identifier, callback)
            finish()
            return
        }
        val targetMac = resolveMacAddress(identifier) ?: run {
            BleLogger.w(TAG, "Companion association skipped, BT address unknown for $identifier")
            finish()
            return
        }
        associate(
            request = manager.buildAssociationRequest(macAddress = targetMac),
            onIntentSender = { intentSender -> launchAssociationTrampoline(identifier, intentSender, callback, finish) },
            callback = object : PolarCompanionDeviceCallback {
                override fun onAssociationSucceeded(macAddress: String) {
                    if (!macAddress.equals(targetMac, ignoreCase = true)) {
                        BleLogger.w(TAG, "Ignoring companion association for unexpected device $macAddress (expected $targetMac)")
                        return
                    }
                    startObservingDevicePresence(identifier, callback)
                    callback.onAssociationSucceeded(macAddress)
                    finish()
                }

                override fun onError(message: String?) {
                    BleLogger.w(TAG, "Companion association failed for $identifier: $message")
                    callback.onError(message)
                    finish()
                }
            }
        )
    }

    /** Starts presence observation for [identifier] only if it is already CDM-associated. */
    fun observePresenceIfAssociated(identifier: String, callback: PolarCompanionDeviceCallback) {
        if (!manager.isAvailable()) return
        if (isAssociated(identifier)) startObservingDevicePresence(identifier, callback)
    }

    /** Resumes presence observation for every device already CDM-associated from a previous run. */
    fun resumeAllAssociatedDevicesPresenceObservation(callback: PolarCompanionDeviceCallback) {
        if (!manager.isAvailable()) return
        getAssociatedDeviceAddresses().forEach { startObservingDevicePresence(it, callback) }
    }

    private fun launchAssociationTrampoline(
        identifier: String,
        intentSender: IntentSender,
        callback: PolarCompanionDeviceCallback,
        onFinished: () -> Unit = {}
    ) {
        lateinit var resultListener: () -> Unit
        resultListener = {
            PolarCompanionAssociationResultBus.removeListener(resultListener)
            if (isAssociated(identifier)) {
                startObservingDevicePresence(identifier, callback)
                resolveMacAddress(identifier)?.let { callback.onAssociationSucceeded(it) }
            }
            onFinished()
        }
        PolarCompanionAssociationResultBus.addListener(resultListener)
        try {
            appContext.startActivity(PolarCompanionAssociationActivity.createIntent(appContext, intentSender))
        } catch (e: Exception) {
            PolarCompanionAssociationResultBus.removeListener(resultListener)
            BleLogger.e(TAG, "Failed to launch companion association chooser for $identifier: ${e.message}")
            callback.onError(e.message)
            onFinished()
        }
    }
}


