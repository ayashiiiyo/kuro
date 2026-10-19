package com.ayashii.kuro.data.remote

import android.content.Context
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.File
import java.util.concurrent.TimeUnit

class KuroDownloader private constructor(context: Context? = null) : Downloader() {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
        .apply {
            context?.let { ctx ->
                val cacheDir = File(ctx.cacheDir, "http_cache")
                cache(Cache(cacheDir, 30L * 1024 * 1024))
            }
        }
        .readTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val reqBuilder = okhttp3.Request.Builder().url(url)

        headers.forEach { (name, values) ->
            values.forEach { value ->
                reqBuilder.addHeader(name, value)
            }
        }

        if (headers["User-Agent"] == null && headers["user-agent"] == null) {
            reqBuilder.header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
            )
        }

        // Inject YouTube auth cookies & SAPISID header if user is logged in
        try {
            val auth = com.ayashii.kuro.data.auth.AuthManager.getInstance()
            val cookie = auth.getCookie()
            if (!cookie.isNullOrBlank() && url.contains("youtube.com") && !url.contains("googlevideo.com")) {
                if (headers["Cookie"] == null && headers["cookie"] == null) {
                    reqBuilder.header("Cookie", cookie)
                }
                if (url.contains("/youtubei/")) {
                    val origin = if (url.contains("music.youtube.com")) "https://music.youtube.com" else "https://www.youtube.com"
                    auth.getSapisidHash(origin)?.let { sapisidHash ->
                        if (headers["Authorization"] == null && headers["authorization"] == null) {
                            reqBuilder.header("Authorization", sapisidHash)
                        }
                        if (headers["x-origin"] == null && headers["X-Origin"] == null) {
                            reqBuilder.header("x-origin", origin)
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        val requestBody = if (httpMethod == "POST" || httpMethod == "PUT") {
            (dataToSend ?: ByteArray(0)).toRequestBody(null)
        } else null

        reqBuilder.method(httpMethod, requestBody)

        val response = client.newCall(reqBuilder.build()).execute()
        val responseBody = response.body?.string() ?: ""
        val responseHeaders = response.headers.toMultimap()

        return Response(
            response.code,
            response.message,
            responseHeaders,
            responseBody,
            response.request.url.toString()
        )
    }

    companion object {
        @Volatile
        private var instance: KuroDownloader? = null

        fun init(context: Context? = null): KuroDownloader {
            return instance ?: synchronized(this) {
                instance ?: KuroDownloader(context).also { instance = it }
            }
        }

        fun getInstance(): KuroDownloader = init()
    }
}
