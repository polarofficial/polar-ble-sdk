// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.impl;

import static com.polar.androidcommunications.api.ble.model.BleDeviceSession.DeviceSessionState.SESSION_CLOSED;
import static com.polar.androidcommunications.api.ble.model.BleDeviceSession.DeviceSessionState.SESSION_OPEN;
import static com.polar.androidcommunications.api.ble.model.BleDeviceSession.DeviceSessionState.SESSION_OPENING;
import static com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdControlPointResponse.PmdControlPointResponseCode.ERROR_ALREADY_IN_STATE;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import com.polar.androidcommunications.api.ble.BleDeviceListener;
import com.polar.androidcommunications.api.ble.BleLogger;
import com.polar.androidcommunications.api.ble.exceptions.BleControlPointCommandError;
import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected;
import com.polar.androidcommunications.api.ble.exceptions.BleNotAvailableInDevice;
import com.polar.androidcommunications.api.ble.model.BleDeviceSession;
import com.polar.androidcommunications.api.ble.model.advertisement.BlePolarHrAdvertisement;
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase;
import com.polar.androidcommunications.api.ble.model.gatt.client.BleBattClient;
import com.polar.androidcommunications.api.ble.model.gatt.client.BleDisClient;
import com.polar.androidcommunications.api.ble.model.gatt.client.BleHrClient;
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient;
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType;
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdSetting;
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.AccData;
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.GyrData;
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.MagData;
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.PpiData;
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient;
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils;
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility;
import com.polar.androidcommunications.common.ble.BleUtils;
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.BDDeviceListenerImpl;
import com.polar.sdk.api.PolarBleApi;
import com.polar.sdk.api.PolarBleApiCallbackProvider;
import com.polar.sdk.api.errors.PolarBleSdkInstanceException;
import com.polar.sdk.api.errors.PolarDeviceDisconnected;
import com.polar.sdk.api.errors.PolarDeviceNotFound;
import com.polar.sdk.api.errors.PolarInvalidArgument;
import com.polar.sdk.api.errors.PolarNotificationNotEnabled;
import com.polar.sdk.api.errors.PolarOperationNotSupported;
import com.polar.sdk.api.errors.PolarServiceNotAvailable;
import com.polar.sdk.api.model.PolarAccelerometerData;
import com.polar.sdk.api.model.PolarDataUtils;
import com.polar.sdk.api.model.PolarDeviceInfo;
import com.polar.sdk.api.model.PolarEcgData;
import com.polar.sdk.api.model.PolarExerciseData;
import com.polar.sdk.api.model.PolarExerciseEntry;
import com.polar.sdk.api.model.PolarGyroData;
import com.polar.sdk.api.model.PolarHrBroadcastData;
import com.polar.sdk.api.model.PolarHrData;
import com.polar.sdk.api.model.PolarMagnetometerData;
import com.polar.sdk.api.model.PolarOhrData;
import com.polar.sdk.api.model.PolarOhrPPIData;
import com.polar.sdk.api.model.PolarSensorSetting;

import org.reactivestreams.Publisher;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import fi.polar.remote.representation.protobuf.ExerciseSamples;
import fi.polar.remote.representation.protobuf.Types;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleSource;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.BiFunction;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.functions.Function;
import io.reactivex.rxjava3.schedulers.Timed;
import protocol.PftpError;
import protocol.PftpRequest;
import protocol.PftpResponse;

/**
 * The default implementation of the Polar API
 */
public class BDBleApiImpl extends PolarBleApi implements BleDeviceListener.BlePowerStateChangedCallback {
    private static final String TAG = "BDBleApiImpl";
    private static volatile BDBleApiImpl instance;
    private final Map<String, Disposable> connectSubscriptions = new HashMap<>();
    private final Map<String, Disposable> deviceDataMonitorDisposable = new HashMap<>();
    private final Map<String, Disposable> stopPmdStreamingDisposable = new HashMap<>();
    private final BleDeviceListener.BleSearchPreFilter filter = content -> !content.getPolarDeviceId().isEmpty() && !content.getPolarDeviceType().equals("mobile");
    private BleDeviceListener listener;
    private Scheduler scheduler;
    private Disposable devicesStateMonitorDisposable;
    private PolarBleApiCallbackProvider callback;
    private PolarBleApiLogger logger;

    private BDBleApiImpl(@NonNull Context context, int features) throws BleNotAvailableInDevice {
        super(features);
        Set<Class<? extends BleGattBase>> clients = new HashSet<>();
        if ((this.features & PolarBleApi.FEATURE_HR) != 0) {
            clients.add(BleHrClient.class);
        }
        if ((this.features & PolarBleApi.FEATURE_DEVICE_INFO) != 0) {
            clients.add(BleDisClient.class);
        }
        if ((this.features & PolarBleApi.FEATURE_BATTERY_INFO) != 0) {
            clients.add(BleBattClient.class);
        }
        if ((this.features & PolarBleApi.FEATURE_POLAR_SENSOR_STREAMING) != 0) {
            clients.add(BlePMDClient.class);
        }
        if ((this.features & PolarBleApi.FEATURE_POLAR_FILE_TRANSFER) != 0) {
            clients.add(BlePsFtpClient.class);
        }
        listener = new BDDeviceListenerImpl(context, clients);
        listener.setScanPreFilter(filter);
        listener.setBlePowerStateCallback(this);
        scheduler = AndroidSchedulers.from(context.getMainLooper());
        BleLogger.setLoggerInterface(new BleLogger.BleLoggerInterface() {
            @Override
            public void d(String tag, String msg) {
                log(tag + "/" + msg);
            }

            @Override
            public void e(String tag, String msg) {
                logError(tag + "/" + msg);
            }

            @Override
            public void w(String tag, String msg) {
            }

            @Override
            public void i(String tag, String msg) {
            }
        });
    }

