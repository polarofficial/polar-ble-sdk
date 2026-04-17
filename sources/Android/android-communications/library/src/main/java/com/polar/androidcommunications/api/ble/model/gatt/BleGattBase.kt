package com.polar.androidcommunications.api.ble.model.gatt;

import com.polar.androidcommunications.api.ble.BleLogger;
import com.polar.androidcommunications.api.ble.exceptions.BleAttributeError;
import com.polar.androidcommunications.api.ble.exceptions.BleCharacteristicNotFound;
import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected;
import com.polar.androidcommunications.common.ble.AtomicSet;

import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Container class holding information from current client or service
 * For client encapsulates all characteristic uuid's , properties and service uuid
 * contains helpers functions for asynchronously monitoring characteristic or service events
 */
public abstract class BleGattBase {
    private static final String TAG = BleGattBase.class.getSimpleName();

    public static final int DEFAULT_ATT_MTU_SIZE = 23;
    private static final int DEFAULT_MTU_SIZE = DEFAULT_ATT_MTU_SIZE - 3;

    /**
     * Characteristic properties
     */
    public static final int PROPERTY_BROADCAST = 0x01;
    public static final int PROPERTY_READ = 0x02;
    public static final int PROPERTY_WRITE_NO_RESPONSE = 0x04;
    public static final int PROPERTY_WRITE = 0x08;
    public static final int PROPERTY_NOTIFY = 0x10;
    public static final int PROPERTY_INDICATE = 0x20;
    public static final int PROPERTY_SIGNED_WRITE = 0x40;
    public static final int PROPERTY_EXTENDED_PROPS = 0x80;

    /**
     * Permissions, note only in service role
     */
    public static final int PERMISSION_READ = 0x01;
    public static final int PERMISSION_READ_ENCRYPTED = 0x02;
    public static final int PERMISSION_READ_ENCRYPTED_MITM = 0x04;
    public static final int PERMISSION_WRITE = 0x10;
    public static final int PERMISSION_WRITE_ENCRYPTED = 0x20;
    public static final int PERMISSION_WRITE_ENCRYPTED_MITM = 0x40;
    public static final int PERMISSION_WRITE_SIGNED = 0x80;

    /**
     * ATT ERROR CODES, endpoint shall prefer these error codes when calling gatt client callbacks
     */
    public static final int ATT_SUCCESS = 0;
    public static final int ATT_INVALID_HANDLE = 0x1;
    public static final int ATT_READ_NOT_PERMITTED = 0x2;
    public static final int ATT_WRITE_NOT_PERMITTED = 0x3;
    public static final int ATT_INVALID_PDU = 0x4;
    public static final int ATT_INSUFFICIENT_AUTHENTICATION = 0x5;
    public static final int ATT_REQUEST_NOT_SUPPORTED = 0x6;
    public static final int ATT_INVALID_OFFSET = 0x7;
    public static final int ATT_INSUFFICIENT_AUTHOR = 0x8;
    public static final int ATT_PREPARE_QUEUE_FULL = 0x9;
    public static final int ATT_ATTR_NOT_FOUND = 0xa;
    public static final int ATT_ATTR_NOT_LONG = 0xb;
    public static final int ATT_INSUFFICIENT_KEY_SIZE = 0xc;
    public static final int ATT_INVALID_ATTRIBUTE_LENGTH = 0xd;
    public static final int ATT_UNLIKELY = 0xe;
    public static final int ATT_INSUFFICIENT_ENCRYPTION = 0xf;
    public static final int ATT_UNSUPPORTED_GRP_TYPE = 0x10;
    public static final int ATT_INSUFFICIENT_RESOURCES = 0x11;
    public static final int ATT_NOTIFY_OR_INDICATE_OFF = 0xff;
    //0x80-0x9F Application Errors
    //0xA0-0xDF Reserved for future use
    //0xE0-0xFF Common Profile and Service Error Codes
    //          Defined in Core Specification Supplement Part B.
    public static final int ATT_WRITE_REQUEST_REJECTED = 0xFC;
    public static final int ATT_CCCD_IMPROPERLY_CONFIGURED = 0xFD;
    public static final int ATT_PROCEDURE_ALREADY_IN_PROGRESS = 0xFE;
    public static final int ATT_OUT_OF_RANGE = 0xFF;

