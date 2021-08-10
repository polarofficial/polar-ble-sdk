package com.polar.androidcommunications.enpoints.ble.bluedroid.host;

import android.bluetooth.BluetoothGattCharacteristic;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class AttributeOperation implements Comparable<AttributeOperation> {

    @Override
    public int compareTo(@NonNull AttributeOperation another) {
        if (another.getAttributeOperation() == AttributeOperation.AttributeOperationCommand.DESCRIPTOR_WRITE &&
                another.isPartOfPrimaryService()) {
            return 1;
        } else if (another.getAttributeOperation() == AttributeOperation.AttributeOperationCommand.DESCRIPTOR_WRITE) {
            return 0;
        }
        return -1;
    }

    public enum AttributeOperationCommand {
        CHARACTERISTIC_READ,
        CHARACTERISTIC_WRITE,
        DESCRIPTOR_WRITE,
        CHARACTERISTIC_NOTIFY // add more later
    }

    private final AttributeOperationCommand attributeOperation;
    private byte[] data;
    private final BluetoothGattCharacteristic characteristic;
    private boolean isPartOfPrimaryService = false;
    private boolean enable = false;
    private boolean withResponse = false;

    void setIsPartOfPrimaryService(boolean isPartOfPrimaryService) {
        this.isPartOfPrimaryService = isPartOfPrimaryService;
    }

    private boolean isPartOfPrimaryService() {
        return isPartOfPrimaryService;
    }

    AttributeOperation(AttributeOperationCommand attributeOperation,
                       BluetoothGattCharacteristic characteristic) {
        this.attributeOperation = attributeOperation;
        this.characteristic = characteristic;
    }

    AttributeOperation(AttributeOperationCommand attributeOperation,
                       BluetoothGattCharacteristic characteristic,
                       boolean enable) {
        this.attributeOperation = attributeOperation;
        this.characteristic = characteristic;
        this.enable = enable;
    }

    AttributeOperation(AttributeOperationCommand attributeOperation,
                       byte[] data,
                       BluetoothGattCharacteristic characteristic,
                       final boolean withResponse) {
        this.attributeOperation = attributeOperation;
        this.data = data;
        this.characteristic = characteristic;
        this.withResponse = withResponse;
    }

    AttributeOperationCommand getAttributeOperation() {
        return attributeOperation;
    }

    @Nullable
    public byte[] getData() {
        return data;
    }

    @NonNull
    public BluetoothGattCharacteristic getCharacteristic() {
        return characteristic;
    }

    public boolean isEnable() {
        return enable; // notification or indication
    }

    boolean isWithResponse() {
        return withResponse;
    }
}