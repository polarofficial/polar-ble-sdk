package com.polar.polarsdkecghrdemo

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl.defaultImplementation
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarDeviceInfo
import java.util.*

class ImageActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "ImageActivity"
    }

    private lateinit var textViewDeviceId: TextView
    private lateinit var imageViewGeneratedImages: ImageView
    private lateinit var imageTestButton: Button

    private lateinit var deviceId: String
    private lateinit var participantNumber: Number

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_images)
        deviceId = intent.getStringExtra("id") ?: throw Exception("ImageActivity couldn't be created, no device id given")
        //Note if no value was given, defaultValue is -1
        participantNumber = intent.getIntExtra("participant", -1)
        try {
            assert(participantNumber != -1)
        } catch (assertionError: AssertionError) {
            Log.e(TAG, "Participant number was not correctly initialized, further details value was -1")
            //Should not happen but would instantly kill the activity
            throw Exception("Participant number was not correct")
        }
        textViewDeviceId = findViewById(R.id.deviceId)
        imageViewGeneratedImages = findViewById(R.id.generated_image)
        imageTestButton = findViewById(R.id.buttonTestImage)

        val deviceIdText = "Device ID: $deviceId"
        textViewDeviceId.text = deviceIdText

        imageTestButton.setOnClickListener {
            imageViewGeneratedImages.setImageResource(R.drawable.imagetest)
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
    }
}