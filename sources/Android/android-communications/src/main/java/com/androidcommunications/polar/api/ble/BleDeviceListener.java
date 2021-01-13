package com.androidcommunications.polar.api.ble;

import android.bluetooth.le.ScanFilter;

import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;

import com.androidcommunications.polar.api.ble.model.BleDeviceSession;
import com.androidcommunications.polar.api.ble.model.advertisement.BleAdvertisementContent;
import com.androidcommunications.polar.api.ble.model.gatt.BleGattBase;
import com.androidcommunications.polar.api.ble.model.gatt.BleGattFactory;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Set;

import io.reactivex.rxjava3.core.Flowable;

public abstract class BleDeviceListener {

    /**
     * Pre filter interface for search, to improve memory usage
     */
    public interface BleSearchPreFilter {
        boolean process(BleAdvertisementContent content);
    }

    protected BleGattFactory factory;
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
    protected BleDeviceListener(Set<Class<? extends BleGattBase>> clients) {
        factory = new BleGattFactory(clients);
    }

    /**
     * @return true if bluetooth is active
     */
    abstract public boolean bleActive();

    /**
     * @param cb callback
     */
    abstract public void setBlePowerStateCallback(@Nullable BlePowerStateChangedCallback cb);

    public interface BlePowerStateChangedCallback {
        /**
         * @param power bt state
         */
        void stateChanged(Boolean power);
    }

    /**
     * @param filters scan filter list, android specific
     */
    abstract public void setScanFilters(@Nullable final List<ScanFilter> filters);

    /**
     * enable to optimize memory usage or disable scan pre filter
     *
     * @param filter policy
     */
    abstract public void setScanPreFilter(@Nullable final BleSearchPreFilter filter);

    /**
     * @param enable true enables timer to avoid opportunistic scan, false disables. Default true.
     */
    abstract public void setOpportunisticScan(boolean enable);

    /**
     * Produces: onNext:      When a advertisement has been detected <BR>
     * onError:     if scan start fails propagates BleStartScanError with error code <BR>
     * onCompleted: Non produced <BR>
     *
     * @param fetchKnownDevices, fetch known devices means bonded, already connected and already found devices <BR>
     * @return Observable stream <BR>
     */
    abstract public Flowable<BleDeviceSession> search(boolean fetchKnownDevices);

    abstract public void setMtu(@IntRange(from = 70, to = 512) int mtu);

    /**
     * As java does not support destructor/RAII, Client/App should call this whenever the application is being destroyed
     */
    abstract public void shutDown();

    /**
     * aquire connection establishment
     *
     * @param session device
     */
    abstract public void openSessionDirect(BleDeviceSession session);

    /**
     * aquire connection establishment, BleDeviceSessionStateChangedCallback callbacks are invoked
     *
     * @param session device
     * @param uuids   needed uuids to be found from advertisement data, when reconnecting
     */
    abstract public void openSessionDirect(BleDeviceSession session, List<String> uuids);

    public interface BleDeviceSessionStateChangedCallback {
        /**
         * Invoked for all sessions and all state changes
         *
         * @param session check sessionState or session.getPreviousState() for actions
         */
        void stateChanged(BleDeviceSession session, BleDeviceSession.DeviceSessionState sessionState);
    }

    /**
     * set or null state observer
     *
     * @param changedCallback @see BleDeviceSessionStateChangedCallback
     */
    abstract public void setDeviceSessionStateChangedCallback(@Nullable BleDeviceSessionStateChangedCallback changedCallback);

    /**
     * aquires disconnection directly without Observable returned
     *
     * @param session device
     */
    abstract public void closeSessionDirect(BleDeviceSession session);

    /**
     * @return List of current device sessions known
     */
    abstract public Set<BleDeviceSession> deviceSessions();

    /**
     * @param address bt address in format 00:11:22:33:44:55
     * @return BleDeviceSession
     */
    abstract public BleDeviceSession sessionByAddress(final String address);

    /**
     * Client app/lib can request to remove device from the list,
     *
     * @param deviceSession @see BleDeviceSession
     * @return true device was removed, false no( means device is considered to be alive )
     */
    abstract public boolean removeSession(BleDeviceSession deviceSession);

    /**
     * @return count of sessions removed
     */
    abstract public int removeAllSessions();

    abstract public int removeAllSessions(Set<BleDeviceSession.DeviceSessionState> inStates);

    /**
     * enable or disable automatic reconnection, by default true.
     *
     * @param automaticReconnection
     */
    abstract public void setAutomaticReconnection(boolean automaticReconnection);

    public static final int POWER_MODE_NORMAL = 0;
    public static final int POWER_MODE_LOW = 1;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({POWER_MODE_NORMAL, POWER_MODE_LOW})
    public @interface PowerMode {
    }

    abstract public void setPowerMode(@PowerMode int mode);
}
