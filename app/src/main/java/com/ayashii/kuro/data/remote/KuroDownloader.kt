package com.ayashii.kuro.data.remote

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.util.concurrent.TimeUnit

class KuroDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .build()
) : Downloader() {

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

        fun init(): KuroDownloader {
            return instance ?: synchronized(this) {
                instance ?: KuroDownloader().also { instance = it }
            }
        }

        fun getInstance(): KuroDownloader = init()
    }
}
