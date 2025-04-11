package com.polar.polarsensordatacollector.di

import android.content.Context
import com.polar.polarsensordatacollector.DataCollector
import com.polar.polarsensordatacollector.ui.utils.FileUtils
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@InstallIn(SingletonComponent::class)
@Module
object PolarSensorDataCollectorModule {

    @Provides
    fun provideDataCollector(@ApplicationContext appContext: Context): DataCollector {
        return DataCollector(appContext)
    }

    @Provides
    fun provideFileUtils(@ApplicationContext appContext: Context): FileUtils {
        return FileUtils(appContext)
    }
}