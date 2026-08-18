package com.polar.polarsensordatacollector.ui.logging

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Button
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.ui.utils.ErrorLogUtils
import com.polar.polarsensordatacollector.ui.utils.ZipCompressionHelper
import com.polar.polarsensordatacollector.ui.utils.showSnackBar
import com.polar.sdk.api.model.LogConfig
import com.polar.sdk.api.model.PolarDeviceLog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoggingFragment: Fragment(R.layout.fragment_logging) {

    private lateinit var ohrLoggingButton: Button
    private lateinit var ppiLoggingButton: Button
    private lateinit var accLoggingButton: Button
    private lateinit var skinTemperatureLoggingButton: Button
    private lateinit var metLoggingButton: Button
    private lateinit var caloriesLoggingButton: Button
    private lateinit var sleepLoggingButton: Button
    private lateinit var fetchErrorLogButton: Button
    private lateinit var exportDeviceLogsButton: Button
    private lateinit var exportDeviceLogsProgress: CircularProgressIndicator

    private val viewModel: LoggingViewModel by viewModels()
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupViews(view)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiLogConfigState.collect {
                    logConfigStatusChange(it)
                }
            }
        }

        ohrLoggingButton.setOnClickListener {
            viewModel.ohrLogging()
        }

        ppiLoggingButton.setOnClickListener {
            viewModel.ppiLogging()
        }

        accLoggingButton.setOnClickListener {
            viewModel.accLogging()
        }

        skinTemperatureLoggingButton.setOnClickListener {
            viewModel.skinTempLogging()
        }

        metLoggingButton.setOnClickListener {
            viewModel.metLogging()
        }

        caloriesLoggingButton.setOnClickListener {
            viewModel.caloriesLogging()
        }

        sleepLoggingButton.setOnClickListener {
            viewModel.sleepLogging()
        }
        fetchErrorLogButton.setOnClickListener {
            viewModel.fetchErrorLog()

            viewModel.errorlogLiveData.observe(viewLifecycleOwner) { errorlog ->
                if (errorlog != null) {
                    val context = requireContext()
                    ErrorLogUtils.saveErrorLogToFile(context, errorlog.errorLog)
                    ErrorLogUtils.shareErrorLogWithMail(context)
                }
            }
        }

        exportDeviceLogsButton.setOnClickListener {
            exportDeviceLogsButton.isEnabled = false
            exportDeviceLogsProgress.visibility = VISIBLE
            viewModel.exportDeviceLogs(
                onSuccess = { logs ->
                    exportDeviceLogsProgress.visibility = GONE
                    exportDeviceLogsButton.isEnabled = true
                    if (logs.isEmpty()) {
                        showSnackBar(rootView = requireView(), header = getString(R.string.export_device_logs_empty), showAsError = false)
                    } else {
                        shareDeviceLogs(logs)
                    }
                },
                onError = { error ->
                    exportDeviceLogsProgress.visibility = GONE
                    exportDeviceLogsButton.isEnabled = true
                    showSnackBar(rootView = requireView(), header = getString(R.string.export_device_logs_error), error, showAsError = true)
                }
            )
        }
    }

    private fun setupViews(view: View) {
        ohrLoggingButton = view.findViewById(R.id.enable_ohr_logging_button)
        ppiLoggingButton = view.findViewById(R.id.enable_ppi_logging_button)
        accLoggingButton = view.findViewById(R.id.enable_acc_logging_button)
        skinTemperatureLoggingButton = view.findViewById(R.id.enable_skin_temperature_logging_button)
        metLoggingButton = view.findViewById(R.id.enable_met_logging_button)
        caloriesLoggingButton = view.findViewById(R.id.enable_calories_logging_button)
        sleepLoggingButton = view.findViewById(R.id.enable_sleep_logging_button)
        fetchErrorLogButton = view.findViewById(R.id.fetch_errorlog_button)
        exportDeviceLogsButton = view.findViewById(R.id.export_device_logs_button)
        exportDeviceLogsProgress = view.findViewById(R.id.export_device_logs_progress)
    }

    private fun logConfigStatusChange(logConfig: LogConfig) {
        if (logConfig.ohrLogEnabled  == true) {
            ohrLoggingButton.text = getString(R.string.ohr_logging_disable)
        } else {
            ohrLoggingButton.text = getString(R.string.ohr_logging_enable)
        }

        if (logConfig.ppiLogEnabled == true) {
            ppiLoggingButton.text = getString(R.string.ppi_logging_disable)
        } else {
            ppiLoggingButton.text = getString(R.string.ppi_logging_enable)
        }

        if (logConfig.accelerationLogEnabled == true) {
            accLoggingButton.text = getString(R.string.acc_logging_disable)
        } else {
            accLoggingButton.text = getString(R.string.acc_logging_enable)
        }

        if (logConfig.skinTemperatureLogEnabled == true) {
            skinTemperatureLoggingButton.text = getString(R.string.skin_temp_logging_disable)
        } else {
            skinTemperatureLoggingButton.text = getString(R.string.skin_temp_logging_enable)
        }

        if (logConfig.metLogEnabled == true) {
            metLoggingButton.text = getString(R.string.met_logging_disable)
        } else {
            metLoggingButton.text = getString(R.string.met_logging_enable)
        }

        if (logConfig.caloriesLogEnabled == true) {
            caloriesLoggingButton.text = getString(R.string.calories_logging_disable)
        } else {
            caloriesLoggingButton.text = getString(R.string.calories_logging_enable)
        }

        if (logConfig.sleepLogEnabled == true) {
            sleepLoggingButton.text = getString(R.string.sleep_logging_disable)
        } else {
            sleepLoggingButton.text = getString(R.string.sleep_logging_enable)
        }
    }

    private fun shareDeviceLogs(logs: List<PolarDeviceLog>) {
        try {
            val logPairs = logs.map { it.path to it.data }
            val zipFile = ZipCompressionHelper.createDeviceLogsZipFile(requireContext(), logPairs, viewModel.uiIdentifier)
            val uri: Uri = FileProvider.getUriForFile(
                requireContext(),
                "com.polar.polarsensordatacollector.fileprovider",
                zipFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_device_logs_sharing_title))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.export_device_logs_sharing_title)))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create device logs ZIP: ${e.message}", e)
            showSnackBar(
                rootView = requireView(),
                header = getString(R.string.export_device_logs_error),
                e.message ?: "",
                showAsError = true
            )
        }
    }

    companion object {
        private const val TAG = "LoggingFragment"
    }
}