    public static final int ATT_UNKNOWN_ERROR = 0x100;

    //
    protected UUID serviceUuid;
    // List of all service characteristics, value is boolean if this is a automatic read/notification
    private final HashMap<UUID, Boolean> characteristics = new HashMap<>();
    // List of all service characteristics to read automatically
    private final HashMap<UUID, Boolean> characteristicsRead = new HashMap<>();
    // List of all service characteristics to be enabled automatically, pair contains property and atomic boolean
    private final HashMap<UUID, AtomicInteger> mandatoryNotificationCharacteristics = new HashMap<>();
    // List of all characteristics that are available
    private final AtomicSet<UUID> availableCharacteristics = new AtomicSet<>();
    // List of all readable characteristics that are available
    private final AtomicSet<UUID> availableReadableCharacteristics = new AtomicSet<>();
    // List of all writable characteristics that are available
    private final AtomicSet<UUID> availableWritableCharacteristics = new AtomicSet<>();
    // transport layer interface
    protected final BleGattTxInterface txInterface;
    // current usable mtu size
    protected final AtomicInteger mtuSize = new AtomicInteger(DEFAULT_MTU_SIZE);
    // mtu size with att layer
    private final AtomicInteger attMtuSize = new AtomicInteger(DEFAULT_ATT_MTU_SIZE);
    // flag to set client as primary
    protected boolean isPrimaryService = false;
    protected final AtomicBoolean serviceDiscovered = new AtomicBoolean(false);
    // sets flag that this client/service requires as a whole encryption
    private boolean encryptionRequired = false;

    protected BleGattBase(BleGattTxInterface txInterface, UUID serviceUuid) {
        this.txInterface = txInterface;
        this.serviceUuid = serviceUuid;
    }

    protected BleGattBase(BleGattTxInterface txInterface, UUID serviceUuid, boolean encryptionRequired) {
        this.txInterface = txInterface;
        this.serviceUuid = serviceUuid;
        this.encryptionRequired = encryptionRequired;
    }

    public boolean isEncryptionRequired() {
        return encryptionRequired;
    }

    public void reset() {
        availableCharacteristics.clear();
        availableReadableCharacteristics.clear();
        availableWritableCharacteristics.clear();
        for (AtomicInteger integer : mandatoryNotificationCharacteristics.values()) {
            synchronized (integer) {
                integer.set(-1);
                integer.notifyAll();
            }
        }
        synchronized (serviceDiscovered) {
            serviceDiscovered.set(false);
            serviceDiscovered.notifyAll();
        }
        mtuSize.set(DEFAULT_MTU_SIZE);
        attMtuSize.set(DEFAULT_ATT_MTU_SIZE);
    }

    /**
     * Callback for GATT service characteristic data processing
     *
     * @param characteristic characteristic UUID
     * @param data           data in byte array
     * @param status         status code of processed data
     * @param notifying      if true data is notification data from GATT service
     */
    public abstract void processServiceData(UUID characteristic, byte[] data, int status, boolean notifying);

    public abstract void processServiceDataWritten(UUID characteristic, int status);

    public Completable clientReady(boolean checkConnection) {
        // override in client if required
        return Completable.fromPublisher(Flowable.empty());
    }

    public void authenticationCompleted() {
        // NOTE this informal for client to know that authentication has been completed,
        // link might be encrypted, and device might be bonded
    }

    public void authenticationFailed(Throwable reason) {
        // NOTE this informal for client to know that authentication has failed
        BleLogger.e(TAG, "authentication failed: " + reason.toString());
    }

    public void processServiceDataWrittenWithResponse(UUID characteristic, int status) {
        // optional, default forward to processServiceDataWritten
        processServiceDataWritten(characteristic, status);
    }

