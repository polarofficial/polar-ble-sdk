package com.polar.androidcommunications.api.ble;

import android.bluetooth.le.ScanFilter;

import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import com.polar.androidcommunications.api.ble.exceptions.BleInvalidMtu;
import com.polar.androidcommunications.api.ble.model.BleDeviceSession;
import com.polar.androidcommunications.api.ble.model.advertisement.BleAdvertisementContent;
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase;
import com.polar.androidcommunications.api.ble.model.gatt.BleGattFactory;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Set;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;

public abstract class BleDeviceListener {

    /**
     * Pre filter interface for search, to improve memory usage
     */
    public interface BleSearchPreFilter {
        boolean process(@NonNull BleAdvertisementContent content);
    }

    @NonNull
    protected BleGattFactory factory;

    @Nullable
    protected BleSearchPreFilter preFilter;

    /**
     * @param clients e.g. how to set the clients
     *                <p>
     *                Set<Class<? extends BleGattBase> > clients = new HashSet<>(Arrays.asList(
     *                BleHrClient.class,
     *                BleBattClient.class,
     *                BleDisClient.class,
     *                BleGapClient.class,
     *                BlePfcClient.class,
     *                BlePsdClient.class,
     *                BlePsFtpClient.class,
     *                BleH7SettingsClient.class,
     *                BlePMDClient.class,
     *                BleRscClient.class));
     */
    protected BleDeviceListener(@NonNull Set<Class<? extends BleGattBase>> clients) {
        factory = new BleGattFactory(clients);
    }

    /**
     * @return true if bluetooth is active
     */
    public abstract boolean bleActive();

    /**
     * @param cb callback
     */
    public abstract void setBlePowerStateCallback(@NonNull BlePowerStateChangedCallback cb);

    public interface BlePowerStateChangedCallback {
        /**
         * @param power bt state
         */
        void stateChanged(boolean power);
    }

    /**
     * Restarts the scan
     */
    public abstract void scanRestart();

    /**
     * @param filters scan filter list, android specific
     */
    public abstract void setScanFilters(@Nullable List<ScanFilter> filters);

    /**
     * enable to optimize memory usage or disable scan pre filter
     *
     * @param filter policy
     */
    public abstract void setScanPreFilter(@Nullable BleSearchPreFilter filter);

    /**
     * @param enable true enables timer to avoid opportunistic scan, false disables. Default true.
     */
    public abstract void setOpportunisticScan(boolean enable);

    /**
     * Produces: onNext:      When a advertisement has been detected <BR>
     * onError:     if scan start fails propagates BleStartScanError with error code <BR>
     * onCompleted: Non produced <BR>
     *
     * @param fetchKnownDevices, fetch known devices means bonded, already connected and already found devices <BR>
     * @return Observable stream <BR>
     */
    @NonNull
    public abstract Flowable<BleDeviceSession> search(boolean fetchKnownDevices);

    /**
     * Set the preferred MTU. This value will be negotiated between the central and peripheral devices,
     * so it might not always take effect if peripheral is not capable
     * <p>
     * If set to 0 before the connection is created then MTU negotiation is skipped. Value 0 can be
     * used in cases we don't want MTU negotiation, this can be handy with phones we know the MTU
     * negotiation is not working.
     *
     * @param mtu preferred mtu
     */
    public abstract void setPreferredMtu(@IntRange(from = 0, to = 512) int mtu) throws BleInvalidMtu;

    /**
     * Read the preferred MTU. This is not the negotiated MTU, but the value suggested by host
     * during MTU negotiation.
     */
    public abstract int getPreferredMtu();

    /**
     * As java does not support destructor/RAII, Client/App should call this whenever the application is being destroyed
     */
    public abstract void shutDown();

    /**
     * Attempt connection establishment
     *
     * @param session device
     */
    public abstract void openSessionDirect(@NonNull BleDeviceSession session);

    /**
     * Acquire connection establishment, BleDeviceSessionStateChangedCallback callbacks are invoked
     *
     * @param session device
     * @param uuids   needed uuids to be found from advertisement data, when reconnecting
     */
    public abstract void openSessionDirect(@NonNull BleDeviceSession session, @NonNull List<String> uuids);

    /**
     * Produces: onNext: When a device session state has changed, Note use pair.second to check the state (see BleDeviceSession.DeviceSessionState)
     *
     * @return Observable stream
     */
    @NonNull
    public abstract Observable<Pair<BleDeviceSession, BleDeviceSession.DeviceSessionState>> monitorDeviceSessionState();


    public interface BleDeviceSessionStateChangedCallback {
        /**
         * Invoked for all sessions and all state changes
         *
         * @param session check sessionState or session.getPreviousState() for actions
         */
        void stateChanged(@NonNull BleDeviceSession session, @NonNull BleDeviceSession.DeviceSessionState sessionState);
    }

    /**
     * Acquires disconnection directly without Observable returned
     *
     * @param session device
     */
    public abstract void closeSessionDirect(@NonNull BleDeviceSession session);

    /**
     * @return List of current device sessions known
     */
    @Nullable
    public abstract Set<BleDeviceSession> deviceSessions();

    /**
     * @param address bt address in format 00:11:22:33:44:55
     * @return BleDeviceSession
     */
    public abstract BleDeviceSession sessionByAddress(final String address);

    /**
     * Client app/lib can request to remove device from the list,
     *
     * @param deviceSession @see BleDeviceSession
     * @return true device was removed, false no( means device is considered to be alive )
     */
    public abstract boolean removeSession(@NonNull BleDeviceSession deviceSession);

    /**
     * @return count of sessions removed
     */
    public abstract int removeAllSessions();

    public abstract int removeAllSessions(@NonNull Set<BleDeviceSession.DeviceSessionState> inStates);

    /**
     * enable or disable automatic reconnection, by default true.
     *
     * @param automaticReconnection
     */
    public abstract void setAutomaticReconnection(boolean automaticReconnection);

    public static final int POWER_MODE_NORMAL = 0;
    public static final int POWER_MODE_LOW = 1;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({POWER_MODE_NORMAL, POWER_MODE_LOW})
    public @interface PowerMode {
    }

    public abstract void setPowerMode(@PowerMode int mode);
}
