package com.polar.androidcommunications.api.ble.model.gatt;

import com.polar.androidcommunications.api.ble.exceptions.BleCharacteristicNotFound;
import com.polar.androidcommunications.api.ble.exceptions.BleGattNotInitialized;
import com.polar.androidcommunications.api.ble.exceptions.BleServiceNotFound;

import java.util.List;
import java.util.UUID;

/**
 * Note any of these functions might be called from different thread
 */
public interface BleGattTxInterface {
    void transmitMessages(UUID serviceUuid, UUID characteristicUuid, List<byte[]> packets, boolean withResponse) throws Exception;

    void transmitMessage(UUID serviceUuid, UUID characteristicUuid, byte[] packet, boolean withResponse) throws Exception;

    void readValue(UUID serviceUuid, UUID characteristicUuid) throws Exception;

    void setCharacteristicNotify(UUID serviceUuid, UUID characteristicUuid, boolean enable) throws BleCharacteristicNotFound, BleServiceNotFound, BleGattNotInitialized;

    boolean isConnected(); // for client

    // optional, request to stop and start scanning

    /**
     * gatt client requests to stop scanning, while there is some heavy attribute operation ongoing
     */
    void gattClientRequestStopScanning();

    /**
     * gatt client has completed heavy attribute operation, and scanning can continue if needed
     */
    void gattClientResumeScanning();

    /**
     * @return current att operation queue size
     */
    int transportQueueSize();
}
