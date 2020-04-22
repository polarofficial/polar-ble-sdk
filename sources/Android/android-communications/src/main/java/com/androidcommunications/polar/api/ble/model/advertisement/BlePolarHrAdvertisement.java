package com.androidcommunications.polar.api.ble.model.advertisement;

public class BlePolarHrAdvertisement {

    private int batteryStatus;
    private int sensorContact;
    private int ucAdvFrameCounter;
    private int broadcastBit;
    private int sensorDataType;
    private int statusFlags;
    private int khzCode;
    private int fastAverageHr;
    private int slowAverageHr;
    private byte[] currentData;

    void processPolarManufacturerData(byte[] data) {
        currentData = data;
        batteryStatus = data[2] & 0x01;
        sensorContact = (data[2] & 0x02) >> 1;
        ucAdvFrameCounter = (data[2] & 0x1C) >> 2;
        broadcastBit = (data[2] & 0x20) >> 5;
        sensorDataType = (data[2] & 0x40) >> 6;
        statusFlags = (data[2] & 0x80) >> 7;
        khzCode = data[3];
        fastAverageHr = (data[4] & 0x000000FF);
        if (data.length == 6) {
            slowAverageHr = (data[5] & 0x000000FF);
        } else {
            slowAverageHr = (data[4] & 0x000000FF);
        }
    }

    public boolean isPresent() {
        return currentData != null;
    }

    public int getBatteryStatus() {
        return batteryStatus;
    }

    public int getSensorContact() {
        return sensorContact;
    }

    public int getUcAdvFrameCounter() {
        return ucAdvFrameCounter;
    }

    public int getBroadcastBit() {
        return broadcastBit;
    }

    public int getSensorDataType() {
        return sensorDataType;
    }

    public int getStatusFlags() {
        return statusFlags;
    }

    public int getKhzCode() {
        return khzCode;
    }

    public int getFastAverageHr() {
        return fastAverageHr;
    }

    public int getSlowAverageHr() {
        return slowAverageHr;
    }

    public int getHrForDisplay() {
        return slowAverageHr;
    }

    public boolean isOldH7H6() {
        return currentData.length == 5;
    }

    public boolean isH7Update() {
        return !isOldH7H6();
    }
}
