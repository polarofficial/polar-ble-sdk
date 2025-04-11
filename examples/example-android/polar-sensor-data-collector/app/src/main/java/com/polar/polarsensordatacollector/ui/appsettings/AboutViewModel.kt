package com.polar.polarsensordatacollector.ui.appsettings

import androidx.lifecycle.ViewModel
import com.polar.polarsensordatacollector.BuildConfig
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
internal class AboutViewModel @Inject constructor(
    private val polarDeviceStreamingRepository: PolarDeviceRepository
) : ViewModel() {

    val uiPolarBleSdkVersion: StateFlow<String> = polarDeviceStreamingRepository.polarBleSdkVersion

    private val _appVersionName: MutableStateFlow<String> = MutableStateFlow(BuildConfig.VERSION_NAME)
    val uiAppVersion: StateFlow<String> = _appVersionName.asStateFlow()

}