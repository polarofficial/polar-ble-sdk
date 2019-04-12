package com.polar.polarsdkbroadcastdemo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import polar.com.sdk.api.PolarBleApi;
import polar.com.sdk.api.PolarBleApiCallback;
import polar.com.sdk.api.PolarBleApiDefaultImpl;
import polar.com.sdk.api.model.PolarDeviceInfo;
import polar.com.sdk.api.model.PolarHrBroadcastData;
import polar.com.sdk.api.model.PolarHrData;

public class BroadcastActivity extends AppCompatActivity {

    private PolarBleApi api;
    private String TAG = "Polar_MainActivity";
    private Disposable broadcastDisposable;

    private PolarDeviceAdapter polarDeviceAdapter;
    private Map<String, PolarHrBroadcastData> broadcastData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_br);
        broadcastData = new LinkedHashMap<>();

        api = PolarBleApiDefaultImpl.defaultImplementation(this, 0);
        api.setApiCallback(new PolarBleApiCallback() {
            @Override
            public void blePowerStateChanged(boolean b) {
                Log.d(TAG,"BluetoothStateChanged " + b);
            }

            @Override
            public void deviceConnected(PolarDeviceInfo s) {
            }

            @Override
            public void deviceConnecting(PolarDeviceInfo polarDeviceInfo) {

            }

            @Override
            public void deviceDisconnected(PolarDeviceInfo s) {
            }

            @Override
            public void ecgFeatureReady(String s) {
            }

            @Override
            public void accelerometerFeatureReady(String s) {
            }

            @Override
            public void ppgFeatureReady(String s) {
            }

            @Override
            public void ppiFeatureReady(String s) {
            }

            @Override
            public void biozFeatureReady(String s) {

            }

            @Override
            public void hrFeatureReady(String s) {
            }

            @Override
            public void disInformationReceived(String s, UUID u, String s1) {
            }

            @Override
            public void batteryLevelReceived(String s, int i) {
            }

            @Override
            public void hrNotificationReceived(String s, PolarHrData polarHrData) {
            }

            @Override
            public void polarFtpFeatureReady(String s) {
            }
        });


        RecyclerView rView = findViewById(R.id.recyclerView);
        RecyclerView.LayoutManager layoutManager = new GridLayoutManager(this,2);
        rView.setLayoutManager(layoutManager);
        polarDeviceAdapter = new PolarDeviceAdapter(broadcastData);

        rView.setAdapter(polarDeviceAdapter);

        broadcast();
    }

    public void broadcast(){
        if(broadcastDisposable == null){
            broadcastDisposable = api.startListenForPolarHrBroadcasts(null).observeOn(AndroidSchedulers.mainThread()).subscribe(
                    new Consumer<PolarHrBroadcastData>() {
                        @Override
                        public void accept(PolarHrBroadcastData polarHrBroadcastData) throws Exception {
                            String deviceID = polarHrBroadcastData.polarDeviceInfo.deviceId;
                            Log.d(TAG, deviceID);

                            if(broadcastData.containsKey(deviceID)){
                                if(Objects.requireNonNull(broadcastData.get(deviceID)).hr != polarHrBroadcastData.hr){
                                    broadcastData.replace(deviceID, polarHrBroadcastData);
                                    polarDeviceAdapter.notifyDataSetChanged();
                                }
                            } else {
                                broadcastData.put(deviceID, polarHrBroadcastData);
                                polarDeviceAdapter.notifyDataSetChanged();
                            }
                        }
                    },
                    new Consumer<Throwable>() {
                        @Override
                        public void accept(Throwable throwable) throws Exception {
                            Log.e("Polar_Broadcast", throwable.getLocalizedMessage());
                        }
                    },
                    new Action() {
                        @Override
                        public void run() throws Exception {
                            Log.e(TAG, "complete");
                        }
                    }
            );
        } else {
            Log.e("Polar_Broadcast", "Broadcast disposed");
            broadcastDisposable.dispose();
            broadcastDisposable = null;
        }
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        api.shutDown();
        api = null;
    }
}
