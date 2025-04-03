package com.polar.polarsensordatacollector.di

import com.polar.polarsensordatacollector.crypto.SecretKeyManager
import com.polar.polarsensordatacollector.crypto.SecretKeyManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent


@InstallIn(SingletonComponent::class)
@Module
abstract class SecretKeyManagerModule {
    @Binds
    abstract fun secretKeyManager(secretKeyManagerImpl: SecretKeyManagerImpl): SecretKeyManager
}