package com.willian.mp3pro.lyrics

import java.io.IOException

/**
 * Procura a letra em várias fontes, da melhor para a pior:
 *  1. LRCLIB, com tempos;
 *  2. NetEase, com tempos;
 *  3. texto sem tempos (do próprio LRCLIB ou do lyrics.ovh).
 * Se uma fonte estiver fora do ar, segue para a próxima.
 */
object LyricsFinder {

    /** Tira "(Official Video)", "[Remastered]" e parecidos, que atrapalham a busca. */
    private fun clean(title: String): String =
        title.replace(Regex("""\s*[\(\[][^\)\]]*[\)\]]"""), "").trim().ifEmpty { title }

    fun find(title: String, artist: String, durationMs: Long): List<LyricLine>? {
        var plain: String? = null
        var answered = 0 // fontes que responderam (mesmo sem achar)
        val errors = ArrayList<String>()

        fun <T> source(name: String, block: () -> T?): T? =
            try {
                block().also { answered++ }
            } catch (e: IOException) {
                errors += name
                null
            } catch (e: Exception) {
                errors += name
                null
            }

        val t2 = clean(title)

        source("LRCLIB") { LrcLib.fetch(title, artist, durationMs) }?.let { r ->
            r.synced?.let { return it }
            if (plain == null) plain = r.plain
        }
        if (t2 != title) {
            source("LRCLIB") { LrcLib.fetch(t2, artist, durationMs) }?.let { r ->
                r.synced?.let { return it }
                if (plain == null) plain = r.plain
            }
        }

        source("NetEase") { NetEase.fetch(t2, artist, durationMs) }?.let { return it }

        if (plain == null) plain = source("lyrics.ovh") { LyricsOvh.fetch(t2, artist) }
        plain?.let { text -> LyricsStore.plain(text).takeIf { it.isNotEmpty() }?.let { return it } }

        if (answered == 0) {
            throw IOException(
                "Os serviços de letras estão fora do ar agora. Tente de novo em alguns minutos."
            )
        }
        return null
    }
}
