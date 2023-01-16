package com.polar.polarsdkecghrdemo

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.androidplot.xy.BoundaryMode
import com.androidplot.xy.StepMode
import com.androidplot.xy.XYGraphWidget
import com.androidplot.xy.XYPlot
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApi.DeviceStreamingFeature
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl.defaultImplementation
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarHrData
import java.text.DecimalFormat
import java.util.*

class HRActivity : AppCompatActivity(), PlotterListener {
    companion object {
        private const val TAG = "HRActivity"
    }

    private lateinit var api: PolarBleApi
    private lateinit var plotter: HrAndRrPlotter
    private lateinit var textViewHR: TextView
    private lateinit var textViewRR: TextView
    private lateinit var textViewDeviceId: TextView
    private lateinit var textViewBattery: TextView
    private lateinit var textViewFwVersion: TextView
    private lateinit var plot: XYPlot

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hr)
        val deviceId = intent.getStringExtra("id") ?: throw Exception("HRActivity couldn't be created, no deviceId given")
        textViewHR = findViewById(R.id.hr_view_hr)
        textViewRR = findViewById(R.id.hr_view_rr)
        textViewDeviceId = findViewById(R.id.hr_view_deviceId)
        textViewBattery = findViewById(R.id.hr_view_battery_level)
        textViewFwVersion = findViewById(R.id.hr_view_fw_version)
        plot = findViewById(R.id.hr_view_plot)

        api = defaultImplementation(
            applicationContext,
            PolarBleApi.FEATURE_BATTERY_INFO or
                    PolarBleApi.FEATURE_DEVICE_INFO or
                    PolarBleApi.FEATURE_HR
        )
        api.setApiLogger { str: String -> Log.d("SDK", str) }
        api.setApiCallback(object : PolarBleApiCallback() {
            override fun blePowerStateChanged(powered: Boolean) {
                Log.d(TAG, "BluetoothStateChanged $powered")
            }

            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "Device connected ${polarDeviceInfo.deviceId}")
                Toast.makeText(applicationContext, R.string.connected, Toast.LENGTH_SHORT).show()
            }

            override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "Device connecting ${polarDeviceInfo.deviceId}")
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "Device disconnected ${polarDeviceInfo.deviceId}")
            }

            override fun streamingFeaturesReady(identifier: String, features: Set<DeviceStreamingFeature>) {
                for (feature in features) {
                    Log.d(TAG, "Streaming feature is ready: $feature")
                }
            }

            override fun hrFeatureReady(identifier: String) {
                Log.d(TAG, "HR Feature ready $identifier")
            }

            override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
                if (uuid == UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb")) {
                    val msg = "Firmware: " + value.trim { it <= ' ' }
                    Log.d(TAG, "Firmware: " + identifier + " " + value.trim { it <= ' ' })
                    textViewFwVersion.append(msg.trimIndent())
                }
            }

            override fun batteryLevelReceived(identifier: String, level: Int) {
                Log.d(TAG, "Battery level $identifier $level%")
                val batteryLevelText = "Battery level: $level%"
                textViewBattery.append(batteryLevelText)
            }

            override fun hrNotificationReceived(identifier: String, data: PolarHrData) {
                Log.d(TAG, "HR ${data.hr} RR ${data.rrsMs}")

                if (data.rrsMs.isNotEmpty()) {
                    val rrText = "(${data.rrsMs.joinToString(separator = "ms, ")}ms)"
                    textViewRR.text = rrText
                }
                textViewHR.text = data.hr.toString()
                plotter.addValues(data)
            }

            override fun polarFtpFeatureReady(identifier: String) {
                Log.d(TAG, "Polar FTP ready $identifier")
            }
        })

        try {
            api.connectToDevice(deviceId)
        } catch (a: PolarInvalidArgument) {
            a.printStackTrace()
        }

        val deviceIdText = "ID: $deviceId"
        textViewDeviceId.text = deviceIdText

        plotter = HrAndRrPlotter()
        plotter.setListener(this)
        plot.addSeries(plotter.hrSeries, plotter.hrFormatter)
        plot.addSeries(plotter.rrSeries, plotter.rrFormatter)
        plot.setRangeBoundaries(50, 100, BoundaryMode.AUTO)
        plot.setDomainBoundaries(0, 360000, BoundaryMode.AUTO)
        // Left labels will increment by 10
        plot.setRangeStep(StepMode.INCREMENT_BY_VAL, 10.0)
        plot.setDomainStep(StepMode.INCREMENT_BY_VAL, 60000.0)
        // Make left labels be an integer (no decimal places)
        plot.graph.getLineLabelStyle(XYGraphWidget.Edge.LEFT).format = DecimalFormat("#")
        // These don't seem to have an effect
        plot.linesPerRangeLabel = 2
    }

    public override fun onDestroy() {
        super.onDestroy()
        api.shutDown()
    }

    override fun update() {
        runOnUiThread { plot.redraw() }
    }
}