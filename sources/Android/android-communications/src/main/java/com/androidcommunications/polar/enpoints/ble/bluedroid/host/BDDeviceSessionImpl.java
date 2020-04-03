package com.androidcommunications.polar.enpoints.ble.bluedroid.host;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.os.Handler;

import com.androidcommunications.polar.BuildConfig;
import com.androidcommunications.polar.api.ble.BleLogger;
import com.androidcommunications.polar.api.ble.exceptions.BleCharacteristicNotFound;
import com.androidcommunications.polar.api.ble.exceptions.BleDisconnected;
import com.androidcommunications.polar.api.ble.exceptions.BleGattNotInitialized;
import com.androidcommunications.polar.api.ble.exceptions.BleNotSupported;
import com.androidcommunications.polar.api.ble.exceptions.BleServiceNotFound;
import com.androidcommunications.polar.api.ble.model.BleDeviceSession;
import com.androidcommunications.polar.api.ble.model.gatt.BleGattBase;
import com.androidcommunications.polar.api.ble.model.gatt.BleGattFactory;
import com.androidcommunications.polar.api.ble.model.gatt.BleGattTxInterface;
import com.androidcommunications.polar.common.ble.AtomicSet;
import com.androidcommunications.polar.common.ble.RxUtils;
import com.androidcommunications.polar.enpoints.ble.common.BleDeviceSession2;
import com.androidcommunications.polar.enpoints.ble.common.attribute.AttributeOperation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import io.reactivex.Completable;
import io.reactivex.CompletableEmitter;
import io.reactivex.CompletableOnSubscribe;
import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;

class BDDeviceSessionImpl extends BleDeviceSession2 implements BleGattTxInterface {

    private static final UUID DESCRIPTOR_CCC = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private final static String TAG = BDDeviceSessionImpl.class.getSimpleName();
    // gatt is the only shared object between threads
    final private Object gattMutex = new Object();
    Disposable serviceDiscovery;

    private final LinkedBlockingDeque<AttributeOperation> attOperations = new LinkedBlockingDeque<>();

    private BluetoothDevice bluetoothDevice;
    private BluetoothGatt gatt;
    private BDScanCallback bleScanCallback;
    private BDBondingListener bondingManager;
    private AtomicSet<SingleEmitter<? super List<UUID>>> servicesSubscriberAtomicList = new AtomicSet<>();
    private AtomicSet<SingleEmitter<? super Integer>> rssiObservers = new AtomicSet<>();
    private List<Disposable> subscriptions = new ArrayList<>();
    private Context context;
    private Handler handler;

    BDDeviceSessionImpl(Context context,
                        BluetoothDevice bluetoothDevice,
                        BDScanCallback scanCallback,
                        BDBondingListener bondingManager,
                        BleGattFactory factory){
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
        this.bluetoothDevice = null;
        this.bluetoothDevice = bluetoothDevice;
        // clear all
        resetGatt();
    }

    BluetoothGatt getGatt() {
       return gatt;
    }

    void setGatt(BluetoothGatt gatt) {
        this.gatt = gatt;
    }

    Object getGattMutex() {
        return gattMutex;
    }

    AtomicSet<SingleEmitter<? super Integer>> getRssiObservers() {
        return rssiObservers;
    }

    List<Disposable> getSubscriptions() {
        return subscriptions;
    }

    void resetGatt() {
        synchronized (gattMutex) {
            if (gatt != null) {
                try {
                    //gatt.disconnect();
                    gatt.close();
                } catch(Exception e) {
                    BleLogger.e(TAG,"gatt error: " + e.toString());
                }
            }
            gatt = null;
        }
    }

