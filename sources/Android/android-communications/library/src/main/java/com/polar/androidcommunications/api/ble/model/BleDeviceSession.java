package com.polar.androidcommunications.api.ble.model;

import android.bluetooth.BluetoothDevice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.polar.androidcommunications.api.ble.model.advertisement.BleAdvertisementContent;
import com.polar.androidcommunications.api.ble.model.advertisement.BlePolarHrAdvertisement;
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Function;

/**
 * Bluetooth le device class, contains all essential api's for sufficient usage of bluetooth le device
 */
public abstract class BleDeviceSession {

    /**
     * Connection state
     */
    public enum DeviceSessionState {
        /**
         * Disconnected state
         */
        SESSION_CLOSED,
        /**
         * Connection attempting/connecting at the moment
         */
        SESSION_OPENING,
        /**
         * Device is disconnected, but is waiting for advertisement head or ble power on for reconnection
         */
        SESSION_OPEN_PARK,
        /**
         * Device is connected
         */
        SESSION_OPEN,
        /**
         * Disconnecting at the moment
         */
        SESSION_CLOSING,
    }

    /**
     * Members
     */
    @NonNull
    protected DeviceSessionState state = DeviceSessionState.SESSION_CLOSED;
    @NonNull
    protected DeviceSessionState previousState = DeviceSessionState.SESSION_CLOSED;
    protected Set<BleGattBase> clients;
    // needs to be set by 'upper' class
    protected BleAdvertisementContent advertisementContent = new BleAdvertisementContent();
    private final List<String> connectionUuids = new ArrayList<>();

    /**
     * Methods
     * <p>
     * Class constructor
     */
    protected BleDeviceSession() {
    }

    /**
     * @param time     max time for the last advertisement event
     * @param timeUnit desired timeunit
     * @return true, device is still somewhat considered "alive"
     */
    public boolean isDeviceAlive(long time, TimeUnit timeUnit) {
        long deltaTime = (System.currentTimeMillis() / 1000L) - advertisementContent.getAdvertisementTimeStamp();
        DeviceSessionState sessionState = getSessionState();
        return (sessionState == DeviceSessionState.SESSION_OPEN ||
                sessionState == DeviceSessionState.SESSION_OPENING ||
                sessionState == DeviceSessionState.SESSION_CLOSING) ||
                (advertisementContent.getAdvertisementData().size() != 0 && deltaTime <= timeUnit.toSeconds(time));
    }

    /**
     * @param time     max time for the last advertisement event
     * @param timeUnit desired timeunit
     * @return true if device is advertising in the required timespan
     */
    public boolean isAdvertising(long time, TimeUnit timeUnit) {
        long deltaTime = (System.currentTimeMillis() / 1000L) - advertisementContent.getAdvertisementTimeStamp();
        return advertisementContent.getAdvertisementData().size() != 0 && deltaTime <= timeUnit.toSeconds(time);
    }

    /**
     * @return true if device is in non-connectable advertisement<BR>
     * Notes: Depending on Android API version,it's impossible to know this.<BR>
     * So this would work only for Polar Devices that follows Polar SAGRFC31
     */
    public abstract boolean isNonConnectableAdvertisement();

    /**
     * @return true if device is connectable advertisement<BR>
     * Notes: Depending on Android API version,it's impossible to know this.<BR>
     * So this would work only for Polar Devices that follows Polar SAGRFC31
     */
    public boolean isConnectableAdvertisement() {
        return !isNonConnectableAdvertisement();
    }

    /**
     * @return bluetooth device address in string format
     */
    public abstract String getAddress();

    /**
     * start pairing and optionally bonding procedure with the device
     *
     * @return Completable either produces complete for already bonded device or starts pairing/bonding procedure
     * or onError on failure case
     */
    public abstract Completable authenticate();

    /**
     * @return true if session is bonded device
     */
    public abstract boolean isAuthenticated();

    /**
     * Monitor services discovered
     *
     * @return Observable stream
     */
    public abstract Single<List<UUID>> monitorServicesDiscovered(final boolean checkConnection);

    /**
     * @return true if current gatt cache clear was ok
     */
    public abstract boolean clearGattCache();

    /**
     * @return Observable stream for rssi values
     */
    public abstract Single<Integer> readRssiValue();

    /**
     * @return get current state (DeviceSessionState.SESSION_XXXXX)
     */
    @NonNull
    public DeviceSessionState getSessionState() {
        return state;
    }

    /**
     * @return state before current one
     */
    @NonNull
    public DeviceSessionState getPreviousState() {
        return previousState;
    }

    /**
     * @param uuid service uuid
     * @return get a specific client from the list
     */
    @Nullable
    public BleGattBase fetchClient(@NonNull UUID uuid) {
        for (BleGattBase serviceBase : clients) {
            if (serviceBase.serviceBelongsToClient(uuid)) {
                return serviceBase;
            }
        }
        return null;
    }

    /**
     * Helper to combine all available/desired clients ready
     *
     * @param checkConnection check initial connection
     * @return Observable stream
     */
    public Completable clientsReady(final boolean checkConnection) {
        return Completable.fromPublisher(
                monitorServicesDiscovered(checkConnection)
                        .toFlowable()
                        .flatMapIterable((Function<List<UUID>, Iterable<UUID>>) uuids -> uuids)
                        .flatMap(
                                uuid -> {
                                    BleGattBase bleGattBase = fetchClient(uuid);
                                    if (bleGattBase != null) {
                                        return bleGattBase.clientReady(checkConnection).toFlowable();
                                    }
                                    return Completable.fromPublisher(Flowable.empty()).toFlowable();
                                }));
    }

    /**
     * @return advertisement content object
     */
    @NonNull
    public BleAdvertisementContent getAdvertisementContent() {
        return advertisementContent;
    }

    /**
     * @param uuids set connection uuid's
     */
    public void setConnectionUuids(List<String> uuids) {
        connectionUuids.clear();
        connectionUuids.addAll(uuids);
    }

    /**
     * @return android bluetooth device instance
     */
    public abstract BluetoothDevice getBluetoothDevice();

    /**
     * @return current connection uuid's needed for connection attempt
     */
    public List<String> getConnectionUuids() {
        return connectionUuids;
    }

    // adv data getter helpers
    public String getName() {
        return advertisementContent.getName();
    }

    /**
     * @return polar device id
     */
    public String getPolarDeviceId() {
        return advertisementContent.getPolarDeviceId();
    }

    /**
     * @return polar device type
     */
    @NonNull
    public String getPolarDeviceType() {
        return advertisementContent.getPolarDeviceType();
    }

    /**
     * @return polar device id in int
     */
    public long getPolarDeviceIdInt() {
        return advertisementContent.getPolarDeviceIdInt();
    }

    /**
     * @return current median rssi value
     */
    public int getMedianRssi() {
        return advertisementContent.getMedianRssi();
    }

    /**
     * @return instant rssi value
     */
    public int getRssi() {
        return advertisementContent.getRssi();
    }

    /**
     * @return polar hr advertisement content @see BlePolarHrAdvertisement
     */
    public BlePolarHrAdvertisement getBlePolarHrAdvertisement() {
        return advertisementContent.getPolarHrAdvertisement();
    }
}
