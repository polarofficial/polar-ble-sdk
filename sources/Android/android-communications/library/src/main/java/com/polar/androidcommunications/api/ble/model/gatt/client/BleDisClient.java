package com.polar.androidcommunications.api.ble.model.gatt.client;

import android.util.Pair;

import androidx.annotation.NonNull;

import com.polar.androidcommunications.api.ble.exceptions.BleAttributeError;
import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected;
import com.polar.androidcommunications.api.ble.model.DisInfo;
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase;
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface;
import com.polar.androidcommunications.common.ble.AtomicSet;
import com.polar.androidcommunications.common.ble.RxUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.FlowableEmitter;
import io.reactivex.rxjava3.core.FlowableOnSubscribe;

public class BleDisClient extends BleGattBase {

    private static final String TAG = BleDisClient.class.getSimpleName();

    public static final UUID DIS_SERVICE = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
    public static final UUID MODEL_NUMBER_STRING = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb");
    public static final UUID MANUFACTURER_NAME_STRING = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb");
    public static final UUID HARDWARE_REVISION_STRING = UUID.fromString("00002a27-0000-1000-8000-00805f9b34fb");
    public static final UUID FIRMWARE_REVISION_STRING = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb");
    public static final UUID SOFTWARE_REVISION_STRING = UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb");
    public static final UUID SERIAL_NUMBER_STRING = UUID.fromString("00002a25-0000-1000-8000-00805f9b34fb");
    public static final UUID SYSTEM_ID = UUID.fromString("00002a23-0000-1000-8000-00805f9b34fb");
    public static final UUID IEEE_11073_20601 = UUID.fromString("00002a2a-0000-1000-8000-00805f9b34fb");
    public static final UUID PNP_ID = UUID.fromString("00002a50-0000-1000-8000-00805f9b34fb");

    public static final String SYSTEM_ID_HEX = "SYSTEM_ID_HEX";

    // store in map
    private final HashMap<UUID, String> disInformation = new HashMap<>();
    private final AtomicSet<FlowableEmitter<? super Pair<UUID, String>>> disObserverAtomicList = new AtomicSet<>();

    private final Set<DisInfo> disInformationDataSet = new HashSet<>();
    private final AtomicSet<FlowableEmitter<DisInfo>> disInfoObservers = new AtomicSet<>();

    public BleDisClient(BleGattTxInterface txInterface) {
        super(txInterface, DIS_SERVICE);
        addCharacteristicRead(MODEL_NUMBER_STRING);
        addCharacteristicRead(MANUFACTURER_NAME_STRING);
        addCharacteristicRead(HARDWARE_REVISION_STRING);
        addCharacteristicRead(FIRMWARE_REVISION_STRING);
        addCharacteristicRead(SOFTWARE_REVISION_STRING);
        addCharacteristicRead(SERIAL_NUMBER_STRING);
        addCharacteristicRead(SYSTEM_ID);
        addCharacteristicRead(IEEE_11073_20601);
        addCharacteristicRead(PNP_ID);
    }

    @Override
    public void reset() {
        super.reset();
        synchronized (disInformation) {
            disInformation.clear();
            disInformationDataSet.clear();
        }
        RxUtils.postDisconnectedAndClearList(disObserverAtomicList);
        RxUtils.postDisconnectedAndClearList(disInfoObservers);
    }

    @Override
    public void processServiceData(final UUID characteristic, final byte[] data, int status, boolean notifying) {
        if (status == 0) {
            final String asciiRepresentation = new String(data, StandardCharsets.UTF_8);
            synchronized (disInformation) {
                disInformation.put(characteristic, asciiRepresentation);
            }
            synchronized (disInformationDataSet) {
                if (characteristic.equals(BleDisClient.SYSTEM_ID)) {
                    final String hexRepresentation = systemIdBytesToHex(data);
                    disInformationDataSet.add(new DisInfo(BleDisClient.SYSTEM_ID_HEX, hexRepresentation));
                } else {
                    disInformationDataSet.add(new DisInfo(characteristic.toString(), asciiRepresentation));
                }
            }
            RxUtils.emitNext(disObserverAtomicList, object -> {
                object.onNext(new Pair<>(characteristic, new String(data, StandardCharsets.UTF_8)));
                synchronized (disInformation) {
                    if (hasAllAvailableReadableCharacteristics(disInformation.keySet())) {
                        object.onComplete();
                    }
                }
            });

            RxUtils.emitNext(disInfoObservers, object -> {
                disInformationDataSet.stream()
                        .filter(info -> (characteristic.equals(BleDisClient.SYSTEM_ID)
                                && info.getKey().equals(BleDisClient.SYSTEM_ID_HEX))
                                || (characteristic.toString().equals(info.getKey())))
                        .findFirst().ifPresent(object::onNext);

                synchronized (disInformationDataSet) {
                    final Set<UUID> validUuids = disInformationDataSet.stream()
                            .map(DisInfo::getKey)
                            .filter(this::isValidUUIDString)
                            .map(UUID::fromString)
                            .collect(Collectors.toSet());

                    if (hasAllAvailableReadableCharacteristics(validUuids)) {
                        object.onComplete();
                    }
                }
            });
        } else {
            RxUtils.postError(disObserverAtomicList, new BleAttributeError("dis ", status));
        }
    }

