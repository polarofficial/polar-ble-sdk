package com.polar.androidcommunications.api.ble.model.gatt.client;

import androidx.annotation.NonNull;

import com.polar.androidcommunications.api.ble.exceptions.BleCharacteristicNotFound;
import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected;
import com.polar.androidcommunications.api.ble.exceptions.BleNotSupported;
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase;
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleOnSubscribe;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class BleH7SettingsClient extends BleGattBase {
    public static final UUID H7_SETTINGS_CHARACTERISTIC = UUID.fromString("6217FF4A-B07D-5DEB-261E-2586752D942E");
    public static final UUID H7_SETTINGS_SERVICE = UUID.fromString("6217FF49-AC7B-547E-EECF-016A06970BA9");

    private final LinkedBlockingDeque<byte[]> h7InputQueue = new LinkedBlockingDeque<>();
    private final LinkedBlockingDeque<Integer> h7WrittenQueue = new LinkedBlockingDeque<>();

    private final Object mutex = new Object();

    public enum H7SettingsMessage {
        H7_UNKNOWN(0),
        H7_CONFIGURE_BROADCAST(1),
        H7_CONFIGURE_5KHZ(2),
        H7_REQUEST_CURRENT_SETTINGS(3);

        private final int numVal;

        H7SettingsMessage(int numVal) {
            this.numVal = numVal;
        }

        public int getNumVal() {
            return numVal;
        }
    }

    public static class H7SettingsResponse {
        private int broadcastValue;
        private int khzValue;

        public H7SettingsResponse() {
        }

        H7SettingsResponse(byte[] data) {
            khzValue = (data[0] & 0x02) >> 1;
            broadcastValue = (data[0] & 0x01);
        }

        H7SettingsResponse(int broadcastValue, int khzValue) {
            this.broadcastValue = broadcastValue;
            this.khzValue = khzValue;
        }

        public int getBroadcastValue() {
            return broadcastValue;
        }

        public int getKhzValue() {
            return khzValue;
        }

        @Override
        public String toString() {
            return "BC value: " + broadcastValue + " khz value: " + khzValue;
        }
    }

    public BleH7SettingsClient(BleGattTxInterface txInterface) {
        super(txInterface, H7_SETTINGS_SERVICE);
        addCharacteristic(H7_SETTINGS_CHARACTERISTIC);
    }

    @Override
    public void reset() {
        super.reset();
        synchronized (h7InputQueue) {
            h7InputQueue.clear();
            h7InputQueue.notifyAll();
        }
        synchronized (h7WrittenQueue) {
            h7WrittenQueue.clear();
            h7WrittenQueue.notifyAll();
        }
    }

    @Override
    public void processServiceData(UUID characteristic, byte[] data, int status, boolean notifying) {
        if (status == 0 && characteristic.equals(H7_SETTINGS_CHARACTERISTIC)) {
            synchronized (h7InputQueue) {
                h7InputQueue.push(data);
                h7InputQueue.notifyAll();
            }
        }
    }

    @Override
    public void processServiceDataWritten(UUID characteristic, int status) {
        // do nothing
        if (characteristic.equals(H7_SETTINGS_CHARACTERISTIC)) {
            synchronized (h7WrittenQueue) {
                h7WrittenQueue.push(status);
                h7WrittenQueue.notifyAll();
            }
        }
    }

    @Override
    public @NonNull
    String toString() {
        return "Legacy H7 settings client";
    }

    private byte[] readSettingsValue() throws Exception {
        txInterface.readValue(H7_SETTINGS_SERVICE, H7_SETTINGS_CHARACTERISTIC);
        byte[] packet = h7InputQueue.poll(30, TimeUnit.SECONDS);
        if (packet != null) {
            return packet;
        } else {
            if (!txInterface.isConnected()) {
                throw new BleDisconnected();
            } else {
                throw new Exception("Failed to receive packet in timeline");
            }
        }
    }

    /**
     * @param command   H7_CONFIGURE_BROADCAST | H7_CONFIGURE_5KHZ: writes broadcast value to desired, contains 3 operations
     *                  1. inital value read 2. updated value write 3. updated value read
     *                  H7_REQUEST_CURRENT_SETTINGS: reads current settings
     * @param parameter byte with value 1=on or 0=off
     * @return Single stream
     */
    public Single<H7SettingsResponse> sendSettingsCommand(final H7SettingsMessage command, final byte parameter) {
        return Single.create((SingleOnSubscribe<H7SettingsResponse>) emitter -> {
            final boolean has = getAvailableCharacteristics().contains(H7_SETTINGS_CHARACTERISTIC);
            try {
                synchronized (mutex) {
                    if (txInterface.isConnected()) {
                        if (has) {
                            try {
                                h7InputQueue.clear();
                                int khzValue;
                                int broadcastValue;
                                byte[] packet = BleH7SettingsClient.this.readSettingsValue();
                                khzValue = (packet[0] & 0x02) >> 1;
                                broadcastValue = packet[0] & 0x01;

                                switch (command) {
                                    case H7_CONFIGURE_5KHZ:
                                    case H7_CONFIGURE_BROADCAST: {
                                        byte[] values = new byte[1];
                                        if (command == H7SettingsMessage.H7_CONFIGURE_BROADCAST) {
                                            values[0] = (byte) ((khzValue << 1) | parameter);
                                        } else {
                                            values[0] = (byte) ((parameter << 1) | broadcastValue);
                                        }
                                        txInterface.transmitMessages(H7_SETTINGS_SERVICE,
                                                H7_SETTINGS_CHARACTERISTIC,
                                                Collections.singletonList(values), true);
                                        Integer error = h7WrittenQueue.poll(30, TimeUnit.SECONDS);
                                        if (error != null) {
                                            if (error == 0) {
                                                emitter.onSuccess(new H7SettingsResponse(values));
                                                return;
                                            } else {
                                                throw new Exception("Failed to write settings: " + error);
                                            }
                                        } else {
                                            if (!txInterface.isConnected()) {
                                                throw new BleDisconnected();
                                            } else {
                                                throw new Exception("Failed to write packet in timeline");
                                            }
                                        }
                                    }
                                    case H7_REQUEST_CURRENT_SETTINGS: {
                                        emitter.onSuccess(new H7SettingsResponse(khzValue, broadcastValue));
                                        return;
                                    }
                                    default:
                                        throw new BleNotSupported("Unknown h7 command");
                                }
                            } catch (Exception e) {
                                if (!emitter.isDisposed()) {
                                    throw e;
                                }
                            }
                        } else {
                            throw new BleCharacteristicNotFound();
                        }
                    } else {
                        throw new BleDisconnected();
                    }
                }
            } catch (Exception e) {
                if (!emitter.isDisposed()) {
                    emitter.tryOnError(e);
                }
            }
        }).subscribeOn(Schedulers.io());
    }
}