    // only for CCC
    public void descriptorWritten(UUID characteristic, boolean active, int status) {
        final AtomicInteger integer = getNotificationAtomicInteger(characteristic);
        if (integer != null) {
            synchronized (integer) {
                if (status == ATT_SUCCESS) {
                    if (active) {
                        integer.set(status);
                    } else {
                        integer.set(ATT_NOTIFY_OR_INDICATE_OFF);
                    }
                } else {
                    integer.set(status);
                }
                integer.notifyAll();
            }
        }
    }

    public void processCharacteristicDiscovered(UUID characteristic, int property) {
        // implement if needed
        addAvailableCharacteristic(characteristic, property);
    }

    public boolean isServiceDiscovered() {
        return serviceDiscovered.get();
    }

    public void setServiceDiscovered(boolean discovered, UUID uuid) {
        synchronized (serviceDiscovered) {
            serviceDiscovered.set(discovered);
            serviceDiscovered.notifyAll();
        }
    }

    public boolean containsCharacteristicRead(UUID characteristic) {
        return characteristicsRead.containsKey(characteristic);
    }

    public boolean containsCharacteristic(UUID characteristic) {
        return characteristics.containsKey(characteristic);
    }

    public boolean isAutomaticRead(UUID characteristic) {
        return characteristicsRead.containsKey(characteristic) && characteristicsRead.get(characteristic);
    }

    public boolean isAutomatic(UUID characteristic) {
        return characteristics.containsKey(characteristic) && characteristics.get(characteristic);
    }

    public boolean serviceBelongsToClient(UUID service) {
        return serviceUuid.equals(service);
    }

    public boolean containsNotifyCharacteristic(UUID characteristic) {
        return mandatoryNotificationCharacteristics.containsKey(characteristic);
    }

    public AtomicInteger getNotificationAtomicInteger(UUID characteristic) {
        if (mandatoryNotificationCharacteristics.containsKey(characteristic)) {
            return mandatoryNotificationCharacteristics.get(characteristic);
        }
        return null;
    }

    private boolean contains(UUID characteristic, Set<UUID> uuids) {
        return uuids.contains(characteristic);
    }

    public Set<UUID> getAvailableCharacteristics() {
        return availableCharacteristics.objects();
    }

    public BleGattTxInterface getTxInterface() {
        return txInterface;
    }

    public void setMtuSize(int mtuSize) {
        this.attMtuSize.set(mtuSize);
        this.mtuSize.set(mtuSize - 3);
    }

    protected void addCharacteristic(UUID chr) {
        characteristics.put(chr, true);
    }

    /**
     * Adds characteristic uuid to be handled by this client, by calling this characteristic shall be auto read after connection establishment <BR>
     *
     * @param characteristicRead <BR>
     */
    protected void addCharacteristicRead(UUID characteristicRead) {
        addCharacteristic(characteristicRead, PROPERTY_READ);
    }

    /**
     * Adds notification characteristic uuid to be handled by this client. This will set
     * notification/indication automatic enabled after connection establishment
     *
     * @param characteristic characteristic which notification is set on
     */
    protected void addCharacteristicNotification(UUID characteristic) {
        // Note properties are just informal, as this is used by the client
        BleLogger.d(TAG, "Added notification characteristic for " + characteristic.toString());
        addCharacteristic(characteristic, PROPERTY_NOTIFY | PROPERTY_INDICATE);
    }

    /**
     * Remove notification characteristic from this client. This will remove the
     * notification/indication automatic enable after connection establishment
     *
     * @param characteristic characteristic which notification is set off
     */
    protected void removeCharacteristicNotification(UUID characteristic) {
        BleLogger.d(TAG, "Remove notification characteristic for " + characteristic.toString());
        if (containsNotifyCharacteristic(characteristic)) {
            mandatoryNotificationCharacteristics.remove(characteristic);
        }
        if (containsCharacteristic(characteristic)) {
            characteristics.remove(characteristic);
        }
    }

    protected void addCharacteristic(final UUID characteristic, int properties) {
        if (((properties & PROPERTY_NOTIFY) != 0 || (properties & PROPERTY_INDICATE) != 0) && !containsNotifyCharacteristic(characteristic)) {
            mandatoryNotificationCharacteristics.put(characteristic, new AtomicInteger(-1));
        }
        if ((properties & PROPERTY_READ) != 0 && !containsCharacteristicRead(characteristic)) {
            characteristicsRead.put(characteristic, true);
        }
        if (!characteristics.containsKey(characteristic)) {
            characteristics.put(characteristic, true);
        }
    }

