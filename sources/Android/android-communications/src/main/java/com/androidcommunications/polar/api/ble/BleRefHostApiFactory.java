package com.androidcommunications.polar.api.ble;

public class BleRefHostApiFactory {

    private static BleRefHostApiFactory instance = new BleRefHostApiFactory();
    private BleDeviceListener deviceListener;

    private BleRefHostApiFactory(){
    }

    public static BleRefHostApiFactory getInstance(){
        return instance;
    }

    public void setDeviceListener(BleDeviceListener deviceListener){
        this.deviceListener = deviceListener;
    }

    public BleDeviceListener getDeviceListener(){
        return deviceListener;
    }
}
