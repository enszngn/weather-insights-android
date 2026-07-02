package com.weatherinsights.daily.forecast.live.radar.di

import com.weatherinsights.daily.forecast.live.radar.data.datasource.WeatherLocalSource
import com.weatherinsights.daily.forecast.live.radar.data.datasource.DataStoreWeatherLocalSource
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