    protected void addAvailableCharacteristic(UUID chr, int property) {
        if (containsCharacteristic(chr) && !contains(chr, availableCharacteristics.objects())) {
            availableCharacteristics.add(chr);
        }
        if ((property & PROPERTY_READ) != 0 && !contains(chr, availableReadableCharacteristics.objects())) {
            availableReadableCharacteristics.add(chr);
        }
        if (((property & PROPERTY_WRITE) != 0 || (property & PROPERTY_WRITE_NO_RESPONSE) != 0) &&
                !contains(chr, availableWritableCharacteristics.objects())) {
            availableWritableCharacteristics.add(chr);
        }
    }

    protected boolean hasAllAvailableReadableCharacteristics(Set<UUID> list) {
        return hasCharacteristics(list, availableReadableCharacteristics.objects());
    }

    protected boolean hasCharacteristics(Set<UUID> set, Set<UUID> list) {
        return !list.isEmpty() && set.containsAll(list);
    }

    /**
     * @return true if the current service is the most primary one
     */
    public boolean isPrimaryService() {
        return isPrimaryService;
    }

    public void setIsPrimaryService(boolean isPrimaryService) {
        this.isPrimaryService = isPrimaryService;
    }

    /**
     * monitor service discovered
     *
     * @param checkConnection optionally check is currently connected
     * @return Observable stream, only complete or error is produced
     */
    public Completable waitServiceDiscovered(final boolean checkConnection) {
        return Completable.create(emitter -> {
            try {
                if (!checkConnection || txInterface.isConnected()) {
                    synchronized (serviceDiscovered) {
                        if (serviceDiscovered.get()) {
                            emitter.onComplete();
                            return;
                        }
                        serviceDiscovered.wait();
                        if (txInterface.isConnected() && serviceDiscovered.get()) {
                            emitter.onComplete();
                            return;
                        }
                    }
                }
                throw new BleDisconnected();
            } catch (Exception ex) {
                if (!emitter.isDisposed()) {
                    emitter.tryOnError(ex);
                }
            }
        }).subscribeOn(Schedulers.io());
    }

    /**
     * monitor notification enabled observable.
     *
     * @param uuid            chr uuid to wait for
     * @param checkConnection optionally check is currently connected
     * @param scheduler       context where to start listening
     * @return Observable stream, only complete or error is produced
     */
    public Completable waitNotificationEnabled(final UUID uuid, final boolean checkConnection, final Scheduler scheduler) {
        final AtomicInteger integer = getNotificationAtomicInteger(uuid);
        return Completable.create(emitter -> {
            try {
                if (!checkConnection || txInterface.isConnected()) {
                    if (integer != null) {
                        if (integer.get() == ATT_SUCCESS) {
                            emitter.onComplete();
                            return;
                        } else if (integer.get() != -1) {
                            throw new BleAttributeError("Failed to set characteristic notification or indication ", integer.get());
                        } else {
                            synchronized (integer) {
                                integer.wait();
                            }
                            if (integer.get() != ATT_SUCCESS && !emitter.isDisposed()) {
                                if (integer.get() != -1) {
                                    throw new BleAttributeError("Failed to set characteristic notification or indication ", integer.get());
                                } else {
                                    throw new BleDisconnected();
                                }
                            }
                        }
                        emitter.onComplete();
                    } else {
                        throw new BleCharacteristicNotFound();
                    }
                } else {
                    throw new BleDisconnected();
                }
            } catch (Exception ex) {
                if (!emitter.isDisposed()) {
                    emitter.tryOnError(ex);
                }
            }
        }).subscribeOn(scheduler);
    }

    public Completable waitNotificationEnabled(final UUID uuid, final boolean checkConnection) {
        return waitNotificationEnabled(uuid, checkConnection, Schedulers.io());
    }
}
