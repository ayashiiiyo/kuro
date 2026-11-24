package com.ayashii.kuro.data.remote

import com.ayashii.kuro.data.auth.AuthManager
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object HttpClientProvider {
    val baseClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val client: OkHttpClient by lazy {
        baseClient.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                val host = request.url.host.lowercase()
                if (!request.header("Cookie").isNullOrBlank() &&
                    (host == "youtube.com" || host.endsWith(".youtube.com") || host == "google.com" || host.endsWith(".google.com"))
                ) {
                    val setCookies = response.headers("Set-Cookie")
                    if (setCookies.isNotEmpty()) {
                        runCatching { AuthManager.getInstance().mergeResponseCookies(setCookies) }
                    }
                }
                response
            }
            .build()
    }
}
