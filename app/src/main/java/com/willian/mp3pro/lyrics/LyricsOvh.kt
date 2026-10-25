package com.willian.mp3pro.lyrics

import com.willian.mp3pro.data.Http
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/** Letra só em texto (sem tempos) do lyrics.ovh: último recurso. Precisa de artista e título. */
object LyricsOvh {
    fun fetch(title: String, artist: String): String? {
        if (title.isBlank() || artist.isBlank()) return null
        val url = "https://api.lyrics.ovh/v1".toHttpUrl().newBuilder()
            .addPathSegment(artist).addPathSegment(title).build()
        val req = Request.Builder().url(url).build()
        Http.client.newCall(req).execute().use { resp ->
            if (resp.code == 404) return null
            if (!resp.isSuccessful) throw IOException("lyrics.ovh (HTTP ${resp.code})")
            val text = JSONObject(resp.body?.string() ?: return null).optString("lyrics", "")
            return text.replace("\r", "").trim().takeIf { it.length > 40 }
        }
    }
}
