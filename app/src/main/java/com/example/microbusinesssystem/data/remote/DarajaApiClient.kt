package com.example.microbusinesssystem.data.remote

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object DarajaApiClient {

    private const val BASE_URL = DarajaEnvironment.SANDBOX_BASE_URL

    val isSandbox: Boolean
        get() = BASE_URL == DarajaEnvironment.SANDBOX_BASE_URL

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val apiService: DarajaApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DarajaApiService::class.java)
    }
}