package com.polar.polarsdkexercisedemo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.widget.Toast;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.StepMode;
import com.androidplot.xy.XYPlot;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import polar.com.sdk.api.PolarBleApi;
import polar.com.sdk.api.PolarBleApiCallback;
import polar.com.sdk.api.PolarBleApiDefaultImpl;
import polar.com.sdk.api.errors.PolarInvalidArgument;
import polar.com.sdk.api.model.PolarDeviceInfo;
import polar.com.sdk.api.model.PolarExerciseEntry;
import polar.com.sdk.api.model.PolarHrData;

public class ExerciseActivity extends Activity implements PlotterListener, ItemClickListener {

    private String TAG = "Polar_ECGActivity";
    public PolarBleApi api;
    private Context classContext = this;

    private RecyclerView rView;
    private RecyclerView.Adapter adapter;
    private RecyclerView.LayoutManager layoutManager;
    private String deviceId;
    private ArrayList<PolarExerciseEntry> entries;

    private XYPlot plot;
    private Plotter plotter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exe);
        entries = new ArrayList<>();

        plot = findViewById(R.id.plot);

        rView = findViewById(R.id.recyclerView);
        layoutManager = new LinearLayoutManager(this);
        rView.setLayoutManager(layoutManager);

        api = PolarBleApiDefaultImpl.defaultImplementation(this, PolarBleApi.FEATURE_POLAR_FILE_TRANSFER);
        api.setApiCallback(new PolarBleApiCallback() {
            @Override
            public void blePowerStateChanged(boolean b) {
                Log.d(TAG,"BluetoothStateChanged " + b);
            }

            @Override
            public void deviceConnected(PolarDeviceInfo s) {
                Log.d(TAG,"Device connected " + s);
                deviceId = s.deviceId;
                Toast.makeText(classContext, getString(R.string.connected) + " " + s.deviceId, Toast.LENGTH_SHORT).show();
                adapter = new ExerciseAdapter(entries, api, deviceId);
                ((ExerciseAdapter) adapter).setListener((ItemClickListener) classContext);
                rView.setAdapter(adapter);

                RecyclerView.ItemDecoration itemDecoration = new
                        DividerItemDecoration(classContext, DividerItemDecoration.VERTICAL);
                rView.addItemDecoration(itemDecoration);
            }

            @Override
            public void deviceConnecting(PolarDeviceInfo polarDeviceInfo) {

            }

            @Override
            public void deviceDisconnected(PolarDeviceInfo s) {
                Log.d(TAG,"Device disconnected " + s.deviceId);
            }

            @Override
            public void ecgFeatureReady(String s) {
                Log.d(TAG,"ECG Feature ready " + s);
            }

            @Override
            public void accelerometerFeatureReady(String s) {
                Log.d(TAG,"ACC Feature ready " + s);
            }

            @Override
            public void ppgFeatureReady(String s) {
                Log.d(TAG,"PPG Feature ready " + s);
            }

            @Override
            public void ppiFeatureReady(String s) {
                Log.d(TAG,"PPI Feature ready " + s);
            }

            @Override
            public void biozFeatureReady(String s) {

            }

            @Override
            public void hrFeatureReady(String s) {
                Log.d(TAG,"HR Feature ready " + s);
            }

            @Override
            public void disInformationReceived(String s, UUID u, String s1) {
            }

            @Override
            public void batteryLevelReceived(String s, int i) {
            }

            @Override
            public void hrNotificationReceived(String s, PolarHrData polarHrData) {
                //Log.d(TAG,"HR " + polarHrData.getHr());
            }

            @Override
            public void polarFtpFeatureReady(String s) {
                Log.d(TAG,"Polar FTP ready " + s);
                listExercises();
            }
        });

        api.autoConnectToDevice(-50, "180D", null).subscribe();

        plotter = new Plotter(this, "Exercise");
        plotter.setListener(this);

        plot.addSeries(plotter.getSeries(), plotter.getFormatter());
        plot.setRangeBoundaries(0,240,BoundaryMode.FIXED);
        plot.setRangeStep(StepMode.INCREMENT_BY_FIT,10);
        plot.setDomainBoundaries(0,10, BoundaryMode.AUTO);
    }

    @SuppressLint("CheckResult")
    public void listExercises(){
        api.listExercises(deviceId).observeOn(AndroidSchedulers.mainThread()).subscribe(
            new Consumer<PolarExerciseEntry>() {
                @Override
                public void accept(PolarExerciseEntry polarExerciseEntry) throws Exception {
                    entries.add(polarExerciseEntry);
                    adapter.notifyDataSetChanged();
                }
            },
            new Consumer<Throwable>() {
                @Override
                public void accept(Throwable throwable) throws Exception {
                    Log.d(TAG,throwable.getLocalizedMessage());
                }
            },
            new Action() {
                @Override
                public void run() throws Exception {

                }
            }
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            api.disconnectFromDevice(deviceId);
        } catch (PolarInvalidArgument polarInvalidArgument) {
            polarInvalidArgument.printStackTrace();
        }
    }

    //PlotterListener
    @Override
    public void update() {
        plot.redraw();
    }


    //ItemClickListener
    @Override
    public void update(List<Integer> data) {
        plotter.sendSeries(data);
    }
}
