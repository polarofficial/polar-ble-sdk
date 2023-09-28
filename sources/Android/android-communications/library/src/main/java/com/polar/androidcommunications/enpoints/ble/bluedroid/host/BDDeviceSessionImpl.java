package com.polar.androidcommunications.enpoints.ble.bluedroid.host;

import static com.polar.androidcommunications.common.ble.AndroidBuildUtils.getBrand;
import static com.polar.androidcommunications.common.ble.AndroidBuildUtils.getBuildVersion;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothStatusCodes;
import android.content.Context;
import android.os.Build;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.polar.androidcommunications.BuildConfig;
import com.polar.androidcommunications.api.ble.BleLogger;
import com.polar.androidcommunications.api.ble.exceptions.BleCharacteristicNotFound;
import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected;
import com.polar.androidcommunications.api.ble.exceptions.BleGattNotInitialized;
import com.polar.androidcommunications.api.ble.exceptions.BleNotSupported;
import com.polar.androidcommunications.api.ble.exceptions.BleServiceNotFound;
import com.polar.androidcommunications.api.ble.model.BleDeviceSession;
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase;
import com.polar.androidcommunications.api.ble.model.gatt.BleGattFactory;
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface;
import com.polar.androidcommunications.common.ble.AtomicSet;
import com.polar.androidcommunications.common.ble.RxUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleEmitter;
import io.reactivex.rxjava3.core.SingleOnSubscribe;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Action;

public class BDDeviceSessionImpl extends BleDeviceSession implements BleGattTxInterface {

    private static final UUID DESCRIPTOR_CCC = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final String TAG = BDDeviceSessionImpl.class.getSimpleName();
    // gatt is the only shared object between threads
    private final Object gattMutex = new Object();
    Disposable serviceDiscovery;

    private final LinkedBlockingDeque<AttributeOperation> attOperations = new LinkedBlockingDeque<>();

    private BluetoothDevice bluetoothDevice;
    private BluetoothGatt gatt;
    private final BDScanCallback bleScanCallback;
    private final BDBondingListener bondingManager;
    private final AtomicSet<SingleEmitter<? super List<UUID>>> servicesSubscriberAtomicList = new AtomicSet<>();
    private final AtomicSet<SingleEmitter<? super Integer>> rssiObservers = new AtomicSet<>();
    private final List<Disposable> subscriptions = new ArrayList<>();
    private final Context context;
    private final Handler handler;

    private final List<String> brandsNotImplementingAndroid13Api = Arrays.asList("OnePlus", "Oppo", "Realme");

    BDDeviceSessionImpl(Context context,
                        BluetoothDevice bluetoothDevice,
                        BDScanCallback scanCallback,
                        BDBondingListener bondingManager,
                        BleGattFactory factory) {
        super();
        this.context = context;
        this.handler = new Handler(context.getMainLooper());
        this.bluetoothDevice = bluetoothDevice;
        this.bleScanCallback = scanCallback;
        this.bondingManager = bondingManager;
        this.clients = factory.getRemoteServices(this);
    }

    public BluetoothDevice getBluetoothDevice() {
        return bluetoothDevice;
    }

    void setBluetoothDevice(BluetoothDevice bluetoothDevice) {
        this.bluetoothDevice = bluetoothDevice;
        // clear all
        resetGatt();
    }

    @Nullable
    BluetoothGatt getGatt() {
        return gatt;
    }

    void setGatt(BluetoothGatt gatt) {
        synchronized (getGattMutex()) {
            this.gatt = gatt;
        }
    }

    @NonNull
    Object getGattMutex() {
        return gattMutex;
    }

    AtomicSet<SingleEmitter<? super Integer>> getRssiObservers() {
        return rssiObservers;
    }

    @SuppressLint("MissingPermission")
    void resetGatt() {
        synchronized (gattMutex) {
            if (gatt != null) {
                try {
                    //gatt.disconnect();
                    gatt.close();
                } catch (Exception e) {
                    BleLogger.e(TAG, "gatt error: " + e);
                }
                gatt = null;
            }
        }
    }

    private void logIfError(final String message, int status) {
        if (status != 0) {
            BleLogger.e(TAG, message + " Failed with error: " + status);
        }
    }

    /**
     * Internal use only
     *
     * @param sessionState @see BleDeviceSession.DeviceSessionState
     */
    public void setSessionState(@NonNull BleDeviceSession.DeviceSessionState sessionState) {
        this.previousState = this.state;
        this.state = sessionState;
    }

