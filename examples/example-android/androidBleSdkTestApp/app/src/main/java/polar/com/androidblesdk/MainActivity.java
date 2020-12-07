package polar.com.androidblesdk;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.widget.Button;

import org.reactivestreams.Publisher;

import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Function;
import polar.com.sdk.api.PolarBleApi;
import polar.com.sdk.api.PolarBleApiCallback;
import polar.com.sdk.api.PolarBleApiDefaultImpl;
import polar.com.sdk.api.errors.PolarInvalidArgument;
import polar.com.sdk.api.model.PolarAccelerometerData;
import polar.com.sdk.api.model.PolarDeviceInfo;
import polar.com.sdk.api.model.PolarEcgData;
import polar.com.sdk.api.model.PolarExerciseEntry;
import polar.com.sdk.api.model.PolarHrData;
import polar.com.sdk.api.model.PolarOhrPPGData;
import polar.com.sdk.api.model.PolarOhrPPIData;
import polar.com.sdk.api.model.PolarSensorSetting;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String API_LOGGER_TAG = "API LOGGER";
    PolarBleApi api;
    Disposable broadcastDisposable;
    Disposable ecgDisposable;
    Disposable accDisposable;
    Disposable ppgDisposable;
    Disposable ppiDisposable;
    Disposable scanDisposable;
    String DEVICE_ID = "0A34442D"; // or bt address like F5:A7:B8:EF:7A:D1 // TODO replace with your device id
    Disposable autoConnectDisposable;
    PolarExerciseEntry exerciseEntry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Notice PolarBleApi.ALL_FEATURES are enabled
        api = PolarBleApiDefaultImpl.defaultImplementation(this, PolarBleApi.ALL_FEATURES);
        api.setPolarFilter(false);

        final Button broadcast = this.findViewById(R.id.broadcast_button);
        final Button connect = this.findViewById(R.id.connect_button);
        final Button disconnect = this.findViewById(R.id.disconnect_button);
        final Button autoConnect = this.findViewById(R.id.auto_connect_button);
        final Button ecg = this.findViewById(R.id.ecg_button);
        final Button acc = this.findViewById(R.id.acc_button);
        final Button ppg = this.findViewById(R.id.ohr_ppg_button);
        final Button ppi = this.findViewById(R.id.ohr_ppi_button);
        final Button scan = this.findViewById(R.id.scan_button);
        final Button list = this.findViewById(R.id.list_exercises);
        final Button read = this.findViewById(R.id.read_exercise);
        final Button remove = this.findViewById(R.id.remove_exercise);
        final Button startH10Recording = this.findViewById(R.id.start_h10_recording);
        final Button stopH10Recording = this.findViewById(R.id.stop_h10_recording);
        final Button H10RecordingStatus = this.findViewById(R.id.h10_recording_status);
        final Button setTime = this.findViewById(R.id.set_time);

        api.setApiLogger(s -> Log.d(API_LOGGER_TAG, s));

        Log.d(TAG, "version: " + PolarBleApiDefaultImpl.versionInfo());

        api.setApiCallback(new PolarBleApiCallback() {
            @Override
            public void blePowerStateChanged(boolean powered) {
                Log.d(TAG, "BLE power: " + powered);
            }

            @Override
            public void deviceConnected(@NonNull PolarDeviceInfo polarDeviceInfo) {
                Log.d(TAG, "CONNECTED: " + polarDeviceInfo.deviceId);
                DEVICE_ID = polarDeviceInfo.deviceId;
            }

            @Override
            public void deviceConnecting(@NonNull PolarDeviceInfo polarDeviceInfo) {
                Log.d(TAG, "CONNECTING: " + polarDeviceInfo.deviceId);
                DEVICE_ID = polarDeviceInfo.deviceId;
            }

            @Override
            public void deviceDisconnected(@NonNull PolarDeviceInfo polarDeviceInfo) {
                Log.d(TAG, "DISCONNECTED: " + polarDeviceInfo.deviceId);
                ecgDisposable = null;
                accDisposable = null;
                ppgDisposable = null;
                ppiDisposable = null;
            }

            @Override
            public void ecgFeatureReady(@NonNull String identifier) {
                Log.d(TAG, "ECG READY: " + identifier);
                // ecg streaming can be started now if needed
            }

            @Override
            public void accelerometerFeatureReady(@NonNull String identifier) {
                Log.d(TAG, "ACC READY: " + identifier);
                // acc streaming can be started now if needed
            }

            @Override
            public void ppgFeatureReady(@NonNull String identifier) {
                Log.d(TAG, "PPG READY: " + identifier);
                // ohr ppg can be started
            }

            @Override
            public void ppiFeatureReady(@NonNull String identifier) {
                Log.d(TAG, "PPI READY: " + identifier);
                // ohr ppi can be started
            }

            @Override
            public void biozFeatureReady(@NonNull String identifier) {
                Log.d(TAG, "BIOZ READY: " + identifier);
                // ohr ppi can be started
            }

            @Override
            public void hrFeatureReady(@NonNull String identifier) {
                Log.d(TAG, "HR READY: " + identifier);
                // hr notifications are about to start
            }

            @Override
            public void disInformationReceived(@NonNull String identifier, @NonNull UUID uuid, @NonNull String value) {
                Log.d(TAG, "uuid: " + uuid + " value: " + value);

            }

            @Override
            public void batteryLevelReceived(@NonNull String identifier, int level) {
                Log.d(TAG, "BATTERY LEVEL: " + level);

            }

            @Override
            public void hrNotificationReceived(@NonNull String identifier, @NonNull PolarHrData data) {
                Log.d(TAG, "HR value: " + data.hr + " rrsMs: " + data.rrsMs + " rr: " + data.rrs + " contact: " + data.contactStatus + "," + data.contactStatusSupported);
            }

            @Override
            public void polarFtpFeatureReady(@NonNull String s) {
                Log.d(TAG, "FTP ready");
            }
        });

        list.setOnClickListener(v -> api.listExercises(DEVICE_ID).observeOn(AndroidSchedulers.mainThread()).subscribe(
                polarExerciseEntry -> {
                    Log.d(TAG, "next: " + polarExerciseEntry.date + " path: " + polarExerciseEntry.path + " id: " + polarExerciseEntry.identifier);
                    exerciseEntry = polarExerciseEntry;
                },
                throwable -> Log.e(TAG, "fetch exercises failed: " + throwable.getLocalizedMessage()),
                () -> Log.d(TAG, "complete")
        ));

        read.setOnClickListener(v -> {
            if (exerciseEntry != null) {
                api.fetchExercise(DEVICE_ID, exerciseEntry).observeOn(AndroidSchedulers.mainThread()).subscribe(
                        polarExerciseData -> Log.d(TAG, "exercise data count: " + polarExerciseData.hrSamples.size() + " samples: " + polarExerciseData.hrSamples),
                        throwable -> Log.e(TAG, "Failed to read exercise: " + throwable.getLocalizedMessage())
                );
            }
        });

        remove.setOnClickListener(v -> {
            if (exerciseEntry != null) {
                api.removeExercise(DEVICE_ID, exerciseEntry).observeOn(AndroidSchedulers.mainThread()).subscribe(
                        () -> Log.d(TAG, "ex removed ok"),
                        throwable -> Log.d(TAG, "ex remove failed: " + throwable.getLocalizedMessage())
                );
            }
        });

        broadcast.setOnClickListener(v -> {
            if (broadcastDisposable == null) {
                broadcastDisposable = api.startListenForPolarHrBroadcasts(null).subscribe(
                        polarBroadcastData -> Log.d(TAG, "HR BROADCAST " +
                                polarBroadcastData.polarDeviceInfo.deviceId + " HR: " +
                                polarBroadcastData.hr + " batt: " +
                                polarBroadcastData.batteryStatus),
                        throwable -> Log.e(TAG, "" + throwable.getLocalizedMessage()),
                        () -> Log.d(TAG, "complete")
                );
            } else {
                broadcastDisposable.dispose();
                broadcastDisposable = null;
            }
        });

        connect.setOnClickListener(v -> {
            try {
                api.connectToDevice(DEVICE_ID);
            } catch (PolarInvalidArgument polarInvalidArgument) {
                polarInvalidArgument.printStackTrace();
            }
        });

        disconnect.setOnClickListener(view -> {
            try {
                api.disconnectFromDevice(DEVICE_ID);
            } catch (PolarInvalidArgument polarInvalidArgument) {
                polarInvalidArgument.printStackTrace();
            }
        });

        autoConnect.setOnClickListener(view -> {
            if (autoConnectDisposable != null) {
                autoConnectDisposable.dispose();
                autoConnectDisposable = null;
            }
            autoConnectDisposable = api.autoConnectToDevice(-50, "180D", null).subscribe(
                    () -> Log.d(TAG, "auto connect search complete"),
                    throwable -> Log.e(TAG, "" + throwable.toString())
            );
        });

        ecg.setOnClickListener(v -> {
            if (ecgDisposable == null) {
                ecgDisposable = api.requestEcgSettings(DEVICE_ID)
                        .toFlowable()
                        .flatMap((Function<PolarSensorSetting, Publisher<PolarEcgData>>) polarEcgSettings -> {
                            PolarSensorSetting sensorSetting = polarEcgSettings.maxSettings();
                            return api.startEcgStreaming(DEVICE_ID, sensorSetting);
                        }).subscribe(
                                polarEcgData -> {
                                    for (Integer microVolts : polarEcgData.samples) {
                                        Log.d(TAG, "    yV: " + microVolts);
                                    }
                                },
                                throwable -> Log.e(TAG, "" + throwable.toString()),
                                () -> Log.d(TAG, "complete")
                        );
            } else {
                // NOTE stops streaming if it is "running"
                ecgDisposable.dispose();
                ecgDisposable = null;
            }
        });

        acc.setOnClickListener(v -> {
            if (accDisposable == null) {
                accDisposable = api.requestAccSettings(DEVICE_ID)
                        .toFlowable()
                        .flatMap((Function<PolarSensorSetting, Publisher<PolarAccelerometerData>>) settings -> {
                            PolarSensorSetting sensorSetting = settings.maxSettings();
                            return api.startAccStreaming(DEVICE_ID, sensorSetting);
                        }).observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                polarAccelerometerData -> {
                                    for (PolarAccelerometerData.PolarAccelerometerSample data : polarAccelerometerData.samples) {
                                        Log.d(TAG, "    x: " + data.x + " y: " + data.y + " z: " + data.z);
                                    }
                                },
                                throwable -> Log.e(TAG, "" + throwable.getLocalizedMessage()),
                                () -> Log.d(TAG, "complete")
                        );
            } else {
                // NOTE dispose will stop streaming if it is "running"
                accDisposable.dispose();
                accDisposable = null;
            }
        });

        ppg.setOnClickListener(v -> {
            if (ppgDisposable == null) {
                ppgDisposable = api.requestPpgSettings(DEVICE_ID)
                        .toFlowable()
                        .flatMap((Function<PolarSensorSetting, Publisher<PolarOhrPPGData>>) polarPPGSettings -> api.startOhrPPGStreaming(DEVICE_ID, polarPPGSettings.maxSettings()))
                        .subscribe(
                                polarOhrPPGData -> {
                                    for (PolarOhrPPGData.PolarOhrPPGSample data : polarOhrPPGData.samples) {
                                        Log.d(TAG, "    ppg0: " + data.ppg0 + " ppg1: " + data.ppg1 + " ppg2: " + data.ppg2 + "ambient: " + data.ambient);
                                    }
                                },
                                throwable -> Log.e(TAG, "" + throwable.getLocalizedMessage()),
                                () -> Log.d(TAG, "complete")
                        );
            } else {
                ppgDisposable.dispose();
                ppgDisposable = null;
            }
        });

        ppi.setOnClickListener(v -> {
            if (ppiDisposable == null) {
                ppiDisposable = api.startOhrPPIStreaming(DEVICE_ID)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                ppiData -> {
                                    for (PolarOhrPPIData.PolarOhrPPISample sample : ppiData.samples) {
                                        Log.d(TAG, "ppi: " + sample.ppi
                                                + " blocker: " + sample.blockerBit + " errorEstimate: " + sample.errorEstimate);
                                    }
                                },
                                throwable -> Log.e(TAG, "" + throwable.getLocalizedMessage()),
                                () -> Log.d(TAG, "complete")
                        );
            } else {
                ppiDisposable.dispose();
                ppiDisposable = null;
            }
        });

        scan.setOnClickListener(view -> {
            if (scanDisposable == null) {
                scanDisposable = api.searchForDevice()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                polarDeviceInfo -> Log.d(TAG, "polar device found id: " + polarDeviceInfo.deviceId + " address: " + polarDeviceInfo.address + " rssi: " + polarDeviceInfo.rssi + " name: " + polarDeviceInfo.name + " isConnectable: " + polarDeviceInfo.isConnectable),
                                throwable -> Log.d(TAG, "" + throwable.getLocalizedMessage()),
                                () -> Log.d(TAG, "complete")
                        );
            } else {
                scanDisposable.dispose();
                scanDisposable = null;
            }
        });

        startH10Recording.setOnClickListener(view -> api.startRecording(DEVICE_ID, "TEST_APP_ID", PolarBleApi.RecordingInterval.INTERVAL_1S, PolarBleApi.SampleType.HR).subscribe(
                () -> Log.d(TAG, "recording started"),
                throwable -> Log.e(TAG, "recording start failed: " + throwable.getLocalizedMessage())
        ));

        stopH10Recording.setOnClickListener(view -> api.stopRecording(DEVICE_ID).subscribe(
                () -> Log.d(TAG, "recording stopped"),
                throwable -> Log.e(TAG, "recording stop failed: " + throwable.getLocalizedMessage())
        ));

        H10RecordingStatus.setOnClickListener(view -> api.requestRecordingStatus(DEVICE_ID).subscribe(
                pair -> Log.d(TAG, "recording on: " + pair.first + " ID: " + pair.second),
                throwable -> Log.e(TAG, "recording status failed: " + throwable.getLocalizedMessage())
        ));

        setTime.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(new Date());
            api.setLocalTime(DEVICE_ID, calendar).subscribe(
                    () -> Log.d(TAG, "time set to device"),
                    throwable -> Log.d(TAG, "set time failed: " + throwable.getLocalizedMessage()));
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && savedInstanceState == null) {
            this.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 1) {
            Log.d(TAG, "bt ready");
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        api.backgroundEntered();
    }

    @Override
    public void onResume() {
        super.onResume();
        api.foregroundEntered();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        api.shutDown();
    }
}
