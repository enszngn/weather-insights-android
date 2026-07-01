package com.weatherinsights.di

import com.weatherinsights.data.datasource.WeatherLocalSource
import com.weatherinsights.data.datasource.DataStoreWeatherLocalSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LocalModule {

    @Binds
    @Singleton
    abstract fun bindWeatherLocalSource(
        impl: DataStoreWeatherLocalSource
    ): WeatherLocalSource
}
