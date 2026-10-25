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

    /** Resultado: letra com tempos e/ou só o texto (quando o LRCLIB não tem os tempos). */
    class Result(val synced: List<LyricLine>?, val plain: String?)

    /** Bloqueante: use fora da thread principal. Null = o LRCLIB não conhece a música. */
    fun fetch(title: String, artist: String, durationMs: Long): Result? {
        if (title.isBlank()) return null
        var plainFallback: String? = null
        val seconds = (durationMs / 1000).toInt()

        // 1) busca exata (título + artista + duração)
        if (artist.isNotBlank() && seconds > 0) {
            val url = "$BASE/get".toHttpUrl().newBuilder()
                .addQueryParameter("track_name", title)
                .addQueryParameter("artist_name", artist)
                .addQueryParameter("duration", seconds.toString())
                .build()
            val body = get(url.toString(), allowNotFound = true)
            if (body != null) {
                val o = JSONObject(body)
                synced(o)?.let { return Result(it, null) }
                plainFallback = plainOf(o)
            }
        }

        // 2) busca ampla
        val b = "$BASE/search".toHttpUrl().newBuilder().addQueryParameter("track_name", title)
        if (artist.isNotBlank()) b.addQueryParameter("artist_name", artist)
        val body = get(b.build().toString(), allowNotFound = true)
            ?: return plainFallback?.let { Result(null, it) }
        val arr = JSONArray(body)
        var best: Pair<Int, List<LyricLine>>? = null
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (plainFallback == null) plainFallback = plainOf(o)
            val lines = synced(o) ?: continue
            // prefere a versão com duração mais parecida
            val diff = if (seconds > 0 && o.has("duration")) abs(o.optDouble("duration") - seconds).toInt() else 0
            if (best == null || diff < best.first) best = diff to lines
        }
        best?.let { return Result(it.second, null) }
        return plainFallback?.let { Result(null, it) }
    }

    private fun plainOf(o: JSONObject): String? =
        o.optString("plainLyrics", "").takeIf { it.isNotBlank() && it != "null" }

    private fun synced(o: JSONObject): List<LyricLine>? {
        val text = o.optString("syncedLyrics", "")
        if (text.isBlank() || text == "null") return null
        return LyricsStore.parse(text).takeIf { it.isNotEmpty() }
    }

    /** Tenta até 3 vezes quando o serviço está sobrecarregado (HTTP 5xx ou 429). */
    private fun get(url: String, allowNotFound: Boolean): String? {
        val waits = longArrayOf(0L, 2000L, 5000L)
        var lastCode = 0
        for (wait in waits) {
            if (wait > 0) Thread.sleep(wait)
            val req = Request.Builder().url(url).header("User-Agent", AGENT).build()
            try {
                Http.client.newCall(req).execute().use { resp ->
                    lastCode = resp.code
                    if (resp.code == 404 && allowNotFound) return null
                    if (resp.isSuccessful) return resp.body?.string()
                    if (resp.code < 500 && resp.code != 429) {
                        throw IOException("Falha ao consultar o LRCLIB (HTTP ${resp.code}).")
                    }
                }
            } catch (e: java.net.SocketTimeoutException) {
                lastCode = 0
            }
        }
        throw IOException(
            "O LRCLIB está fora do ar ou sobrecarregado agora" +
                (if (lastCode > 0) " (HTTP $lastCode)" else "") +
                ". Tente de novo em alguns minutos."
        )
    }
}
