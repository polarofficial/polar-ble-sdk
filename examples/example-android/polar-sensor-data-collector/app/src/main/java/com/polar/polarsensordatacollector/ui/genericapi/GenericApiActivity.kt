package com.polar.polarsensordatacollector.ui.genericapi

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.lifecycle.ViewModelProvider
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.polarsensordatacollector.ui.exercise.GenericApiActivityScreen
import com.polar.polarsensordatacollector.ui.utils.FileUtils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class GenericApiActivity : AppCompatActivity() {

    private lateinit var viewModel: GenericApiViewModel
    @Inject
    lateinit var polarDeviceRepository: PolarDeviceRepository
    @Inject
    lateinit var fileUtils: FileUtils
    lateinit var deviceId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        deviceId = polarDeviceRepository.deviceConnectionStatus.value.identifier
        if (deviceId.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_no_device), Toast.LENGTH_SHORT).show()
            finish(); return
        }

        viewModel = ViewModelProvider(
            this,
            GenericApiViewModelFactory(polarDeviceRepository, fileUtils, this, deviceId)
        )[GenericApiViewModel::class.java]

        setContent {
            MaterialTheme(colors = darkColors()) {
                GenericApiActivityScreen (
                    viewModel = viewModel
                )
            }
        }
    }
    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, GenericApiActivity::class.java))
        }
    }
}
