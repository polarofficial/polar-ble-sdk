package com.polar.polarsensordatacollector.di

import android.content.Context
import android.util.Log
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.impl.BDBleApiImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object PolarBleSdkModule {

    @Provides
    @Singleton
    fun providePolarBleSdkApi(@ApplicationContext appContext: Context): BDBleApiImpl {

        val polarSdkApi = PolarBleApiDefaultImpl.defaultImplementation(
            context = appContext,
            features = setOf(
                PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE
            )
        )

        polarSdkApi.setApiLogger { s: String -> Log.d("API_LOGGER", s) }
        return polarSdkApi
    }
}