    public static BDBleApiImpl getInstance(@NonNull Context context, int features) throws PolarBleSdkInstanceException, BleNotAvailableInDevice {
        BDBleApiImpl result = instance;
        if (result != null) {
            if (result.features == features) {
                return result;
            } else {
                throw new PolarBleSdkInstanceException("Attempt to create Polar BLE API with features " + features + ". Instance with features " + result.features + " already exists");
            }
        }
        synchronized (BDBleApiImpl.class) {
            if (instance == null) {
                instance = new BDBleApiImpl(context, features);
            }
            return instance;
        }
    }

    private static void clearInstance() {
        instance = null;
    }

    @Override
    public void setMtu(int mtu) {
        listener.setMtu(mtu);
    }

    @Override
    public void shutDown() {
        for (Map.Entry<String, Disposable> deviceDisposable : deviceDataMonitorDisposable.entrySet()) {
            if (deviceDisposable.getValue().isDisposed()) {
                deviceDisposable.getValue().dispose();
            }
        }
        if (devicesStateMonitorDisposable != null && !devicesStateMonitorDisposable.isDisposed()) {
            devicesStateMonitorDisposable.dispose();
        }
        devicesStateMonitorDisposable = null;

        for (Map.Entry<String, Disposable> connection : connectSubscriptions.entrySet()) {
            if (!connection.getValue().isDisposed()) {
                connection.getValue().dispose();
            }
        }

        for (Map.Entry<String, Disposable> pmdStream : stopPmdStreamingDisposable.entrySet()) {
            if (!pmdStream.getValue().isDisposed()) {
                pmdStream.getValue().dispose();
            }
        }

        listener.shutDown();
        logger = null;
        callback = null;
        listener = null;
        scheduler = null;

        clearInstance();
    }

    @Override
    public void cleanup() {
        listener.removeAllSessions();
    }

    @Override
    public void setPolarFilter(boolean enable) {
        if (!enable) {
            listener.setScanPreFilter(null);
        } else {
            listener.setScanPreFilter(filter);
        }
    }

