package com.androidcommunications.polar.enpoints.ble.common.attribute;

import android.bluetooth.BluetoothGattCharacteristic;
import android.support.annotation.NonNull;

public class AttributeOperation implements Comparable<AttributeOperation>{

    @Override
    public int compareTo(@NonNull AttributeOperation another) {
        if( another.getAttributeOperation() == AttributeOperation.AttributeOperationCommand.DESCRIPTOR_WRITE &&
            another.isPartOfPrimaryService() ){
            return 1;
        } else if( another.getAttributeOperation() == AttributeOperation.AttributeOperationCommand.DESCRIPTOR_WRITE ){
            return 0;
        }
        return -1;
    }

    public enum AttributeOperationCommand{
        CHARACTERISTIC_READ,
        CHARACTERISTIC_WRITE,
        DESCRIPTOR_WRITE,
        CHARACTERISTIC_NOTIFY // add more later
    }

    private AttributeOperationCommand attributeOperation;
    private byte[] data;
    private BluetoothGattCharacteristic characteristic;
    private boolean isPartOfPrimaryService=false;
    private boolean enable=false;
    private boolean withResponse=false;

    public void setIsPartOfPrimaryService(boolean isPartOfPrimaryService) {
        this.isPartOfPrimaryService = isPartOfPrimaryService;
    }

    private boolean isPartOfPrimaryService() {
        return isPartOfPrimaryService;
    }

    public AttributeOperation(AttributeOperationCommand attributeOperation,
                              BluetoothGattCharacteristic characteristic) {
        this.attributeOperation = attributeOperation;
        this.characteristic = characteristic;
    }

    public AttributeOperation(AttributeOperationCommand attributeOperation,
                              BluetoothGattCharacteristic characteristic,
                              boolean enable) {
        this.attributeOperation = attributeOperation;
        this.characteristic = characteristic;
        this.enable = enable;
    }

    public AttributeOperation(AttributeOperationCommand attributeOperation,
                              byte[] data,
                              BluetoothGattCharacteristic characteristic,
                              final boolean withResponse) {
        this.attributeOperation = attributeOperation;
        this.data = data;
        this.characteristic = characteristic;
        this.withResponse = withResponse;
    }

    public AttributeOperationCommand getAttributeOperation() {
        return attributeOperation;
    }

    public byte[] getData() {
        return data;
    }

    public BluetoothGattCharacteristic getCharacteristic() {
        return characteristic;
    }

    public boolean isEnable() {
        return enable; // notification or indication
    }

    public boolean isWithResponse() {
        return withResponse;
    }
}
