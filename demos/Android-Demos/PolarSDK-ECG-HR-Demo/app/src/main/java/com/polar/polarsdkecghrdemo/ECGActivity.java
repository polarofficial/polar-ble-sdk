package com.polar.polarsdkecghrdemo;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.StepMode;
import com.androidplot.xy.XYPlot;
import com.polar.sdk.api.PolarBleApi;
import com.polar.sdk.api.PolarBleApiCallback;
import com.polar.sdk.api.PolarBleApiDefaultImpl;
import com.polar.sdk.api.errors.PolarInvalidArgument;
import com.polar.sdk.api.model.PolarDeviceInfo;
import com.polar.sdk.api.model.PolarEcgData;
import com.polar.sdk.api.model.PolarHrData;
import com.polar.sdk.api.model.PolarSensorSetting;

import org.reactivestreams.Publisher;

import java.util.Set;
import java.util.UUID;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Function;

public class ECGActivity extends AppCompatActivity implements PlotterListener {
    private static final String TAG = "ECGActivity";

    private PolarBleApi api;
    private TextView textViewHR;
    private TextView textViewFW;
    private XYPlot plot;
    private Plotter plotter;

    private Disposable ecgDisposable = null;
    private String deviceId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ecg);
        deviceId = getIntent().getStringExtra("id");
        textViewHR = findViewById(R.id.info);
        textViewFW = findViewById(R.id.fw);

        plot = findViewById(R.id.plot);

        api = PolarBleApiDefaultImpl.defaultImplementation(getApplicationContext(),
                PolarBleApi.FEATURE_POLAR_SENSOR_STREAMING |
                        PolarBleApi.FEATURE_BATTERY_INFO |
                        PolarBleApi.FEATURE_DEVICE_INFO |
                        PolarBleApi.FEATURE_HR);
        api.setApiCallback(new PolarBleApiCallback() {
            @Override
            public void blePowerStateChanged(boolean b) {
                Log.d(TAG, "BluetoothStateChanged " + b);
            }

            @Override
            public void deviceConnected(@NonNull PolarDeviceInfo s) {
                Log.d(TAG, "Device connected " + s.deviceId);
                Toast.makeText(getApplicationContext(), R.string.connected, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void deviceConnecting(@NonNull PolarDeviceInfo polarDeviceInfo) {

            }

            @Override
            public void deviceDisconnected(@NonNull PolarDeviceInfo s) {
                Log.d(TAG, "Device disconnected " + s);
            }

            @Override
            public void streamingFeaturesReady(@NonNull final String identifier,
                                               @NonNull final Set<PolarBleApi.DeviceStreamingFeature> features) {

                for (PolarBleApi.DeviceStreamingFeature feature : features) {
                    Log.d(TAG, "Streaming feature is ready: " + feature);
                    switch (feature) {
                        case ECG:
                            streamECG();
                            break;
                        case ACC:
                        case MAGNETOMETER:
                        case GYRO:
                        case PPI:
                        case PPG:
                            break;
                    }
                }
            }

            @Override
            public void hrFeatureReady(@NonNull String s) {
                Log.d(TAG, "HR Feature ready " + s);
            }

            @Override
            public void disInformationReceived(@NonNull String s, @NonNull UUID u, @NonNull String s1) {
                if (u.equals(UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb"))) {
                    String msg = "Firmware: " + s1.trim();
                    Log.d(TAG, "Firmware: " + s + " " + s1.trim());
                    textViewFW.append(msg + "\n");
                }
            }

            @Override
            public void batteryLevelReceived(@NonNull String s, int i) {
                String msg = "ID: " + s + "\nBattery level: " + i;
                Log.d(TAG, "Battery level " + s + " " + i);
//                Toast.makeText(classContext, msg, Toast.LENGTH_LONG).show();
                textViewFW.append(msg + "\n");
            }

            @Override
            public void hrNotificationReceived(@NonNull String s, @NonNull PolarHrData polarHrData) {
                Log.d(TAG, "HR " + polarHrData.hr);
                textViewHR.setText(String.valueOf(polarHrData.hr));
            }

            @Override
            public void polarFtpFeatureReady(@NonNull String s) {
                Log.d(TAG, "Polar FTP ready " + s);
            }
        });
        try {
            api.connectToDevice(deviceId);
        } catch (PolarInvalidArgument a) {
            a.printStackTrace();
        }

        plotter = new Plotter("ECG");
        plotter.setListener(this);

        plot.addSeries(plotter.getSeries(), plotter.getFormatter());
        plot.setRangeBoundaries(-3.3, 3.3, BoundaryMode.FIXED);
        plot.setRangeStep(StepMode.INCREMENT_BY_FIT, 0.55);
        plot.setDomainBoundaries(0, 500, BoundaryMode.GROW);
        plot.setLinesPerRangeLabel(2);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (ecgDisposable != null && !ecgDisposable.isDisposed()) {
            ecgDisposable.dispose();
        }
        api.shutDown();
    }

    public void streamECG() {
        if (ecgDisposable == null) {
            ecgDisposable =
                    api.requestStreamSettings(deviceId, PolarBleApi.DeviceStreamingFeature.ECG)
                            .toFlowable()
                            .flatMap((Function<PolarSensorSetting, Publisher<PolarEcgData>>) sensorSetting -> api.startEcgStreaming(deviceId, sensorSetting.maxSettings()))
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                    polarEcgData -> {
                                        Log.d(TAG, "ecg update");
                                        for (Integer data : polarEcgData.samples) {
                                            plotter.sendSingleSample((float) ((float) data / 1000.0));
                                        }
                                    },
                                    throwable -> {
                                        Log.e(TAG,
                                                "" + throwable.getLocalizedMessage());
                                        ecgDisposable = null;
                                    },
                                    () -> Log.d(TAG, "complete")
                            );
        } else {
            // NOTE stops streaming if it is "running"
            ecgDisposable.dispose();
            ecgDisposable = null;
        }
    }

    @Override
    public void update() {
        runOnUiThread(() -> plot.redraw());
    }
}
