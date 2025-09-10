package com.polar.polarsensordatacollector.ui.exercise

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.di.PolarBleSdkModule
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ExerciseActivity : AppCompatActivity() {

    @Inject lateinit var polarDeviceRepository: PolarDeviceRepository

    private lateinit var deviceId: String
    private lateinit var viewModel: ExerciseViewModel

    private var onNotifPermissionGranted: (() -> Unit)? = null
    private val requestNotifPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            toast(getString(R.string.toast_notif_disabled))
            onNotifPermissionGranted = null
        } else {
            onNotifPermissionGranted?.invoke()
            onNotifPermissionGranted = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        deviceId = polarDeviceRepository.deviceConnectionStatus.value.deviceId
        if (deviceId.isEmpty()) {
            toast(getString(R.string.toast_no_device))
            finish(); return
        }

        val exerciseApi = PolarBleSdkModule.providePolarBleSdkApi(this)
        ServiceLocator.exerciseApi = exerciseApi
        ServiceLocator.bleApi = exerciseApi

        viewModel = ViewModelProvider(
            this,
            ExerciseViewModelFactory(exerciseApi, deviceId, this)
        )[ExerciseViewModel::class.java]

        setContent {
            MaterialTheme(colors = darkColors()) {
                ExerciseScreen(
                    vm = viewModel,
                    onStart = {
                        toast(getString(R.string.toast_starting))
                        viewModel.start()
                        ensureNotificationPermission { startExerciseService() }
                    },
                    onPause = { toast(getString(R.string.toast_pausing)); viewModel.pause() },
                    onResume = { toast(getString(R.string.toast_resuming)); viewModel.resume() },
                    onStop = { toast(getString(R.string.toast_stopping)); viewModel.stop() },
                    onStatusToast = { msg -> toast(msg) }
                )
            }
        }

        toast(getString(R.string.toast_controlling, deviceId))
    }

    private fun ensureNotificationPermission(onGranted: () -> Unit) {
        if (Build.VERSION.SDK_INT < 33) { onGranted(); return }
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) onGranted() else {
            onNotifPermissionGranted = onGranted
            requestNotifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startExerciseService(action: String? = null) {
        val intent = Intent(this, ExerciseService::class.java).apply {
            if (action != null) this.action = action
            putExtra(ExerciseService.EXTRA_DEVICE_ID, deviceId)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.startForegroundService(intent)
        } else {
            applicationContext.startService(intent)
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, ExerciseActivity::class.java))
        }
    }
}