package com.polar.polarsdkecghrdemo;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "Polar_MainActivity";
    private static final String SHARED_PREFS_KEY = "polar_device_id";
    private String deviceId;
    SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sharedPreferences = this.getPreferences(Context.MODE_PRIVATE);
        checkBT();
    }

    public void onClickConnect(View view) {
        checkBT();
        deviceId = sharedPreferences.getString(SHARED_PREFS_KEY, "");
        Log.d(TAG, deviceId);
        if (deviceId.equals("")) {
            showDialog(view);
        } else {
            Toast.makeText(this, getString(R.string.connecting) + " " + deviceId, Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, ECGActivity.class);
            intent.putExtra("id", deviceId);
            startActivity(intent);
        }
    }

    public void onClickConnect2(View view) {
        checkBT();
        deviceId = sharedPreferences.getString(SHARED_PREFS_KEY, "");
        Log.d(TAG, deviceId);
        if (deviceId.equals("")) {
            showDialog(view);
        } else {
            Toast.makeText(this, getString(R.string.connecting) + " " + deviceId, Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, HRActivity.class);
            intent.putExtra("id", deviceId);
            startActivity(intent);
        }
    }

    public void onClickChangeID(View view) {
        showDialog(view);
    }

    public void showDialog(View view) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this, R.style.PolarTheme);
        dialog.setTitle("Enter your Polar device's ID");

        View viewInflated = LayoutInflater.from(getApplicationContext()).inflate(R.layout.device_id_dialog_layout, (ViewGroup) view.getRootView(), false);

        final EditText input = viewInflated.findViewById(R.id.input);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        dialog.setView(viewInflated);

        dialog.setPositiveButton("OK", (dialog1, which) -> {
            deviceId = input.getText().toString();
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(SHARED_PREFS_KEY, deviceId);
            editor.apply();
        });
        dialog.setNegativeButton("Cancel", (dialog12, which) -> dialog12.cancel());
        dialog.show();
    }

    public void checkBT() {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter != null && !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 2);
        }

        //requestPermissions() method needs to be called when the build SDK version is 23 or above
        if (Build.VERSION.SDK_INT >= 23) {
            this.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
    }
}
