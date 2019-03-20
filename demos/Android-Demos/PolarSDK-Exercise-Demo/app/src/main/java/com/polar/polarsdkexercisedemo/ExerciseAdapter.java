package com.polar.polarsdkexercisedemo;

import android.annotation.SuppressLint;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.Single;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import polar.com.sdk.api.PolarBleApi;
import polar.com.sdk.api.model.PolarExerciseData;
import polar.com.sdk.api.model.PolarExerciseEntry;

public class ExerciseAdapter extends RecyclerView.Adapter<ExerciseAdapter.ExerciseViewHolder> {

    PolarBleApi api;
    private String deviceId;
    private ArrayList<PolarExerciseEntry> mExeList;
    private ItemClickListener itemClickListener;

    @SuppressLint("CheckResult")
    public void getExerciseData(int pos){
        Single<PolarExerciseData> data = api.fetchExercise(deviceId, mExeList.get(pos));
        data.subscribe(
                new Consumer<PolarExerciseData>() {
                    @Override
                    public void accept(PolarExerciseData polarExerciseData) throws Exception {
                        itemClickListener.update(polarExerciseData.hrSamples);
                    }
                },
                new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        Log.e("ExerciseAdapter", throwable.getLocalizedMessage());
                    }
                }
        );
    }

    public ExerciseAdapter(ArrayList<PolarExerciseEntry> entries, PolarBleApi api, String deviceId){
        mExeList = entries;
        this.deviceId = deviceId;
        this.api = api;
    }

    public void setListener(ItemClickListener itemClickListener){
        this.itemClickListener = itemClickListener;
    }

    public static class ExerciseViewHolder extends RecyclerView.ViewHolder{
        TextView textView;
        public ExerciseViewHolder(View v) {
            super(v);
            textView = v.findViewById(R.id.textView);
        }
    }

    @NonNull
    @Override
    public ExerciseViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, final int i) {
        LayoutInflater layoutInflater = LayoutInflater.from(viewGroup.getContext());
        View itemView = layoutInflater.inflate(R.layout.list_item, viewGroup, false);
        return new ExerciseViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull ExerciseViewHolder exerciseViewHolder, final int i) {
        exerciseViewHolder.textView.setText(String.valueOf(mExeList.get(i).date));
        exerciseViewHolder.textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getExerciseData(i);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mExeList.size();
    }
}
