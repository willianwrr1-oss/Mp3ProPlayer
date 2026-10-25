package com.willian.mp3pro.lyrics

import com.willian.mp3pro.data.Http
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlin.math.abs

/**
 * Busca letras sincronizadas no LRCLIB (lrclib.net), serviço aberto e gratuito, sem chave.
 * Só o título, o artista e a duração da música são enviados.
 */
object LrcLib {
    private const val BASE = "https://lrclib.net/api"
    private const val AGENT = "MP3ProPlayer/1.5 (willian.wrr1@gmail.com)"

    /** Letra com tempos, ou null se o LRCLIB não tiver. Bloqueante: use fora da thread principal. */
    fun fetch(title: String, artist: String, durationMs: Long): List<LyricLine>? {
        if (title.isBlank()) return null
        val seconds = (durationMs / 1000).toInt()

        // 1) busca exata (título + artista + duração)
        if (artist.isNotBlank() && seconds > 0) {
            val url = "$BASE/get".toHttpUrl().newBuilder()
                .addQueryParameter("track_name", title)
                .addQueryParameter("artist_name", artist)
                .addQueryParameter("duration", seconds.toString())
                .build()
            val body = get(url.toString(), allowNotFound = true)
            if (body != null) synced(JSONObject(body))?.let { return it }
        }

        // 2) busca ampla
        val b = "$BASE/search".toHttpUrl().newBuilder().addQueryParameter("track_name", title)
        if (artist.isNotBlank()) b.addQueryParameter("artist_name", artist)
        val body = get(b.build().toString(), allowNotFound = true) ?: return null
        val arr = JSONArray(body)
        var best: Pair<Int, List<LyricLine>>? = null
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val lines = synced(o) ?: continue
            // prefere a versão com duração mais parecida
            val diff = if (seconds > 0 && o.has("duration")) abs(o.optDouble("duration") - seconds).toInt() else 0
            if (best == null || diff < best.first) best = diff to lines
        }
        return best?.second
    }

    private fun synced(o: JSONObject): List<LyricLine>? {
        val text = o.optString("syncedLyrics", "")
        if (text.isBlank() || text == "null") return null
        return LyricsStore.parse(text).takeIf { it.isNotEmpty() }
    }

    private fun get(url: String, allowNotFound: Boolean): String? {
        val req = Request.Builder().url(url).header("User-Agent", AGENT).build()
        Http.client.newCall(req).execute().use { resp ->
            if (resp.code == 404 && allowNotFound) return null
            if (!resp.isSuccessful) throw IOException("Falha ao consultar o LRCLIB (HTTP ${resp.code}).")
            return resp.body?.string()
        }
    }
}