    public void reset() {
        BleLogger.d(TAG, "reset");
        resetGatt();
    }

    @Override
    public boolean isNonConnectableAdvertisement() {
        return advertisementContent.isNonConnectableAdvertisement();
    }

    @Override
    public String getAddress() {
        return bluetoothDevice.getAddress();
    }

    @Override
    @SuppressLint("MissingPermission")
    public Completable authenticate() {
        final BDBondingListener.BondingObserver[] observer = {null};
        return Completable.create(subscriber -> {
            if (getSessionState() == DeviceSessionState.SESSION_OPEN) {
                switch (bluetoothDevice.getBondState()) {
                    case BluetoothDevice.BOND_NONE: {
                        if (!bluetoothDevice.createBond()) {
                            subscriber.tryOnError(new Throwable("BD bonding start failed"));
                            return;
                        }
                    }
                    case BluetoothDevice.BOND_BONDING: {
                        observer[0] = new BDBondingListener.BondingObserver(bluetoothDevice) {
                            @Override
                            public void bonding() {
                            }

                            @Override
                            public void bonded() {
                                subscriber.onComplete();
                            }

                            @Override
                            public void bondNone() {
                                // BIG NOTE, don't produce error, sometimes android produces bond none before bonded or bonding event
                                // subscriber.onError(new Throwable("Bonding failed for unknown reason"));
                            }
                        };
                        bondingManager.addObserver(observer[0]);
                        break;
                    }
                    case BluetoothDevice.BOND_BONDED: {
                        subscriber.onComplete();
                        break;
                    }
                }
            } else {
                subscriber.onError(new BleDisconnected());
            }
        }).doFinally(() -> bondingManager.removeObserver(observer[0]));
    }

    @SuppressLint("MissingPermission")
    @Override
    public boolean isAuthenticated() {
        return bluetoothDevice.getBondState() == BluetoothDevice.BOND_BONDED;
    }

    @Override
    public boolean clearGattCache() {
        boolean result = false;
        synchronized (gattMutex) {
            if (gatt != null) {
                try {
                    Method localMethod = gatt.getClass().getMethod("refresh");
                    result = ((Boolean) localMethod.invoke(gatt, new Object[0])).booleanValue();
                } catch (Exception localException) {
                    BleLogger.e(TAG, "An exception occured while refreshing device");
                }
            }
        }
        return result;
    }

    @SuppressLint("MissingPermission")
    @Override
    public Single<Integer> readRssiValue() {
        final SingleEmitter<? super Integer>[] observer = new SingleEmitter[1];
        return Single.create(
                (SingleOnSubscribe<Integer>) subscriber -> {
                    if (getSessionState() == DeviceSessionState.SESSION_OPEN) {
                        synchronized (gattMutex) {
                            if (gatt != null) {
                                if (gatt.readRemoteRssi()) {
                                    observer[0] = subscriber;
                                    rssiObservers.add(subscriber);
                                    return;
                                }
                                subscriber.tryOnError(new Throwable("Failed to read rssi"));
                                return;
                            }
                            subscriber.tryOnError(new Throwable("Gatt not initialized"));
                            return;
                        }
                    }
                    subscriber.tryOnError(new BleDisconnected());
                }).doFinally(() -> rssiObservers.remove(observer[0]));
    }

