package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.api.ble.model.BleDeviceSession
import com.polar.androidcommunications.api.ble.model.BleDeviceSession.DeviceSessionState
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.api.ble.model.gatt.client.BleHrClient
import com.polar.androidcommunications.api.ble.model.gatt.client.BleHrClient.Companion.HR_MEASUREMENT
import com.polar.androidcommunications.api.ble.model.gatt.client.BleHrClient.Companion.HR_SERVICE
import com.polar.androidcommunications.api.ble.model.gatt.client.BlePfcClient
import com.polar.androidcommunications.api.ble.model.gatt.client.BlePfcClient.Companion.PFC_SERVICE
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.sdk.api.errors.PolarDeviceDisconnected
import com.polar.sdk.api.errors.PolarDeviceNotFound
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.errors.PolarNotificationNotEnabled
import com.polar.sdk.api.errors.PolarServiceNotAvailable
import java.util.UUID

internal object PolarServiceClientUtils {

    @Throws(Throwable::class)
    internal fun sessionHrClientReady(identifier: String, listener: BleDeviceListener?): BleDeviceSession {
        val session = sessionServiceReady(identifier, HR_SERVICE, listener)
        val client = session.fetchClient(HR_SERVICE) as BleHrClient? ?: throw PolarServiceNotAvailable()
        val hrMeasurementChr = client.getNotificationAtomicInteger(HR_MEASUREMENT)
        if (hrMeasurementChr != null && hrMeasurementChr.get() == BleGattBase.ATT_SUCCESS) {
            return session
        }
        throw PolarNotificationNotEnabled()
    }

    @Throws(Throwable::class)
    internal fun sessionPmdClientReady(identifier: String, listener: BleDeviceListener?): BleDeviceSession {
        val session = sessionServiceReady(identifier, BlePMDClient.PMD_SERVICE, listener)
        val client = session.fetchClient(BlePMDClient.PMD_SERVICE) as BlePMDClient? ?: throw PolarServiceNotAvailable()
        val pair = client.getNotificationAtomicInteger(BlePMDClient.PMD_CP)
        val pairData = client.getNotificationAtomicInteger(BlePMDClient.PMD_DATA)
        if (pair != null && pairData != null && pair.get() == BleGattBase.ATT_SUCCESS && pairData.get() == BleGattBase.ATT_SUCCESS) {
            return session
        }
        throw PolarNotificationNotEnabled()
    }

    @Throws(Throwable::class)
    internal fun sessionPsFtpClientReady(identifier: String, listener: BleDeviceListener?): BleDeviceSession {
        val session = sessionServiceReady(identifier, BlePsFtpUtils.RFC77_PFTP_SERVICE, listener)
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient? ?: throw PolarServiceNotAvailable()
        val pair = client.getNotificationAtomicInteger(BlePsFtpUtils.RFC77_PFTP_MTU_CHARACTERISTIC)
        if (pair != null && pair.get() == BleGattBase.ATT_SUCCESS) {
            return session
        }
        throw PolarNotificationNotEnabled()
    }

    @Throws(Throwable::class)
    internal fun sessionPsPfcClientReady(identifier: String, listener: BleDeviceListener?): BleDeviceSession {
        val session = sessionServiceReady(identifier, PFC_SERVICE, listener)
        val client = session.fetchClient(PFC_SERVICE) as BlePfcClient? ?: throw PolarServiceNotAvailable()
        if (client.isServiceDiscovered) {
            return session
        }
        throw PolarNotificationNotEnabled()
    }

    @Throws(Throwable::class)
    internal fun sessionServiceReady(identifier: String, service: UUID, listener: BleDeviceListener?): BleDeviceSession {
        val session = fetchSession(identifier, listener)
            ?: throw PolarDeviceNotFound()

        if (session.sessionState != DeviceSessionState.SESSION_OPEN) {
            throw PolarDeviceDisconnected()
        }

        val client = session.fetchClient(service)
            ?: throw PolarServiceNotAvailable()

        val timeoutMillis = 10 * 1000L
        if (!client.isServiceDiscovered) {
            if (!waitForServiceDiscovery(client, timeoutMillis)) {
                throw PolarServiceNotAvailable()
            }
        }

        return session
    }

    @Throws(PolarInvalidArgument::class)
    internal fun fetchSession(identifier: String, listener: BleDeviceListener?): BleDeviceSession? {
        if (identifier.matches(Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$"))) {
            return sessionByAddress(identifier, listener)
        } else if (identifier.matches(Regex("([0-9a-fA-F]){6,8}"))) {
            return sessionByDeviceId(identifier, listener)
        }
        throw PolarInvalidArgument()
    }

    internal fun getRSSIValue(deviceId: String, listener: BleDeviceListener?): Int {
        listener?.let {
            val sessions = it.deviceSessions()
            if (sessions != null) {
                for (session in sessions) {
                    if (session != null) {
                        if (session.advertisementContent.polarDeviceId == deviceId) {
                            return session.rssi
                        }
                    }
                }
            }
        }
        return -1
    }

    private fun sessionByDeviceId(deviceId: String, listener: BleDeviceListener?): BleDeviceSession? {
        listener?.let {
            val sessions = it.deviceSessions()
            if (sessions != null) {
                for (session in sessions) {
                    if (session != null) {
                        if (session.advertisementContent.polarDeviceId == deviceId) {
                            return session
                        }
                    }
                }
            }
        }
        return null
    }

    private fun sessionByAddress(address: String, listener: BleDeviceListener?): BleDeviceSession? {
        listener?.let {
            val sessions = it.deviceSessions()
            if (sessions != null) {
                for (session in sessions) {
                    if (session != null) {
                        if (session.address == address) {
                            return session
                        }
                    }
                }
            }
        }
        return null
    }

    private fun waitForServiceDiscovery(client: BleGattBase, timeoutMs: Long): Boolean {
        val start = System.currentTimeMillis()
        while (!client.isServiceDiscovered) {
            if (System.currentTimeMillis() - start > timeoutMs) {
                return false
            }
            try {
                Thread.sleep(100)
            } catch (ie: InterruptedException) {
                return false
            }
        }
        return true
    }
}