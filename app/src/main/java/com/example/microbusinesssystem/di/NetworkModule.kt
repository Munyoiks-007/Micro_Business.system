package com.example.microbusinesssystem.di

import com.example.microbusinesssystem.data.remote.DarajaApiService
import com.example.microbusinesssystem.data.remote.DarajaConfiguration
import com.example.microbusinesssystem.data.remote.DarajaEnvironment
import com.example.microbusinesssystem.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideDarajaConfiguration(): DarajaConfiguration = DarajaConfiguration(
        consumerKey = BuildConfig.DARAJA_SANDBOX_CONSUMER_KEY,
        consumerSecret = BuildConfig.DARAJA_SANDBOX_CONSUMER_SECRET,
        shortCode = BuildConfig.DARAJA_SANDBOX_SHORT_CODE,
        passKey = BuildConfig.DARAJA_SANDBOX_PASS_KEY,
        callbackUrl = BuildConfig.DARAJA_SANDBOX_CALLBACK_URL
    )

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(DarajaEnvironment.SANDBOX_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideDarajaApiService(retrofit: Retrofit): DarajaApiService {
        return retrofit.create(DarajaApiService::class.java)
    }
}
