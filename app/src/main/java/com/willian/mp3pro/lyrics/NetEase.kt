package com.willian.mp3pro.lyrics

import com.willian.mp3pro.data.Http
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import kotlin.math.abs

/**
 * Letras sincronizadas do NetEase Cloud Music (music.163.com). A API não é oficial,
 * então o app trata qualquer falha como "não achei" e segue para a próxima fonte.
 */
object NetEase {
    private const val UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    private val credit = Regex(
        """^\s*(作词|作曲|编曲|制作人?|词|曲|lyrics?|composer|composed|producer|arranger|written)\s*(by)?\s*[:：]""",
        RegexOption.IGNORE_CASE
    )

    /** Letra com tempos, ou null. Lança IOException se o serviço não respondeu. */
    fun fetch(title: String, artist: String, durationMs: Long): List<LyricLine>? {
        val query = (title + " " + artist).trim()
        if (query.isBlank()) return null

        val search = "https://music.163.com/api/search/get/web".toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .addQueryParameter("type", "1")
            .addQueryParameter("offset", "0")
            .addQueryParameter("limit", "5")
            .addQueryParameter("total", "true")
            .build()
        val songs = JSONObject(get(search.toString()) ?: return null)
            .optJSONObject("result")?.optJSONArray("songs") ?: return null

        // escolhe a faixa de duração mais parecida (aceita até 8 s de diferença)
        var bestId = 0L
        var bestDiff = Long.MAX_VALUE
        for (i in 0 until songs.length()) {
            val o = songs.getJSONObject(i)
            val dur = o.optLong("duration", o.optLong("dt", 0L))
            val diff = if (durationMs > 0 && dur > 0) abs(dur - durationMs) else 0L
            if (diff < bestDiff) {
                bestDiff = diff
                bestId = o.optLong("id", 0L)
            }
        }
        if (bestId == 0L || (durationMs > 0 && bestDiff > 8000)) return null

        val lyric = "https://music.163.com/api/song/lyric".toHttpUrl().newBuilder()
            .addQueryParameter("id", bestId.toString())
            .addQueryParameter("lv", "1")
            .addQueryParameter("kv", "1")
            .addQueryParameter("tv", "-1")
            .build()
        val text = JSONObject(get(lyric.toString()) ?: return null)
            .optJSONObject("lrc")?.optString("lyric", "") ?: return null
        return LyricsStore.parse(text).filterNot { credit.containsMatchIn(it.text) }
            .takeIf { it.size >= 4 }
    }

    private fun get(url: String): String? {
        val req = Request.Builder().url(url)
            .header("User-Agent", UA)
            .header("Referer", "https://music.163.com")
            .build()
        Http.client.newCall(req).execute().use { resp ->
            if (resp.code == 404) return null
            if (!resp.isSuccessful) throw IOException("NetEase (HTTP ${resp.code})")
            return resp.body?.string()
        }
    }
}
