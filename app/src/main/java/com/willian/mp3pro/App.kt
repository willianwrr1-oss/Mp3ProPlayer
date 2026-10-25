package com.willian.mp3pro

import android.app.Application
import com.willian.mp3pro.audio.FxStore
import com.willian.mp3pro.data.Offline

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        FxStore.init(this)
        Offline.init(this)
    }
}
