package com.ayashii.kuro

import android.app.Application
import com.ayashii.kuro.data.remote.KuroDownloader
import org.schabi.newpipe.extractor.NewPipe

class KuroApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val downloader = KuroDownloader.init()
        NewPipe.init(downloader)
    }
}