    @Override
    public boolean isFeatureReady(@NonNull final String deviceId, int feature) {
        try {
            switch (feature) {
                case FEATURE_POLAR_FILE_TRANSFER:
                    return sessionPsFtpClientReady(deviceId) != null;
                case FEATURE_POLAR_SENSOR_STREAMING:
                    return sessionPmdClientReady(deviceId) != null;
                default:
                    return false;
            }
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void setApiCallback(@NonNull PolarBleApiCallbackProvider callback) {
        this.callback = callback;
        callback.blePowerStateChanged(listener.bleActive());
    }

    @Override
    public void setApiLogger(@NonNull PolarBleApiLogger logger) {
        this.logger = logger;
    }

    @Override
    public void setAutomaticReconnection(boolean disable) {
        listener.setAutomaticReconnection(disable);
    }

    @NonNull
    @Override
    public Completable setLocalTime(@NonNull String identifier, @NonNull Calendar cal) {
        try {
            final BleDeviceSession session = sessionPsFtpClientReady(identifier);
            final BlePsFtpClient client = (BlePsFtpClient) session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE);
            PftpRequest.PbPFtpSetLocalTimeParams.Builder builder = PftpRequest.PbPFtpSetLocalTimeParams.newBuilder();
            Types.PbDate date = Types.PbDate.newBuilder()
                    .setYear(cal.get(Calendar.YEAR))
                    .setMonth(cal.get(Calendar.MONTH) + 1)
                    .setDay(cal.get(Calendar.DAY_OF_MONTH)).build();
            Types.PbTime time = Types.PbTime.newBuilder()
                    .setHour(cal.get(Calendar.HOUR_OF_DAY))
                    .setMinute(cal.get(Calendar.MINUTE))
                    .setSeconds(cal.get(Calendar.SECOND))
                    .setMillis(cal.get(Calendar.MILLISECOND)).build();
            builder.setDate(date).setTime(time).setTzOffset((int) TimeUnit.MINUTES.convert(cal.get(Calendar.ZONE_OFFSET), TimeUnit.MILLISECONDS));
            return client.query(PftpRequest.PbPFtpQuery.SET_LOCAL_TIME_VALUE, builder.build().toByteArray())
                    .toObservable()
                    .ignoreElements();
        } catch (Throwable error) {
            return Completable.error(error);
        }
    }

    @NonNull
    @Override
    public Single<PolarSensorSetting> requestStreamSettings(@NonNull final String identifier,
                                                            @NonNull DeviceStreamingFeature feature) {
        switch (feature) {
            case ECG:
                return querySettings(identifier, PmdMeasurementType.ECG);
            case ACC:
                return querySettings(identifier, PmdMeasurementType.ACC);
            case PPG:
                return querySettings(identifier, PmdMeasurementType.PPG);
            case PPI:
                return Single.error(new PolarOperationNotSupported());
            case GYRO:
                return querySettings(identifier, PmdMeasurementType.GYRO);
            case MAGNETOMETER:
                return querySettings(identifier, PmdMeasurementType.MAGNETOMETER);
            default:
                return Single.error(new PolarInvalidArgument());
        }
    }

    @NonNull
    @Override
    public Single<PolarSensorSetting> requestFullStreamSettings(@NonNull final String identifier,
                                                                @NonNull DeviceStreamingFeature feature) {
        switch (feature) {
            case ECG:
                return queryFullSettings(identifier, PmdMeasurementType.ECG);
            case ACC:
                return queryFullSettings(identifier, PmdMeasurementType.ACC);
            case PPG:
                return queryFullSettings(identifier, PmdMeasurementType.PPG);
            case PPI:
                return Single.error(new PolarOperationNotSupported());
            case GYRO:
                return queryFullSettings(identifier, PmdMeasurementType.GYRO);
            case MAGNETOMETER:
                return queryFullSettings(identifier, PmdMeasurementType.MAGNETOMETER);
            default:
                return Single.error(new PolarInvalidArgument());
        }
    }

    private Single<PolarSensorSetting> querySettings(final String identifier, final PmdMeasurementType type) {
        try {
            final BleDeviceSession session = sessionPmdClientReady(identifier);
            final BlePMDClient client = (BlePMDClient) session.fetchClient(BlePMDClient.PMD_SERVICE);
            return client.querySettings(type)
                    .map(setting -> new PolarSensorSetting((Map<PmdSetting.PmdSettingType, Set<Integer>>) setting.settings, type));
        } catch (Throwable e) {
            return Single.error(e);
        }
    }

    private Single<PolarSensorSetting> queryFullSettings(final String identifier, final PmdMeasurementType type) {
        try {
            final BleDeviceSession session = sessionPmdClientReady(identifier);
            final BlePMDClient client = (BlePMDClient) session.fetchClient(BlePMDClient.PMD_SERVICE);
            return client.queryFullSettings(type)
                    .map(setting -> new PolarSensorSetting((Map<PmdSetting.PmdSettingType, Set<Integer>>) setting.settings, type));
        } catch (Throwable e) {
            return Single.error(e);
        }
    }

    @Override
    public void backgroundEntered() {
        BleLogger.w(TAG, "call of deprecated backgroundEntered() method");
    }

    @Override
    public void foregroundEntered() {
        listener.scanRestart();
    }

    @NonNull
    @Override
    public Completable autoConnectToDevice(final int rssiLimit, @Nullable final String service, final int timeout, @NonNull final TimeUnit unit, @Nullable final String polarDeviceType) {
        final long[] start = {0};
        return Completable.create(emitter -> {
            if (service == null || service.matches("([0-9a-fA-F]{4})")) {
                emitter.onComplete();
            } else {
                emitter.tryOnError(new PolarInvalidArgument("Invalid service string format"));
            }
        })
                .andThen(listener.search(false)
                        .filter(bleDeviceSession -> {
                            if (bleDeviceSession.getMedianRssi() >= rssiLimit
                                    && bleDeviceSession.isConnectableAdvertisement()
                                    && (polarDeviceType == null || polarDeviceType.equals(bleDeviceSession.getPolarDeviceType()))
                                    && (service == null || bleDeviceSession.getAdvertisementContent().containsService(service))) {
                                if (start[0] == 0) {
                                    start[0] = System.currentTimeMillis();
                                }
                                return true;
                            }
                            return false;
                        })
                        .timestamp()
                        .takeUntil(bleDeviceSessionTimed -> {
                            long diff = bleDeviceSessionTimed.time(TimeUnit.MILLISECONDS) - start[0];
                            return (diff >= unit.toMillis(timeout));
                        })
                        .reduce(new HashSet<>(), (BiFunction<Set<BleDeviceSession>, Timed<BleDeviceSession>, Set<BleDeviceSession>>) (objects, bleDeviceSessionTimed) -> {
                            objects.add(bleDeviceSessionTimed.value());
                            return objects;
                        })
                        .doOnSuccess(set -> {
                            List<BleDeviceSession> list = new ArrayList<>(set);
                            Collections.sort(list, (o1, o2) -> o1.getRssi() > o2.getRssi() ? -1 : 1);
                            openConnection(list.get(0));
                            log("auto connect search complete");
                        })
                        .toObservable()
                        .ignoreElements());
    }

    @NonNull
    @Override
    public Completable autoConnectToDevice(final int rssiLimit, @Nullable final String service, @Nullable final String polarDeviceType) {
        return autoConnectToDevice(rssiLimit, service, 2, TimeUnit.SECONDS, polarDeviceType);
    }

    @Override
    public void connectToDevice(@NonNull final String identifier) throws PolarInvalidArgument {
        BleDeviceSession session = fetchSession(identifier);
        if (session == null || session.getSessionState() == SESSION_CLOSED) {
            if (connectSubscriptions.containsKey(identifier)) {
                Objects.requireNonNull(connectSubscriptions.get(identifier)).dispose();
                connectSubscriptions.remove(identifier);
            }
            if (session != null) {
                openConnection(session);
            } else {
                connectSubscriptions.put(identifier,
                        listener.search(false)
                                .filter(bleDeviceSession -> identifier.contains(":") ? bleDeviceSession.getAddress().equals(identifier) : bleDeviceSession.getPolarDeviceId().equals(identifier))
                                .take(1)
                                .observeOn(scheduler)
                                .subscribe(
                                        this::openConnection,
                                        error -> logError("connect search error with device: " + identifier + " error: " + error.getMessage()),
                                        () -> log("connect search completed for " + identifier)
                                ));
            }
        }
    }

    @Override
    public void disconnectFromDevice(@NonNull String identifier) throws PolarInvalidArgument {
        BleDeviceSession session = fetchSession(identifier);
        if (session != null
                && (session.getSessionState() == SESSION_OPEN
                || session.getSessionState() == SESSION_OPENING
                || session.getSessionState() == BleDeviceSession.DeviceSessionState.SESSION_OPEN_PARK)) {
            listener.closeSessionDirect(session);
        }

        if (connectSubscriptions.containsKey(identifier)) {
            Objects.requireNonNull(connectSubscriptions.get(identifier)).dispose();
            connectSubscriptions.remove(identifier);
        }
    }

    @NonNull
    @Override
    public Completable startRecording(@NonNull String identifier, @NonNull String exerciseId, @Nullable RecordingInterval interval, @NonNull SampleType type) {
        try {
            final BleDeviceSession session = sessionPsFtpClientReady(identifier);
            final BlePsFtpClient client = (BlePsFtpClient) session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE);
            final boolean recordingSupported = BlePolarDeviceCapabilitiesUtility.isRecordingSupported(session.getPolarDeviceType());
            if (recordingSupported) {
                Types.PbSampleType pbSampleType = type == SampleType.HR ? Types.PbSampleType.SAMPLE_TYPE_HEART_RATE : Types.PbSampleType.SAMPLE_TYPE_RR_INTERVAL;
                int recordingInterval = interval == null ? RecordingInterval.INTERVAL_1S.getValue() : interval.getValue();

                Types.PbDuration duration = Types.PbDuration.newBuilder().setSeconds(recordingInterval).build();
                PftpRequest.PbPFtpRequestStartRecordingParams params = PftpRequest.PbPFtpRequestStartRecordingParams.newBuilder().
                        setSampleDataIdentifier(exerciseId)
                        .setSampleType(pbSampleType)
                        .setRecordingInterval(duration)
                        .build();
                return client.query(PftpRequest.PbPFtpQuery.REQUEST_START_RECORDING_VALUE, params.toByteArray())
                        .toObservable()
                        .ignoreElements()
                        .onErrorResumeNext(throwable -> Completable.error(handleError(throwable)));
            }
            return Completable.error(new PolarOperationNotSupported());
        } catch (Throwable error) {
            return Completable.error(error);
        }
    }