    @Override
    public void reset(){
        BleLogger.d(TAG,"reset");
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
    public Completable authenticate() {
        final BDBondingListener.BondingObserver[] observer = {null};
        return Completable.create(new CompletableOnSubscribe() {
            @Override
            public void subscribe(final CompletableEmitter subscriber) {
                if( getSessionState() == DeviceSessionState.SESSION_OPEN ) {
                    switch (bluetoothDevice.getBondState()) {
                        case BluetoothDevice.BOND_NONE: {
                            if( !bluetoothDevice.createBond() ){
                                subscriber.tryOnError(new Throwable("BD bonding start failed"));
                                return;
                            }
                        }
                        case BluetoothDevice.BOND_BONDING: {
                            observer[0] = new BDBondingListener.BondingObserver(bluetoothDevice) {
                                @Override
                                public void bonding() {
                                    //subscriber.onNext(new HashMap<BleUtils.PAIRING_CAPABILITY, Byte>());
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
                }else{
                    subscriber.onError(new BleDisconnected());
                }
            }
        }).doFinally(new Action() {
            @Override
            public void run() {
                bondingManager.removeObserver(observer[0]);
            }
        });
    }

    @Override
    public boolean isAuthenticated(){
        return bluetoothDevice.getBondState() == BluetoothDevice.BOND_BONDED;
    }

    @Override
    public boolean clearGattCache() {
        boolean result = false;
        synchronized (gattMutex) {
            if (gatt != null) {
                try {
                    Method localMethod = gatt.getClass().getMethod("refresh", new Class[0]);
                    if (localMethod != null) {
                        result = ((Boolean) localMethod.invoke(gatt, new Object[0])).booleanValue();
                    }
                } catch (Exception localException) {
                    BleLogger.e(TAG, "An exception occured while refreshing device");
                }
            }
        }
        return result;
    }

    @Override
    public Single<Integer> readRssiValue() {
        final SingleEmitter<? super Integer>[] observer = new SingleEmitter[1];
        return Single.create(
            new SingleOnSubscribe<Integer>() {
                @Override
                public void subscribe(SingleEmitter<Integer> subscriber) throws Exception {
                    if( getSessionState() == DeviceSessionState.SESSION_OPEN ){
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
            }
        }).doFinally(new Action() {
            @Override
            public void run() {
                rssiObservers.remove(observer[0]);
            }
        });
    }

    public boolean sendNextAttributeOperation(AttributeOperation operation) throws Throwable {
        BluetoothGattCharacteristic characteristic = operation.getCharacteristic();
        synchronized (getGattMutex()) {
            if(gatt != null) {
                switch (operation.getAttributeOperation()) {
                    case CHARACTERISTIC_READ: {
                        return getGatt().readCharacteristic(characteristic);
                    }
                    case CHARACTERISTIC_WRITE: {
                        if( operation.isWithResponse() && (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ){
                            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                        }else if((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0 ){
                            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                        }
                        characteristic.setValue(operation.getData());
                        return getGatt().writeCharacteristic(characteristic);
                    }
                    case DESCRIPTOR_WRITE: {
                        BluetoothGattDescriptor descriptor = operation.getCharacteristic().getDescriptor(DESCRIPTOR_CCC);
                        if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            byte[] value = operation.isEnable() ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
                            getGatt().setCharacteristicNotification(characteristic, operation.isEnable());
                            descriptor.setValue(value);
                            return getGatt().writeDescriptor(descriptor);
                        } else if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0) {
                            byte[] value = operation.isEnable() ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
                            getGatt().setCharacteristicNotification(characteristic, operation.isEnable());
                            descriptor.setValue(value);
                            return getGatt().writeDescriptor(descriptor);
                        }
                        break;
                    }
                    default:{
                        throw new BleNotSupported("not supported");
                    }
                }
            }else{
                throw new BleGattNotInitialized("Attribute operation tryed while gatt is uninitialized");
            }
        }
        return false;
    }

    @Override
    public void gattClientRequestStopScanning() {
        BleLogger.d(TAG,"GATT client request stop scanning");
        handler.post(new Runnable() {
            @Override
            public void run() {
                bleScanCallback.stopScan();
            }
        });
    }

    @Override
    public void gattClientResumeScanning() {
        BleLogger.d(TAG,"GATT client request continue scanning");
        handler.post(new Runnable() {
            @Override
            public void run() {
                bleScanCallback.startScan();
            }
        });
    }

    @Override
    public int transportQueueSize() {
        return attOperations.size();
    }

    @Override
    public void discoverServices() {
        // do nothing
    }

    @Override
    public void handleDisconnection() {
        // do nothing
        BleLogger.d(TAG,"disconnected");
        advertisementContent.resetAdvertisementData();
        attOperations.clear();
        for(BleGattBase gattclient : clients){
            gattclient.reset();
        }
        RxUtils.postDisconnectedAndClearList(servicesSubscriberAtomicList);
        RxUtils.postDisconnectedAndClearList(rssiObservers);
        for( Disposable subscription : subscriptions ){
            subscription.dispose();
        }
        subscriptions.clear();
        if( serviceDiscovery != null ) {
            serviceDiscovery.dispose();
            serviceDiscovery = null;
        }
    }

    // GATT
    @Override
    public void transmitMessages(BleGattBase gattclient, UUID serviceUuid, UUID characteristicUuid, List<byte[]> packets, boolean withResponse) throws Exception{
        // note most likely this comes from a different thread
        for(byte[] packet : packets){
            transmitMessage(gattclient,serviceUuid,characteristicUuid,packet,withResponse);
        }
    }

    @Override
    public void transmitMessage(BleGattBase gattclient, UUID serviceUuid, UUID characteristicUuid, byte[] packet, boolean withResponse) throws Exception {
        // note most likely this comes from a different thread
        synchronized (gattMutex){
            if(gatt != null) {
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
    public void readValue(BleGattBase gattclient, UUID serviceUuid, UUID characteristicUuid) throws Exception {
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
    public void setCharacteristicNotify(BleGattBase gattclient, UUID serviceUuid, UUID characteristicUuid, boolean enable) throws Exception {
        synchronized (gattMutex) {
            if (gatt != null) {
                for (BluetoothGattService service : gatt.getServices()) {
                    if (service.getUuid().equals(serviceUuid)) {
                        for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                            if (characteristic.getUuid().equals(characteristicUuid)) {
                                if ((characteristic.getProperties() & BleGattBase.PROPERTY_NOTIFY) > 0 ||
                                        (characteristic.getProperties() & BleGattBase.PROPERTY_INDICATE) > 0) {
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
    public Single<List<UUID>> monitorServicesDiscovered(final boolean checkConnection){
        final SingleEmitter<? super List<UUID>>[] observer = new SingleEmitter[1];
        return Single.create(
            new SingleOnSubscribe<List<UUID>>() {
                @Override
                public void subscribe(SingleEmitter<List<UUID>> subscriber) {
                    if(checkConnection && getSessionState() != BleDeviceSession.DeviceSessionState.SESSION_OPEN){
                        subscriber.tryOnError(new BleDisconnected());
                    } else {
                        observer[0] = subscriber;
                        servicesSubscriberAtomicList.add(subscriber);
                        synchronized (gattMutex) {
                            if (gatt != null && gatt.getServices().size() != 0) {
                                List<BluetoothGattService> s = gatt.getServices();
                                List<UUID> uuids = new ArrayList<>();
                                for (BluetoothGattService service : s) {
                                    uuids.add(service.getUuid());
                                }
                                subscriber.onSuccess(uuids);
                            }
                        }
                    }
            }
        }).doFinally(new Action() {
            @Override
            public void run() {
                servicesSubscriberAtomicList.remove(observer[0]);
            }
        });
    }

    @Override
    public boolean isConnected(){
        return getSessionState() == DeviceSessionState.SESSION_OPEN;
    }

    boolean isAuthenticationNeeded(){
        synchronized (gattMutex) {
            if(gatt != null) {
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

    void handleServicesDiscovered(){
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
            new RxUtils.Emitter<SingleEmitter<? super List<UUID>>>() {
                @Override
                public void item(SingleEmitter<? super List<UUID>> object) {
                    List<UUID> services = new ArrayList<>(serviceUuids);
                    object.onSuccess(services);
                }
            });
        Collections.sort(operations);
        this.attOperations.clear();
        this.attOperations.addAll(operations);
    }

    private void handleServiceDiscovered(List<AttributeOperation> operations, BluetoothGattService service){
        final BleGattBase client = fetchClient(service.getUuid());
        BleLogger.d(TAG, " SERVICE: " + service.getUuid().toString());
        if (client != null) {
            client.setServiceDiscovered(true,service.getUuid());
            for( BluetoothGattCharacteristic characteristic : service.getCharacteristics() ) {
                BleLogger.d(TAG,"     CHARACTERISTIC: "+ characteristic.getUuid().toString() + " PROPERTIES: " + characteristic.getProperties());
                client.processCharacteristicDiscovered(characteristic.getUuid(), characteristic.getProperties());
                if (client.containsNotifyCharacteristic(characteristic.getUuid()) &&
                    ((characteristic.getProperties() & BleGattBase.PROPERTY_NOTIFY) != 0 ||
                     (characteristic.getProperties() & BleGattBase.PROPERTY_INDICATE) != 0 ) &&
                     client.isAutomatic(characteristic.getUuid())) {
                    AttributeOperation operation = new AttributeOperation(AttributeOperation.AttributeOperationCommand.DESCRIPTOR_WRITE, characteristic,true);
                    operation.setIsPartOfPrimaryService(client.isPrimaryService());
                    operations.add(operation);
                }
                if (client.containsCharacteristicRead(characteristic.getUuid()) &&
                    (characteristic.getProperties() & BleGattBase.PROPERTY_READ) != 0 &&
                    client.isAutomaticRead(characteristic.getUuid())) {
                    AttributeOperation operation = new AttributeOperation(AttributeOperation.AttributeOperationCommand.CHARACTERISTIC_READ, characteristic);
                    operation.setIsPartOfPrimaryService(client.isPrimaryService());
                    operations.add(operation);
                }
            }
        } else {
            BleLogger.d(TAG,"No client found for SERVICE: " + service.getUuid().toString() + " chrs: " + service.getCharacteristics().size());
        }
    }

    void handleCharacteristicWrite(final BluetoothGattService service, final BluetoothGattCharacteristic characteristic, int status) {
        logIfError("handleCharacteristicWrite uuid: " + characteristic.getUuid().toString(),status);
        switch (status) {
            case BleGattBase.ATT_INSUFFICIENT_AUTHENTICATION:
            case BleGattBase.ATT_INSUFFICIENT_ENCRYPTION: {
                BleLogger.e(TAG, "Attribute operation write failed due the reason: " + status);
                startAuthentication(new Action() {
                    @Override
                    public void run() {
                        handleAuthenticationComplete();
                    }
                });
            }
            default: {
                final BleGattBase client = fetchClient(service.getUuid());
                if (client != null && client.containsCharacteristic(characteristic.getUuid())) {
                    if (attOperations.size() != 0 && attOperations.peek().isWithResponse()) {
                        client.processServiceDataWrittenWithResponse(characteristic.getUuid(), status);
                    } else {
                        client.processServiceDataWritten(characteristic.getUuid(), status);
                    }
                }
                if( status != BleGattBase.ATT_INSUFFICIENT_AUTHENTICATION &&
                    status != BleGattBase.ATT_INSUFFICIENT_ENCRYPTION ) {
                    processNextAttributeOperation(true);
                }
                break;
            }
        }
    }

    void handleCharacteristicRead(final BluetoothGattService service, final BluetoothGattCharacteristic characteristic, byte[] value, int status) {
        logIfError("handleCharacteristicRead uuid: " + characteristic.getUuid().toString(),status);
        switch (status){
            case BleGattBase.ATT_INSUFFICIENT_AUTHENTICATION:
            case BleGattBase.ATT_INSUFFICIENT_ENCRYPTION:{
                BleLogger.e(TAG, "Attribute operation read failed due the reason: " + status);
                startAuthentication(new Action() {
                    @Override
                    public void run() {
                        handleAuthenticationComplete();
                    }
                });
            }
            default: {
                if( status != BleGattBase.ATT_INSUFFICIENT_AUTHENTICATION &&
                    status != BleGattBase.ATT_INSUFFICIENT_ENCRYPTION ) {
                    processNextAttributeOperation(true);
                }
                final BleGattBase client = fetchClient(service.getUuid());
                if (client != null) {
                    if (client.containsCharacteristic(characteristic.getUuid())) {
                        client.processServiceData(characteristic.getUuid(), value, status, false);
                    }
                }
                break;
            }
        }
    }

    void handleCharacteristicValueUpdated(final BluetoothGattService service, final BluetoothGattCharacteristic characteristic, byte[] value) {
        final BleGattBase client = fetchClient(service.getUuid());
        if (client != null) {
            if (client.containsCharacteristic(characteristic.getUuid())) {
                client.processServiceData(characteristic.getUuid(), value, BleGattBase.ATT_SUCCESS, true);
            }
        } else {
            BleLogger.e(TAG,"Unhandled notification received");
        }
    }

    void handleDescriptorRead(BluetoothGattDescriptor descriptor, byte[] value, int status) {
        BleLogger.d(TAG, "onDescriptorRead status: " + status);
        processNextAttributeOperation(true);
    }

    void handleDescriptorWrite(final BluetoothGattService service, final BluetoothGattCharacteristic characteristic, final BluetoothGattDescriptor descriptor, byte[] value, int status) {
        BleLogger.d(TAG,"onDescriptorWrite uuid: " + characteristic.getUuid().toString() + " status: " + status);
        switch (status) {
            case BleGattBase.ATT_INSUFFICIENT_AUTHENTICATION:
            case BleGattBase.ATT_INSUFFICIENT_ENCRYPTION: {
                BleLogger.e(TAG, "Attribute operation descriptor write failed due the reason: " + status);
                startAuthentication(new Action() {
                    @Override
                    public void run() {
                        handleAuthenticationComplete();
                    }
                });
            }
            default: {
                if( status != BleGattBase.ATT_INSUFFICIENT_AUTHENTICATION &&
                    status != BleGattBase.ATT_INSUFFICIENT_ENCRYPTION ) {
                    processNextAttributeOperation(true);
                }
                byte[] disable = new byte[]{0x00, 0x00};
                boolean activated = !Arrays.equals(disable,value);
                if(status != BleGattBase.ATT_SUCCESS){
                    activated = false;
                }
                final BleGattBase client = fetchClient(service.getUuid());
                if (client != null) {
                    if (client.containsCharacteristic(characteristic.getUuid())) {
                        client.descriptorWritten(characteristic.getUuid(), activated, status);
                    }
                }
                break;
            }
        }
    }

    /**
     * @param mtu att mtu size
     * @param status @see BleGattBase error codes
     */
    void handleMtuChanged(int mtu, int status) {
        BleLogger.d(TAG, "handleMtuChanged status: " + status + " mtu: " + mtu);
        if(status == BleGattBase.ATT_SUCCESS){
            for( BleGattBase gattclient : clients ){
                gattclient.setMtuSize(mtu);
            }
        }
    }

    void handleAuthenticationComplete(){
        processNextAttributeOperation(false);
        for(BleGattBase gattClient : clients){
            gattClient.authenticationCompleted();
        }
    }

    private void handleAuthenticationFailed(Throwable e){
        processNextAttributeOperation(false);
        for(BleGattBase gattClient : clients){
            gattClient.authenticationFailed(e);
        }
    }

    void processNextAttributeOperation(boolean remove) {
        if (attOperations.size() != 0) {
            try {
                if (remove) {
                    attOperations.take();
                }
                if (attOperations.size() != 0) {
                    AttributeOperation operation = attOperations.peek();
                    if(BuildConfig.DEBUG) {
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

    @Override
    public void startAuthentication(Action complete){
        subscriptions.add(authenticate().toObservable().delaySubscription(500, TimeUnit.MILLISECONDS).ignoreElements().
                observeOn(AndroidSchedulers.from(context.getMainLooper())).subscribe(
            complete,
            new Consumer<Throwable>() {
                @Override
                public void accept(Throwable throwable) {
                    // try next att operation anyway
                    handleAuthenticationFailed(throwable);

                }
            }));
    }
}
