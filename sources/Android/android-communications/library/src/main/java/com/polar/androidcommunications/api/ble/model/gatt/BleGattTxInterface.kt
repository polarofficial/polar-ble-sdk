package com.polar.androidcommunications.api.ble.model.gatt

import com.polar.androidcommunications.api.ble.exceptions.BleCharacteristicNotFound
import com.polar.androidcommunications.api.ble.exceptions.BleGattNotInitialized
import com.polar.androidcommunications.api.ble.exceptions.BleServiceNotFound
import java.util.UUID

/**
 * Note any of these functions might be called from different thread
 */
interface BleGattTxInterface {
    @Throws(Exception::class)
    fun transmitMessages(
        serviceUuid: UUID,
        characteristicUuid: UUID,
        packets: List<ByteArray>,
        withResponse: Boolean
    )

    @Throws(Exception::class)
    fun transmitMessage(
        serviceUuid: UUID,
        characteristicUuid: UUID,
        packet: ByteArray,
        withResponse: Boolean
    )

    @Throws(Exception::class)
    fun readValue(serviceUuid: UUID, characteristicUuid: UUID)

    @Throws(
        BleCharacteristicNotFound::class,
        BleServiceNotFound::class,
        BleGattNotInitialized::class
    )
    fun setCharacteristicNotify(serviceUuid: UUID, characteristicUuid: UUID, enable: Boolean)

    // optional, request to stop and start scanning
    /**
     * gatt client requests to stop scanning, while there is some heavy attribute operation ongoing
     */
    fun gattClientRequestStopScanning()

    /**
     * gatt client has completed heavy attribute operation, and scanning can continue if needed
     */
    fun gattClientResumeScanning()

    /**
     * @return current att operation queue size
     */
    fun transportQueueSize(): Int

    fun isConnected(): Boolean
}
