package com.polar.polarsdkbroadcastdemo;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Map;

import polar.com.sdk.api.model.PolarHrBroadcastData;


public class PolarDeviceAdapter extends RecyclerView.Adapter<PolarDeviceAdapter.DeviceViewHolder> {

    private String TAG = "Polar_DeviceAdapter";

    private Map<String, PolarHrBroadcastData> broadcastData;

    public PolarDeviceAdapter(Map<String, PolarHrBroadcastData> broadcastData){
        this.broadcastData = broadcastData;
    }

    public class DeviceViewHolder extends RecyclerView.ViewHolder{
        private TextView textViewDeviceID;
        private TextView textViewHR;

        public DeviceViewHolder(View v){
            super(v);
            textViewDeviceID = v.findViewById(R.id.textViewDeviceID);
            textViewHR = v.findViewById(R.id.textViewHR);
        }
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
        View itemView = inflater.inflate(R.layout.layout_card, viewGroup, false);
        return new DeviceViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder deviceViewHolder, int i) {
        deviceViewHolder.textViewDeviceID.setText(String.valueOf(new ArrayList<>(broadcastData.values()).get(i).polarDeviceInfo.deviceId));
        deviceViewHolder.textViewHR.setText(String.valueOf(new ArrayList<>(broadcastData.values()).get(i).hr));
    }

    @Override
    public int getItemCount() {
        return broadcastData.size();
    }
}