    @Override
    public void processServiceDataWritten(UUID characteristic, int status) {
        // do nothing
    }

    @Override
    public @NonNull
    String toString() {
        return "Device info service";
    }

    /**
     * Produces:  onNext, when a dis data has been read <BR>
     * onCompleted, after all available dis info has been read <BR>
     * onError, if client is not initially connected or ble disconnect's  <BR>
     *
     * @param checkConnection, optionally check connection on subscribe <BR>
     * @return Flowable stream <BR>
     */
    public Flowable<Pair<UUID, String>> observeDisInfo(final boolean checkConnection) {
        final FlowableEmitter<? super Pair<UUID, String>>[] observer = new FlowableEmitter[1];
        return Flowable.create((FlowableOnSubscribe<Pair<UUID, String>>) subscriber -> {
            if (!checkConnection || BleDisClient.this.txInterface.isConnected()) {
                observer[0] = subscriber;
                disObserverAtomicList.add(subscriber);
                synchronized (disInformation) {
                    for (UUID e : disInformation.keySet()) {
                        subscriber.onNext(new Pair<>(e, disInformation.get(e)));
                    }
                    if (hasAllAvailableReadableCharacteristics(disInformation.keySet())) {
                        subscriber.onComplete();
                    }
                }
            } else if (!subscriber.isCancelled()) {
                subscriber.tryOnError(new BleDisconnected());
            }
        }, BackpressureStrategy.BUFFER).doFinally(() -> disObserverAtomicList.remove(observer[0]));
    }

    /**
     * Produces: onNext, when a {@link DisInfo} has been read <BR>
     * onCompleted, after all available {@link DisInfo} has been read <BR>
     * onError, if client is not initially connected or ble disconnect's  <BR>
     *
     * @param checkConnection, optionally check connection on subscribe <BR>
     * @return Flowable stream emitting {@link DisInfo} <BR>
     */
    public Flowable<DisInfo> observeDisInfoWithKeysAsStrings(final boolean checkConnection) {
        final FlowableEmitter<DisInfo>[] observer = new FlowableEmitter[1];
        return Flowable.create((FlowableOnSubscribe<DisInfo>) subscriber -> {
                    if (!checkConnection || BleDisClient.this.txInterface.isConnected()) {
                        observer[0] = subscriber;
                        disInfoObservers.add(subscriber);

                        synchronized (disInformationDataSet) {
                            for (DisInfo disInfo : disInformationDataSet) {
                                subscriber.onNext(disInfo);
                            }

                            final Set<UUID> validUuids = disInformationDataSet.stream()
                                    .filter(disInfo -> isValidUUIDString(disInfo.getKey()))
                                    .map(disInfo -> UUID.fromString(disInfo.getKey()))
                                    .collect(Collectors.toSet());

                            if (hasAllAvailableReadableCharacteristics(validUuids)) {
                                subscriber.onComplete();
                            }
                        }
                    } else if (!subscriber.isCancelled()) {
                        subscriber.tryOnError(new BleDisconnected());
                    }
                }, BackpressureStrategy.BUFFER)
                .doFinally(() -> disInfoObservers.remove(observer[0]));
    }

    private String systemIdBytesToHex(final byte[] bytes) {
        final StringBuilder hex = new StringBuilder(2 * bytes.length);
        for (int i = bytes.length - 1; i >= 0; i--) {
            hex.append(String.format("%02X", bytes[i]));
        }
        return hex.toString();
    }

    private boolean isValidUUIDString(final String s) {
        try {
            UUID.fromString(s);
            return true;
        } catch (final IllegalArgumentException e) {
            return false;
        }
    }
}

