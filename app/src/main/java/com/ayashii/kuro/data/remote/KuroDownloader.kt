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

    private val client: OkHttpClient = HttpClientProvider.baseClient.newBuilder()
        .apply {
            context?.let { ctx ->
                val cacheDir = File(ctx.cacheDir, "http_cache")
                cache(Cache(cacheDir, 30L * 1024 * 1024))
            }
        }
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

        /*
         * Only attach user cookies to explicit playlist browse calls (list=LL),
         * never to /youtubei/v1/player stream extraction where cookies without poToken trigger HTTP 403 / Sabr throttling.
         */
        try {
            val isPlayerOrStreamCall = url.contains("/youtubei/v1/player") || url.contains("googlevideo.com")
            if (!isPlayerOrStreamCall && url.contains("list=LL")) {
                val auth = com.ayashii.kuro.data.auth.AuthManager.getInstance()
                val cookie = auth.getCookie()
                if (!cookie.isNullOrBlank()) {
                    if (headers["Cookie"] == null && headers["cookie"] == null) {
                        reqBuilder.header("Cookie", cookie)
                    }
                }
            }
        } catch (_: Exception) {}

        val requestBody = if (httpMethod == "POST" || httpMethod == "PUT") {
            (dataToSend ?: ByteArray(0)).toRequestBody(null)
        } else null

        reqBuilder.method(httpMethod, requestBody)

        return client.newCall(reqBuilder.build()).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            val responseHeaders = response.headers.toMultimap()
            Response(
                response.code,
                response.message,
                responseHeaders,
                responseBody,
                response.request.url.toString()
            )
        }
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