    @NonNull
    @Override
    public Completable stopRecording(@NonNull String identifier) {
        try {
            final BleDeviceSession session = sessionPsFtpClientReady(identifier);
            final BlePsFtpClient client = (BlePsFtpClient) session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE);
            final boolean recordingSupported = BlePolarDeviceCapabilitiesUtility.isRecordingSupported(session.getPolarDeviceType());
            if (recordingSupported) {
                return client.query(PftpRequest.PbPFtpQuery.REQUEST_STOP_RECORDING_VALUE, null)
                        .toObservable()
                        .ignoreElements()
                        .onErrorResumeNext(throwable -> Completable.error(handleError(throwable)));
            }
            return Completable.error(new PolarOperationNotSupported());
        } catch (Throwable error) {
            return Completable.error(error);
        }
    }

    @NonNull
    @Override
    public Single<Pair<Boolean, String>> requestRecordingStatus(@NonNull String identifier) {
        try {
            final BleDeviceSession session = sessionPsFtpClientReady(identifier);
            final BlePsFtpClient client = (BlePsFtpClient) session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE);
            final boolean recordingSupported = BlePolarDeviceCapabilitiesUtility.isRecordingSupported(session.getPolarDeviceType());
            if (recordingSupported) {
                return client.query(PftpRequest.PbPFtpQuery.REQUEST_RECORDING_STATUS_VALUE, null).map(byteArrayOutputStream -> {
                    PftpResponse.PbRequestRecordingStatusResult result = PftpResponse.PbRequestRecordingStatusResult.parseFrom(byteArrayOutputStream.toByteArray());
                    return new Pair<>(result.getRecordingOn(), result.hasSampleDataIdentifier() ? result.getSampleDataIdentifier() : "");
                }).onErrorResumeNext(throwable -> Single.error(handleError(throwable)));
            }
            return Single.error(new PolarOperationNotSupported());
        } catch (Throwable error) {
            return Single.error(error);
        }
    }

    @NonNull
    @Override
    public Flowable<PolarExerciseEntry> listExercises(@NonNull String identifier) {
        try {
            final BleDeviceSession session = sessionPsFtpClientReady(identifier);
            final BlePsFtpClient client = (BlePsFtpClient) session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE);
            switch (BlePolarDeviceCapabilitiesUtility.getFileSystemType(session.getPolarDeviceType())) {
                case SAGRFC2_FILE_SYSTEM:
                    return fetchRecursively(client, "/U/0/", entry -> entry.matches("^([0-9]{8})(\\/)") ||
                            entry.matches("^([0-9]{6})(\\/)") ||
                            entry.equals("E/") ||
                            entry.equals("SAMPLES.BPB") ||
                            entry.equals("00/"))
                            .map(p -> {
                                String[] components = p.split("/");
                                SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd HHmmss", Locale.getDefault());
                                Date date = format.parse(components[3] + " " + components[5]);
                                return new PolarExerciseEntry(p, date, components[3] + components[5]);
                            })
                            .onErrorResumeNext(throwable -> Flowable.error(handleError(throwable)));
                case H10_FILE_SYSTEM:
                    return fetchRecursively(client, "/", entry -> entry.endsWith("/") || entry.equals("SAMPLES.BPB"))
                            .map(p -> {
                                String[] components = p.split("/");
                                return new PolarExerciseEntry(p, new Date(), components[1]);
                            })
                            .onErrorResumeNext(throwable -> Flowable.error(handleError(throwable)));
                default:
                    return Flowable.error(new PolarOperationNotSupported());
            }
        } catch (Throwable error) {
            return Flowable.error(error);
        }
    }

    @NonNull
    @Override
    public Single<PolarExerciseData> fetchExercise(@NonNull String identifier, @NonNull PolarExerciseEntry entry) {
        try {
            final BleDeviceSession session = sessionPsFtpClientReady(identifier);
            final BlePsFtpClient client = (BlePsFtpClient) session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE);
            protocol.PftpRequest.PbPFtpOperation.Builder builder = protocol.PftpRequest.PbPFtpOperation.newBuilder();
            builder.setCommand(PftpRequest.PbPFtpOperation.Command.GET);
            builder.setPath(entry.path);
            final BlePolarDeviceCapabilitiesUtility.FileSystemType fsType = BlePolarDeviceCapabilitiesUtility.getFileSystemType(session.getPolarDeviceType());
            if (fsType == BlePolarDeviceCapabilitiesUtility.FileSystemType.SAGRFC2_FILE_SYSTEM
                    || fsType == BlePolarDeviceCapabilitiesUtility.FileSystemType.H10_FILE_SYSTEM) {
                /*
                TODO to improve throughput (device will request parameter update from mobile):

                Add these two notifications before reading ex
                client.sendNotification(PftpNotification.PbPFtpHostToDevNotification.START_SYNC_VALUE, null).andThen(
                        client.sendNotification(PftpNotification.PbPFtpHostToDevNotification.INITIALIZE_SESSION_VALUE, null)
                );

                Add these two notifications after reading ex
                client.sendNotification(PftpNotification.PbPFtpHostToDevNotification.STOP_SYNC_VALUE,
                        PftpNotification.PbPFtpStopSyncParams.newBuilder().setCompleted(true).build().toByteArray()).andThen(
                        client.sendNotification(PftpNotification.PbPFtpHostToDevNotification.TERMINATE_SESSION_VALUE, null)
                );
                 */

                return client.request(builder.build().toByteArray())
                        .map(byteArrayOutputStream -> {
                            ExerciseSamples.PbExerciseSamples samples = ExerciseSamples.PbExerciseSamples.parseFrom(byteArrayOutputStream.toByteArray());
                            if (samples.hasRrSamples()) {
                                return new PolarExerciseData(samples.getRecordingInterval().getSeconds(), samples.getRrSamples().getRrIntervalsList());
                            } else {
                                return new PolarExerciseData(samples.getRecordingInterval().getSeconds(), samples.getHeartRateSamplesList());
                            }
                        })
                        .onErrorResumeNext(throwable -> Single.error(handleError(throwable)));
            }
            return Single.error(new PolarOperationNotSupported());
        } catch (Throwable error) {
            return Single.error(error);
        }
    }

    @NonNull
    @Override
    public Completable removeExercise(@NonNull String identifier, @NonNull PolarExerciseEntry entry) {
        try {
            final BleDeviceSession session = sessionPsFtpClientReady(identifier);
            final BlePsFtpClient client = (BlePsFtpClient) session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE);
            final BlePolarDeviceCapabilitiesUtility.FileSystemType fsType = BlePolarDeviceCapabilitiesUtility.getFileSystemType(session.getPolarDeviceType());
            if (fsType == BlePolarDeviceCapabilitiesUtility.FileSystemType.SAGRFC2_FILE_SYSTEM) {
                protocol.PftpRequest.PbPFtpOperation.Builder builder = protocol.PftpRequest.PbPFtpOperation.newBuilder();
                builder.setCommand(PftpRequest.PbPFtpOperation.Command.GET);
                final String[] components = entry.path.split("/");
                final String exerciseParent = "/U/0/" + components[3] + "/E/";
                builder.setPath(exerciseParent);
                return client.request(builder.build().toByteArray())
                        .flatMap((Function<ByteArrayOutputStream, SingleSource<?>>) byteArrayOutputStream -> {
                            PftpResponse.PbPFtpDirectory directory = PftpResponse.PbPFtpDirectory.parseFrom(byteArrayOutputStream.toByteArray());
                            PftpRequest.PbPFtpOperation.Builder removeBuilder = PftpRequest.PbPFtpOperation.newBuilder();
                            removeBuilder.setCommand(PftpRequest.PbPFtpOperation.Command.REMOVE);
                            if (directory.getEntriesCount() <= 1) {
                                // remove entire directory
                                removeBuilder.setPath("/U/0/" + components[3] + "/");
                            } else {
                                // remove only exercise
                                removeBuilder.setPath("/U/0/" + components[3] + "/E/" + components[5] + "/");
                            }
                            return client.request(removeBuilder.build().toByteArray());
                        })
                        .toObservable()
                        .ignoreElements()
                        .onErrorResumeNext(throwable -> Completable.error(handleError(throwable)));
            } else if (fsType == BlePolarDeviceCapabilitiesUtility.FileSystemType.H10_FILE_SYSTEM) {
                protocol.PftpRequest.PbPFtpOperation.Builder builder = protocol.PftpRequest.PbPFtpOperation.newBuilder();
                builder.setCommand(PftpRequest.PbPFtpOperation.Command.REMOVE);
                builder.setPath(entry.path);
                return client.request(builder.build().toByteArray())
                        .toObservable()
                        .ignoreElements()
                        .onErrorResumeNext(throwable -> Completable.error(handleError(throwable)));
            }
            return Completable.error(new PolarOperationNotSupported());
        } catch (Throwable error) {
            return Completable.error(error);
        }
    }

    @NonNull
    @Override
    public Flowable<PolarDeviceInfo> searchForDevice() {
        return listener.search(false)
                .distinct()
                .map(bleDeviceSession -> new PolarDeviceInfo(bleDeviceSession.getPolarDeviceId(),
                        bleDeviceSession.getAddress(),
                        bleDeviceSession.getRssi(),
                        bleDeviceSession.getName(),
                        bleDeviceSession.isConnectableAdvertisement()));
    }

    @NonNull
    @Override
    public Flowable<PolarHrBroadcastData> startListenForPolarHrBroadcasts(@Nullable final Set<String> deviceIds) {
        return listener.search(false)
                .filter(bleDeviceSession ->
                        (deviceIds == null || deviceIds.contains(bleDeviceSession.getPolarDeviceId())) &&
                                bleDeviceSession.getAdvertisementContent().getPolarHrAdvertisement().isPresent() &&
                                bleDeviceSession.getAdvertisementContent().getPolarHrAdvertisement().isHrDataUpdated()
                )
                .map(bleDeviceSession -> {
                    BlePolarHrAdvertisement advertisement = bleDeviceSession.getBlePolarHrAdvertisement();
                    return new PolarHrBroadcastData(new PolarDeviceInfo(bleDeviceSession.getPolarDeviceId(),
                            bleDeviceSession.getAddress(),
                            bleDeviceSession.getRssi(),
                            bleDeviceSession.getName(),
                            bleDeviceSession.isConnectableAdvertisement()),
                            advertisement.getHrForDisplay(),
                            advertisement.getBatteryStatus() != 0);
                });
    }

    private <T> Flowable<T> startStreaming(String identifier,
                                           PmdMeasurementType type,
                                           PolarSensorSetting setting,
                                           Function<BlePMDClient, Flowable<T>> observer) {
        try {
            final BleDeviceSession session = sessionPmdClientReady(identifier);
            final BlePMDClient client = (BlePMDClient) session.fetchClient(BlePMDClient.PMD_SERVICE);
            return client.startMeasurement(type, setting.map2PmdSettings())
                    .andThen(observer.apply(client)
                            .onErrorResumeNext(throwable -> Flowable.error(handleError(throwable)))
                            .doFinally(
                                    () -> stopPmdStreaming(session, client, type)
                            ));
        } catch (Throwable t) {
            return Flowable.error(t);
        }
    }

    private void openConnection(@NonNull BleDeviceSession session) {
        if (devicesStateMonitorDisposable == null || devicesStateMonitorDisposable.isDisposed()) {
            devicesStateMonitorDisposable = listener.monitorDeviceSessionState().subscribe(deviceStateMonitorObserver);
        }
        listener.openSessionDirect(session);
    }

    @NonNull
    @Override
    public Flowable<PolarEcgData> startEcgStreaming(@NonNull String identifier,
                                                    @NonNull PolarSensorSetting setting) {
        return startStreaming(identifier, PmdMeasurementType.ECG, setting,
                client -> client.monitorEcgNotifications(true)
                        .map(PolarDataUtils::mapPmdClientEcgDataToPolarEcg));
    }

    @NonNull
    @Override
    public Flowable<PolarAccelerometerData> startAccStreaming(@NonNull String identifier,
                                                              @NonNull PolarSensorSetting setting) {
        return startStreaming(identifier, PmdMeasurementType.ACC, setting, client -> client.monitorAccNotifications(true)
                .map(accData -> {
                    List<PolarAccelerometerData.PolarAccelerometerDataSample> samples = new ArrayList<>();
                    for (AccData.AccSample sample : accData.accSamples) {
                        samples.add(new PolarAccelerometerData.PolarAccelerometerDataSample(sample.getX(), sample.getY(), sample.getZ()));
                    }
                    return new PolarAccelerometerData(samples, accData.timeStamp);
                }));
    }

    @NonNull
    @Override
    public Flowable<PolarOhrData> startOhrStreaming(@NonNull String identifier,
                                                    @NonNull PolarSensorSetting setting) {
        return startStreaming(identifier, PmdMeasurementType.PPG, setting, client -> client.monitorPpgNotifications(true)
                .map(PolarDataUtils::mapPMDClientOhrDataToPolarOhr));
    }

    @NonNull
    @Override
    public Flowable<PolarOhrPPIData> startOhrPPIStreaming(@NonNull String identifier) {
        return startStreaming(identifier, PmdMeasurementType.PPI, new PolarSensorSetting(new HashMap<>()), client -> client.monitorPpiNotifications(true).map(ppiData -> {
            List<PolarOhrPPIData.PolarOhrPPISample> samples = new ArrayList<>();
            for (PpiData.PPSample ppSample : ppiData.ppSamples) {
                samples.add(new PolarOhrPPIData.PolarOhrPPISample(ppSample.ppInMs,
                        ppSample.ppErrorEstimate,
                        ppSample.hr,
                        ppSample.blockerBit != 0,
                        ppSample.skinContactStatus != 0,
                        ppSample.skinContactSupported != 0));
            }
            return new PolarOhrPPIData(ppiData.timeStamp, samples);
        }));
    }

    @NonNull
    @Override
    public Flowable<PolarMagnetometerData> startMagnetometerStreaming(@NonNull final String identifier,
                                                                      @NonNull PolarSensorSetting setting) {
        return startStreaming(identifier, PmdMeasurementType.MAGNETOMETER, setting, client -> client.monitorMagnetometerNotifications(true)
                .map(mgn -> {
                    List<PolarMagnetometerData.PolarMagnetometerDataSample> samples = new ArrayList<>();
                    for (MagData.MagSample sample : mgn.magSamples) {
                        samples.add(new PolarMagnetometerData.PolarMagnetometerDataSample(sample.getX(), sample.getY(), sample.getZ()));
                    }
                    return new PolarMagnetometerData(samples, mgn.getTimeStamp());
                }));
    }

    @NonNull
    @Override
    public Flowable<PolarGyroData> startGyroStreaming(@NonNull final String identifier,
                                                      @NonNull PolarSensorSetting setting) {
        return startStreaming(identifier, PmdMeasurementType.GYRO, setting, client -> client.monitorGyroNotifications(true)
                .map(gyro -> {
                    List<PolarGyroData.PolarGyroDataSample> samples = new ArrayList<>();
                    for (GyrData.GyrSample sample : gyro.gyrSamples) {
                        samples.add(new PolarGyroData.PolarGyroDataSample(sample.getX(), sample.getY(), sample.getZ()));
                    }
                    return new PolarGyroData(samples, gyro.getTimeStamp());
                }));
    }

    @NonNull
    @Override
    public Completable enableSDKMode(@NonNull String identifier) {
        try {
            final BleDeviceSession session = sessionPmdClientReady(identifier);
            final BlePMDClient client = (BlePMDClient) session.fetchClient(BlePMDClient.PMD_SERVICE);
            if (client != null && client.isServiceDiscovered()) {
                return client.startSDKMode()
                        .onErrorResumeNext(error -> {
                            if (error instanceof BleControlPointCommandError
                                    && ERROR_ALREADY_IN_STATE.equals(((BleControlPointCommandError) error).getError())) {
                                return Completable.complete();
                            }
                            return Completable.error(error);
                        });
            }
            return Completable.error(new PolarServiceNotAvailable());
        } catch (Throwable t) {
            return Completable.error(t);
        }
    }

    @NonNull
    @Override
    public Completable disableSDKMode(@NonNull String identifier) {
        try {
            final BleDeviceSession session = sessionPmdClientReady(identifier);
            final BlePMDClient client = (BlePMDClient) session.fetchClient(BlePMDClient.PMD_SERVICE);
            if (client != null && client.isServiceDiscovered()) {
                return client.stopSDKMode()
                        .onErrorResumeNext(error -> {
                            if (error instanceof BleControlPointCommandError
                                    && ERROR_ALREADY_IN_STATE.equals(((BleControlPointCommandError) error).getError())) {
                                return Completable.complete();
                            }
                            return Completable.error(error);
                        });
            }
            return Completable.error(new PolarServiceNotAvailable());
        } catch (Throwable t) {
            return Completable.error(t);
        }
    }

    @Nullable
    protected BleDeviceSession fetchSession(@NonNull final String identifier) throws PolarInvalidArgument {
        if (identifier.matches("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$")) {
            return sessionByAddress(identifier);
        } else if (identifier.matches("([0-9a-fA-F]){6,8}")) {
            return sessionByDeviceId(identifier);
        }
        throw new PolarInvalidArgument();
    }

    @Nullable
    protected BleDeviceSession sessionByAddress(@NonNull final String address) {
        for (BleDeviceSession session : listener.deviceSessions()) {
            if (session.getAddress().equals(address)) {
                return session;
            }
        }
        return null;
    }

    @Nullable
    protected BleDeviceSession sessionByDeviceId(@NonNull final String deviceId) {
        for (BleDeviceSession session : listener.deviceSessions()) {
            if (session.getAdvertisementContent().getPolarDeviceId().equals(deviceId)) {
                return session;
            }
        }
        return null;
    }

    @NonNull
    protected BleDeviceSession sessionServiceReady(final @NonNull String identifier, @NonNull UUID service) throws Throwable {
        BleDeviceSession session = fetchSession(identifier);
        if (session != null) {
            if (session.getSessionState() == SESSION_OPEN) {
                BleGattBase client = session.fetchClient(service);
                if (client != null && client.isServiceDiscovered()) {
                    return session;
                }
                throw new PolarServiceNotAvailable();
            }
            throw new PolarDeviceDisconnected();
        }
        throw new PolarDeviceNotFound();
    }

    @NonNull
    public BleDeviceSession sessionPmdClientReady(final @NonNull String identifier) throws Throwable {
        BleDeviceSession session = sessionServiceReady(identifier, BlePMDClient.PMD_SERVICE);
        BlePMDClient client = (BlePMDClient) session.fetchClient(BlePMDClient.PMD_SERVICE);
        if (client != null) {
            final AtomicInteger pair = client.getNotificationAtomicInteger(BlePMDClient.PMD_CP);
            final AtomicInteger pairData = client.getNotificationAtomicInteger(BlePMDClient.PMD_DATA);
            if (pair != null
                    && pairData != null
                    && pair.get() == BleGattBase.ATT_SUCCESS
                    && pairData.get() == BleGattBase.ATT_SUCCESS) {
                return session;
            }
            throw new PolarNotificationNotEnabled();
        }
        throw new PolarServiceNotAvailable();
    }

    @NonNull
    protected BleDeviceSession sessionPsFtpClientReady(final @NonNull String identifier) throws Throwable {
        BleDeviceSession session = sessionServiceReady(identifier, BlePsFtpUtils.RFC77_PFTP_SERVICE);
        BlePsFtpClient client = (BlePsFtpClient) session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE);
        if (client != null) {
            final AtomicInteger pair = client.getNotificationAtomicInteger(BlePsFtpUtils.RFC77_PFTP_MTU_CHARACTERISTIC);
            if (pair != null && pair.get() == BleGattBase.ATT_SUCCESS) {
                return session;
            }
            throw new PolarNotificationNotEnabled();
        }
        throw new PolarServiceNotAvailable();
    }

    @SuppressLint("CheckResult")
    protected void stopPmdStreaming(@NonNull BleDeviceSession session, @NonNull BlePMDClient client, @NonNull PmdMeasurementType type) {
        if (session.getSessionState() == SESSION_OPEN) {
            // stop streaming
            Disposable disposable = client.stopMeasurement(type).subscribe(
                    () -> {
                    },
                    throwable -> logError("failed to stop pmd stream: " + throwable.getLocalizedMessage())
            );
            stopPmdStreamingDisposable.put(session.getAddress(), disposable);
        }
    }

    private final Consumer<Pair<BleDeviceSession, BleDeviceSession.DeviceSessionState>> deviceStateMonitorObserver = deviceSessionStatePair ->
    {
        BleDeviceSession session = deviceSessionStatePair.first;
        BleDeviceSession.DeviceSessionState sessionState = deviceSessionStatePair.second;

        //Guard
        if (session == null || sessionState == null) {
            return;
        }

        PolarDeviceInfo info = new PolarDeviceInfo(
                session.getPolarDeviceId().isEmpty() ? session.getAddress() : session.getPolarDeviceId(),
                session.getAddress(),
                session.getRssi(),
                session.getName(),
                true);
        switch (Objects.requireNonNull(sessionState)) {
            case SESSION_OPEN:
                if (callback != null) {
                    callback.deviceConnected(info);
                }
                setupDevice(session);
                break;
            case SESSION_CLOSED:
                if (callback != null && (session.getPreviousState() == SESSION_OPEN || session.getPreviousState() == BleDeviceSession.DeviceSessionState.SESSION_CLOSING)) {
                    callback.deviceDisconnected(info);
                }
                tearDownDevice(session);
                break;
            case SESSION_OPENING:
                if (callback != null) {
                    callback.deviceConnecting(info);
                }
                break;
            default:
                //Do nothing
                break;
        }
    };

    private void setupDevice(final @NonNull BleDeviceSession session) {
        final String deviceId = session.getPolarDeviceId().length() != 0 ? session.getPolarDeviceId() : session.getAddress();

        Disposable disposable = session.monitorServicesDiscovered(true)
                .observeOn(scheduler)
                .toFlowable()
                .flatMapIterable((Function<List<UUID>, Iterable<UUID>>) uuids -> uuids)
                .flatMap(uuid -> {
                    if (session.fetchClient(uuid) != null) {
                        if (uuid.equals(BleHrClient.HR_SERVICE)) {
                            if (callback != null) {
                                callback.hrFeatureReady(deviceId);
                            }
                            final BleHrClient client = (BleHrClient) session.fetchClient(BleHrClient.HR_SERVICE);
                            client.observeHrNotifications(true)
                                    .observeOn(scheduler)
                                    .subscribe(
                                            hrNotificationData -> {
                                                if (callback != null) {
                                                    callback.hrNotificationReceived(deviceId,
                                                            new PolarHrData(hrNotificationData.hrValue,
                                                                    hrNotificationData.rrs,
                                                                    hrNotificationData.sensorContact,
                                                                    hrNotificationData.sensorContactSupported,
                                                                    hrNotificationData.rrPresent));
                                                }
                                            },
                                            error -> logError(error.getMessage()),
                                            () -> {
                                            }
                                    );
                        } else if (uuid.equals(BleBattClient.BATTERY_SERVICE)) {
                            BleBattClient client = (BleBattClient) session.fetchClient(BleBattClient.BATTERY_SERVICE);
                            client.monitorBatteryStatus(true)
                                    .observeOn(scheduler)
                                    .subscribe(
                                            batteryLevel -> {
                                                if (callback != null) {
                                                    callback.batteryLevelReceived(deviceId, batteryLevel);
                                                }
                                            },
                                            error -> logError(error.getMessage()),
                                            () -> {
                                            }
                                    );
                        } else if (uuid.equals(BlePMDClient.PMD_SERVICE)) {
                            final BlePMDClient client = (BlePMDClient) session.fetchClient(BlePMDClient.PMD_SERVICE);
                            return client.waitNotificationEnabled(BlePMDClient.PMD_CP, true)
                                    .concatWith(client.waitNotificationEnabled(BlePMDClient.PMD_DATA, true))
                                    .andThen(client.readFeature(true)
                                            .doOnSuccess(pmdFeature -> {
                                                if (callback != null) {
                                                    Set<DeviceStreamingFeature> deviceStreamingFeatures = new HashSet<>();
                                                    if (pmdFeature.ecgSupported) {
                                                        deviceStreamingFeatures.add(DeviceStreamingFeature.ECG);
                                                    }
                                                    if (pmdFeature.accSupported) {
                                                        deviceStreamingFeatures.add(DeviceStreamingFeature.ACC);
                                                    }
                                                    if (pmdFeature.ppgSupported) {
                                                        deviceStreamingFeatures.add(DeviceStreamingFeature.PPG);
                                                    }
                                                    if (pmdFeature.ppiSupported) {
                                                        deviceStreamingFeatures.add(DeviceStreamingFeature.PPI);
                                                    }
                                                    if (pmdFeature.gyroSupported) {
                                                        deviceStreamingFeatures.add(DeviceStreamingFeature.GYRO);
                                                    }
                                                    if (pmdFeature.magnetometerSupported) {
                                                        deviceStreamingFeatures.add(DeviceStreamingFeature.MAGNETOMETER);
                                                    }
                                                    callback.streamingFeaturesReady(deviceId, deviceStreamingFeatures);

                                                    if (pmdFeature.sdkModeSupported) {
                                                        callback.sdkModeFeatureAvailable(deviceId);
                                                    }
                                                }
                                            }))
                                    .toFlowable();
                        } else if (uuid.equals(BleDisClient.DIS_SERVICE)) {
                            BleDisClient client = (BleDisClient) session.fetchClient(BleDisClient.DIS_SERVICE);
                            return client.observeDisInfo(true)
                                    .observeOn(scheduler)
                                    .doOnNext(pair -> {
                                        if (callback != null) {
                                            callback.disInformationReceived(deviceId, pair.first, pair.second);
                                        }
                                    });
                        } else if (uuid.equals(BlePsFtpUtils.RFC77_PFTP_SERVICE)) {
                            BlePsFtpClient client = (BlePsFtpClient) session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE);
                            return client.waitPsFtpClientReady(true)
                                    .observeOn(scheduler)
                                    .doOnComplete(() -> {
                                        if (callback != null) {
                                            callback.polarFtpFeatureReady(deviceId);
                                        }
                                    }).toFlowable();
                        }
                    }
                    return Flowable.empty();
                })
                .subscribe(
                        o -> {
                        },
                        throwable -> logError("Error while monitoring session services: " + throwable.getMessage()),
                        () -> log("complete"));
        deviceDataMonitorDisposable.put(session.getAddress(), disposable);
    }

    private void tearDownDevice(final @NonNull BleDeviceSession session) {
        final String address = session.getAddress();
        if (deviceDataMonitorDisposable.containsKey(address)) {
            if (!deviceDataMonitorDisposable.get(address).isDisposed()) {
                Objects.requireNonNull(deviceDataMonitorDisposable.get(address)).dispose();
            }
            deviceDataMonitorDisposable.remove(address);
        }
    }

    @NonNull
    protected Exception handleError(@NonNull Throwable throwable) {
        if (throwable instanceof BleDisconnected) {
            return new PolarDeviceDisconnected();
        } else if (throwable instanceof BlePsFtpUtils.PftpResponseError) {
            int errorId = ((BlePsFtpUtils.PftpResponseError) throwable).getError();
            PftpError.PbPFtpError pftpError = PftpError.PbPFtpError.forNumber(errorId);
            if (pftpError != null) return new Exception(pftpError.toString());
        }
        return new Exception(throwable);
    }

    @Override
    public void stateChanged(boolean power) {
        if (callback != null) {
            callback.blePowerStateChanged(power);
        }
    }

    interface FetchRecursiveCondition {
        boolean include(String entry);
    }

    @NonNull
    protected Flowable<String> fetchRecursively(@NonNull final BlePsFtpClient client, @NonNull final String path, final FetchRecursiveCondition condition) {
        protocol.PftpRequest.PbPFtpOperation.Builder builder = protocol.PftpRequest.PbPFtpOperation.newBuilder();
        builder.setCommand(PftpRequest.PbPFtpOperation.Command.GET);
        builder.setPath(path);
        return client.request(builder.build().toByteArray())
                .toFlowable()
                .flatMap((Function<ByteArrayOutputStream, Publisher<String>>) byteArrayOutputStream -> {
                    PftpResponse.PbPFtpDirectory dir = PftpResponse.PbPFtpDirectory.parseFrom(byteArrayOutputStream.toByteArray());
                    Set<String> entries = new HashSet<>();
                    for (int i = 0; i < dir.getEntriesCount(); ++i) {
                        PftpResponse.PbPFtpEntry entry = dir.getEntries(i);
                        if (condition.include(entry.getName())) {
                            BleUtils.validate(entries.add(path + entry.getName()), "duplicate entry");
                        }
                    }
                    if (!entries.isEmpty()) {
                        return Flowable.fromIterable(entries)
                                .flatMap((Function<String, Publisher<String>>) s -> {
                                    if (s.endsWith("/")) {
                                        return fetchRecursively(client, s, condition);
                                    } else {
                                        return Flowable.just(s);
                                    }
                                });
                    }
                    return Flowable.empty();
                });
    }

    protected void log(@NonNull String message) {
        if (logger != null) {
            logger.message("" + message);
        }
    }

    protected void logError(@NonNull String message) {
        if (logger != null) {
            logger.message("Error: " + message);
        }
    }
}