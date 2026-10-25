package com.willian.mp3pro.data

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Cliente HTTP único, usado na listagem, nos downloads e no streaming. */
object Http {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
