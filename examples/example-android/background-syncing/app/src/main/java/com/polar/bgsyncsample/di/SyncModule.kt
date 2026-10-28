// Copyright © 2026 Polar Electro Oy. All rights reserved.

package com.polar.bgsyncsample.di

import android.content.Context
import com.polar.polarbgsync.api.BackgroundSyncApi
import com.polar.polarbgsync.api.LocalDataApi
import com.polar.polarbgsync.api.PolarBackgroundSync
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    @Singleton
    fun provideBackgroundSyncApi(@ApplicationContext context: Context): BackgroundSyncApi {
        return PolarBackgroundSync.create(context)
    }

    @Provides
    @Singleton
    fun provideLocalDataApi(@ApplicationContext context: Context): LocalDataApi {
        return PolarBackgroundSync.localDataApi(context)
    }
}