    @SuppressLint({"NewApi", "MissingPermission"})
    private boolean sendNextAttributeOperation(AttributeOperation operation) throws Throwable {
        BluetoothGattCharacteristic characteristic = operation.getCharacteristic();
        synchronized (getGattMutex()) {
            if (gatt != null) {
                switch (operation.getAttributeOperation()) {
                    case CHARACTERISTIC_READ: {
                        return getGatt().readCharacteristic(characteristic);
                    }
                    case CHARACTERISTIC_WRITE: {
                        int writeType;
                        if (operation.isWithResponse() && (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
                            writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
                        } else if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                            writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
                        } else {
                            BleLogger.e(TAG, "Undefined state. BluetoothGattCharacteristic write type cannot be defined.");
                            return false;
                        }

                        if (getBuildVersion() >= Build.VERSION_CODES.TIRAMISU) {
                            int status = gatt.writeCharacteristic(characteristic, operation.getData(), writeType);
                            if (status == BluetoothStatusCodes.SUCCESS) {
                                return true;
                            } else {
                                BleLogger.e(TAG, "Error: characteristic write failed. Reason: " + status);
                                return false;
                            }
                        } else {
                            characteristic.setWriteType(writeType);
                            characteristic.setValue(operation.getData());
                            return gatt.writeCharacteristic(characteristic);
                        }
                    }
                    case DESCRIPTOR_WRITE: {
                        BluetoothGattDescriptor descriptor = operation.getCharacteristic().getDescriptor(DESCRIPTOR_CCC);
                        byte[] value;
                        if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            value = operation.isEnable() ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
                        } else if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0) {
                            value = operation.isEnable() ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
                        } else {
                            return false;
                        }
                        gatt.setCharacteristicNotification(characteristic, operation.isEnable());

                        //Note, some manufacturers, e.g. OnePlus haven't properly implemented the new API.
                        //Ignore with third party devices.
                        final boolean isThirdPartyDevice = getPolarDeviceType().isEmpty();
                        if (getBuildVersion() >= Build.VERSION_CODES.TIRAMISU
                                && (isBrandImplementingAndroid13Api(getBrand()) || isThirdPartyDevice)) {
                            int status = gatt.writeDescriptor(descriptor, value);
                            if (status == BluetoothStatusCodes.SUCCESS) {
                                return true;
                            } else {
                                BleLogger.e(TAG, "Error: descriptor write failed. Reason: " + status);
                                return false;
                            }
                        } else {
                            BleLogger.d(TAG, "using deprecated descriptor write");
                            descriptor.setValue(value);
                            return gatt.writeDescriptor(descriptor);
                        }
                    }
                    default: {
                        throw new BleNotSupported("not supported");
                    }
                }
            } else {
                throw new BleGattNotInitialized("Attribute operation tried while gatt is uninitialized");
            }
        }
    }

    private boolean isBrandImplementingAndroid13Api(final String brandName) {
        return brandsNotImplementingAndroid13Api.stream().noneMatch(name -> name.equalsIgnoreCase(brandName));
    }

    @Override
    public void gattClientRequestStopScanning() {
        BleLogger.d(TAG, "GATT client request stop scanning");
        handler.post(bleScanCallback::stopScan);
    }

    @Override
    public void gattClientResumeScanning() {
        BleLogger.d(TAG, "GATT client request continue scanning");
        handler.post(bleScanCallback::startScan);
    }

    @Override
    public int transportQueueSize() {
        return attOperations.size();
    }

    void handleDisconnection() {
        BleLogger.d(TAG, "disconnected");
        advertisementContent.resetAdvertisementData();
        attOperations.clear();
        for (BleGattBase gattClient : clients) {
            gattClient.reset();
        }
        RxUtils.postDisconnectedAndClearList(servicesSubscriberAtomicList);
        RxUtils.postDisconnectedAndClearList(rssiObservers);
        for (Disposable subscription : subscriptions) {
            subscription.dispose();
        }
        subscriptions.clear();
        if (serviceDiscovery != null) {
            serviceDiscovery.dispose();
            serviceDiscovery = null;
        }
    }

    // GATT
    @Override
    public void transmitMessages(UUID serviceUuid, UUID characteristicUuid, List<byte[]> packets, boolean withResponse) throws Exception {
        // note most likely this comes from a different thread
        for (byte[] packet : packets) {
            transmitMessage(serviceUuid, characteristicUuid, packet, withResponse);
        }
    }

    @Override
    public void transmitMessage(UUID serviceUuid, UUID characteristicUuid, byte[] packet, boolean withResponse) throws Exception {
        // note most likely this comes from a different thread
        synchronized (gattMutex) {
            if (gatt != null) {
                for (BluetoothGattService service : gatt.getServices()) {
                    if (service.getUuid().equals(serviceUuid)) {
                        for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                            if (characteristic.getUuid().equals(characteristicUuid)) {
                                attOperations.add(new AttributeOperation(AttributeOperation.AttributeOperationCommand.CHARACTERISTIC_WRITE, packet, characteristic, withResponse));
                                if (attOperations.size() == 1) {
                                    processNextAttributeOperation(false);
                                }
                                return;
                            }
                        }
                        throw new BleCharacteristicNotFound();
                    }
                }
                throw new BleServiceNotFound();
            }
            throw new BleGattNotInitialized();
        }
    }

    @Override
    public void readValue(UUID serviceUuid, UUID characteristicUuid) throws Exception {
        synchronized (gattMutex) {
            if (gatt != null) {
                for (BluetoothGattService service : gatt.getServices()) {
                    if (service.getUuid().equals(serviceUuid)) {
                        for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                            if (characteristic.getUuid().equals(characteristicUuid)) {
                                attOperations.add(new AttributeOperation(AttributeOperation.AttributeOperationCommand.CHARACTERISTIC_READ, characteristic));
                                if (attOperations.size() == 1) {
                                    processNextAttributeOperation(false);
                                }
                                return;
                            }
                        }
                        throw new BleCharacteristicNotFound();
                    }
                }
                throw new BleServiceNotFound();
            }
            throw new BleGattNotInitialized();
        }
    }

    @Override
    public void setCharacteristicNotify(UUID serviceUuid, UUID characteristicUuid, boolean enable)
            throws BleCharacteristicNotFound, BleServiceNotFound, BleGattNotInitialized {
        synchronized (gattMutex) {
            if (gatt != null) {
                for (BluetoothGattService service : gatt.getServices()) {
                    if (service.getUuid().equals(serviceUuid)) {
                        for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                            if (characteristic.getUuid().equals(characteristicUuid)) {
                                if ((characteristic.getProperties() & BleGattBase.PROPERTY_NOTIFY) > 0
                                        || (characteristic.getProperties() & BleGattBase.PROPERTY_INDICATE) > 0) {
                                    attOperations.add(new AttributeOperation(AttributeOperation.AttributeOperationCommand.DESCRIPTOR_WRITE, characteristic, enable));
                                    if (attOperations.size() == 1) {
                                        processNextAttributeOperation(false);
                                    }
                                }
                                return;
                            }
                        }
                        throw new BleCharacteristicNotFound();
                    }
                }
                throw new BleServiceNotFound();
            }
            throw new BleGattNotInitialized();
        }
    }

    @Override
    public Single<List<UUID>> monitorServicesDiscovered(final boolean checkConnection) {
        final SingleEmitter<? super List<UUID>>[] observer = new SingleEmitter[1];
        return Single.create(
                (SingleOnSubscribe<List<UUID>>) subscriber -> {
                    if (checkConnection && getSessionState() != DeviceSessionState.SESSION_OPEN) {
                        subscriber.tryOnError(new BleDisconnected());
                    } else {
                        observer[0] = subscriber;
                        servicesSubscriberAtomicList.add(subscriber);
                        synchronized (gattMutex) {
                            if (gatt != null && !gatt.getServices().isEmpty()) {
                                List<BluetoothGattService> s = gatt.getServices();
                                List<UUID> uuids = new ArrayList<>();
                                for (BluetoothGattService service : s) {
                                    uuids.add(service.getUuid());
                                }
                                subscriber.onSuccess(uuids);
                            }
                        }
                    }
                }).doFinally(() -> servicesSubscriberAtomicList.remove(observer[0]));
    }

    @Override
    public boolean isConnected() {
        return getSessionState() == DeviceSessionState.SESSION_OPEN;
    }

    boolean isAuthenticationNeeded() {
        synchronized (gattMutex) {
            if (gatt != null) {
                for (BluetoothGattService service : gatt.getServices()) {
                    final BleGattBase client = fetchClient(service.getUuid());
                    if (client != null && client.isEncryptionRequired()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    void handleServicesDiscovered() {
        List<AttributeOperation> operations = new ArrayList<>();
        final List<UUID> serviceUuids = new ArrayList<>();
        synchronized (gattMutex) {
            for (BluetoothGattService service : gatt.getServices()) {
                serviceUuids.add(service.getUuid());
                handleServiceDiscovered(operations, service);
                for (BluetoothGattService includedService : service.getIncludedServices()) {
                    BleLogger.d(TAG, " INCLUDED SERVICE: " + includedService.getUuid());
                    serviceUuids.add(includedService.getUuid());
                    handleServiceDiscovered(operations, includedService);
                }
            }
        }
        RxUtils.emitNext(servicesSubscriberAtomicList,
                object -> {
                    List<UUID> services = new ArrayList<>(serviceUuids);
                    object.onSuccess(services);
                });
        Collections.sort(operations);
        this.attOperations.clear();
        this.attOperations.addAll(operations);
    }

    private void handleServiceDiscovered(List<AttributeOperation> operations, BluetoothGattService service) {
        final BleGattBase client = fetchClient(service.getUuid());
        BleLogger.d(TAG, " SERVICE: " + service.getUuid().toString());
        if (client != null) {
            client.setServiceDiscovered(true, service.getUuid());
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                BleLogger.d(TAG, "     CHARACTERISTIC: " + characteristic.getUuid().toString() + " PROPERTIES: " + characteristic.getProperties());
                client.processCharacteristicDiscovered(characteristic.getUuid(), characteristic.getProperties());
                if (client.containsNotifyCharacteristic(characteristic.getUuid())
                        && ((characteristic.getProperties() & BleGattBase.PROPERTY_NOTIFY) != 0 || (characteristic.getProperties() & BleGattBase.PROPERTY_INDICATE) != 0)
                        && client.isAutomatic(characteristic.getUuid())) {
                    AttributeOperation operation = new AttributeOperation(AttributeOperation.AttributeOperationCommand.DESCRIPTOR_WRITE, characteristic, true);
                    operation.setIsPartOfPrimaryService(client.isPrimaryService());
                    operations.add(operation);
                }
                if (client.containsCharacteristicRead(characteristic.getUuid())
                        && (characteristic.getProperties() & BleGattBase.PROPERTY_READ) != 0
                        && client.isAutomaticRead(characteristic.getUuid())) {
                    AttributeOperation operation = new AttributeOperation(AttributeOperation.AttributeOperationCommand.CHARACTERISTIC_READ, characteristic);
                    operation.setIsPartOfPrimaryService(client.isPrimaryService());
                    operations.add(operation);
                }
            }
        } else {
            BleLogger.d(TAG, "No client found for SERVICE: " + service.getUuid().toString() + " chrs: " + service.getCharacteristics().size());
        }
    }

    void handleCharacteristicWrite(final BluetoothGattService service, final BluetoothGattCharacteristic characteristic, int status) {
        logIfError("handleCharacteristicWrite uuid: " + characteristic.getUuid().toString(), status);
        switch (status) {
            case BleGattBase.ATT_INSUFFICIENT_AUTHENTICATION:
            case BleGattBase.ATT_INSUFFICIENT_ENCRYPTION: {
                BleLogger.e(TAG, "Attribute operation write failed due the reason: " + status);
                startAuthentication(this::handleAuthenticationComplete);
                // fallthrough
            }
            default: {
                final BleGattBase client = fetchClient(service.getUuid());
                if (client != null && client.containsCharacteristic(characteristic.getUuid())) {
                    if (!attOperations.isEmpty() && attOperations.peek().isWithResponse()) {
                        client.processServiceDataWrittenWithResponse(characteristic.getUuid(), status);
                    } else {
                        client.processServiceDataWritten(characteristic.getUuid(), status);
                    }
                }
                if (status != BleGattBase.ATT_INSUFFICIENT_AUTHENTICATION &&
                        status != BleGattBase.ATT_INSUFFICIENT_ENCRYPTION) {
                    processNextAttributeOperation(true);
                }
                break;
            }
        }
    }

    void handleCharacteristicRead(final BluetoothGattService service, final BluetoothGattCharacteristic characteristic, byte[] value, int status) {
        logIfError("handleCharacteristicRead uuid: " + characteristic.getUuid().toString(), status);
        switch (status) {
            case BleGattBase.ATT_INSUFFICIENT_AUTHENTICATION:
            case BleGattBase.ATT_INSUFFICIENT_ENCRYPTION: {
                BleLogger.e(TAG, "Attribute operation read failed due the reason: " + status);
                startAuthentication(this::handleAuthenticationComplete);
                // fallthrough
            }
            default: {
                if (status != BleGattBase.ATT_INSUFFICIENT_AUTHENTICATION &&
                        status != BleGattBase.ATT_INSUFFICIENT_ENCRYPTION) {
                    processNextAttributeOperation(true);
                }
                final BleGattBase client = fetchClient(service.getUuid());
                if (client != null && client.containsCharacteristic(characteristic.getUuid())) {
                    client.processServiceData(characteristic.getUuid(), value, status, false);
                }
                break;
            }
        }
    }

    void handleCharacteristicValueUpdated(final BluetoothGattService service, final BluetoothGattCharacteristic characteristic, byte[] value) {
        final BleGattBase client = fetchClient(service.getUuid());
        if (client != null && client.containsCharacteristic(characteristic.getUuid())) {
            client.processServiceData(characteristic.getUuid(), value, BleGattBase.ATT_SUCCESS, true);
        } else {
            BleLogger.e(TAG, "Unhandled notification received");
        }
    }

    void handleDescriptorRead(BluetoothGattDescriptor descriptor, byte[] value, int status) {
        BleLogger.d(TAG, "onDescriptorRead status: " + status);
        processNextAttributeOperation(true);
    }

    void handleDescriptorWrite(final BluetoothGattService service,
                               final BluetoothGattCharacteristic characteristic,
                               byte[] value,
                               int status) {
        BleLogger.d(TAG, "onDescriptorWrite uuid: " + characteristic.getUuid().toString() + " status: " + status);
        switch (status) {
            case BleGattBase.ATT_INSUFFICIENT_AUTHENTICATION:
            case BleGattBase.ATT_INSUFFICIENT_ENCRYPTION: {
                BleLogger.e(TAG, "Attribute operation descriptor write failed due the reason: " + status);
                startAuthentication(this::handleAuthenticationComplete);
                // fallthrough
            }
            default: {
                if (status != BleGattBase.ATT_INSUFFICIENT_AUTHENTICATION &&
                        status != BleGattBase.ATT_INSUFFICIENT_ENCRYPTION) {
                    processNextAttributeOperation(true);
                }
                byte[] disable = new byte[]{0x00, 0x00};
                boolean activated = !Arrays.equals(disable, value);
                if (status != BleGattBase.ATT_SUCCESS) {
                    activated = false;
                }
                final BleGattBase client = fetchClient(service.getUuid());
                if (client != null && client.containsCharacteristic(characteristic.getUuid())) {
                    client.descriptorWritten(characteristic.getUuid(), activated, status);
                }
                break;
            }
        }
    }

    /**
     * @param mtu    att mtu size
     * @param status @see BleGattBase error codes
     */
    void handleMtuChanged(int mtu, int status) {
        BleLogger.d(TAG, "handleMtuChanged status: " + status + " mtu: " + mtu);
        if (status == BleGattBase.ATT_SUCCESS) {
            for (BleGattBase gattClient : clients) {
                gattClient.setMtuSize(mtu);
            }
        }
    }

    void handleAuthenticationComplete() {
        processNextAttributeOperation(false);
        for (BleGattBase gattClient : clients) {
            gattClient.authenticationCompleted();
        }
    }

    private void handleAuthenticationFailed(Throwable e) {
        processNextAttributeOperation(false);
        for (BleGattBase gattClient : clients) {
            gattClient.authenticationFailed(e);
        }
    }

    public void processNextAttributeOperation(boolean remove) {
        if (!attOperations.isEmpty()) {
            try {
                if (remove) {
                    attOperations.take();
                }
                if (!attOperations.isEmpty()) {
                    AttributeOperation operation = Objects.requireNonNull(attOperations.peek());
                    if (BuildConfig.DEBUG) {
                        BleLogger.d(TAG, "send next: " + operation.getCharacteristic().getUuid() + " op: " + operation.getAttributeOperation().toString());
                    }
                    try {
                        if (!sendNextAttributeOperation(operation)) {
                            BleLogger.w(TAG, "Attribute operation still pending");
                            // pending operation still in progress, ok case basically
                        }
                    } catch (BleNotSupported bleNotSupported) {
                        BleLogger.e(TAG, "attribute operation failed due to reason: " + bleNotSupported.getLocalizedMessage());
                        processNextAttributeOperation(true);
                    } catch (BleGattNotInitialized gattNotInitialized) {
                        BleLogger.e(TAG, "attribute operation failed due to reason gatt not initialized, ALL att operations will be removed");
                        attOperations.clear();
                    } catch (Throwable throwable) {
                        // fatal / unknown
                        attOperations.clear();
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void startAuthentication(Action complete) {
        // try next att operation anyway
        subscriptions.add(
                authenticate().toObservable()
                        .delaySubscription(500, TimeUnit.MILLISECONDS)
                        .ignoreElements()
                        .observeOn(AndroidSchedulers.from(context.getMainLooper()))
                        .subscribe(
                                complete,
                                this::handleAuthenticationFailed)
        );
    }
}