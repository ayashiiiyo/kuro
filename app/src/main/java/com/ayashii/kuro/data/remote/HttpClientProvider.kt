package com.ayashii.kuro.data.remote

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object HttpClientProvider {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
