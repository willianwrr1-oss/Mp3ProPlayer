package com.willian.mp3pro.lyrics

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.security.MessageDigest

/** Uma linha da letra e o momento (em ms) em que ela começa. */
data class LyricLine(val timeMs: Long, val text: String)

/**
 * Letras salvas no celular, uma por música, no formato LRC ("[mm:ss.cc]texto").
 * Ficam na pasta privada do app e valem para a música mesmo que ela venha do
 * OneDrive, das baixadas ou do Android Auto (o identificador é normalizado).
 */
object LyricsStore {
    private lateinit var dir: File
    private var started = false

    private val _version = MutableStateFlow(0)

    /** Muda a cada gravação ou exclusão: telas que mostram letra se atualizam por ele. */
    val version: StateFlow<Int> = _version.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (started) return
        started = true
        dir = File(context.applicationContext.filesDir, "lyrics").apply { mkdirs() }
    }

    /** Mesma música, mesmo identificador, venha de onde vier. */
    fun normalize(mediaId: String): String = when {
        mediaId.startsWith("dl:") -> mediaId.removePrefix("dl:")
        mediaId.startsWith("od:") -> mediaId.removePrefix("od:")
        mediaId.startsWith("lt:") -> "local:" + mediaId.removePrefix("lt:")
        else -> mediaId
    }

    private fun fileFor(mediaId: String): File = File(dir, sha1(normalize(mediaId)) + ".lrc")

    fun has(mediaId: String): Boolean = mediaId.isNotEmpty() && fileFor(mediaId).exists()

    fun load(mediaId: String): List<LyricLine>? {
        if (mediaId.isEmpty()) return null
        val f = fileFor(mediaId)
        if (!f.exists()) return null
        return try {
            parse(f.readText()).takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    fun save(mediaId: String, lines: List<LyricLine>) {
        if (lines.isEmpty()) return
        fileFor(mediaId).writeText(format(lines))
        _version.value = _version.value + 1
    }

    fun delete(mediaId: String) {
        fileFor(mediaId).delete()
        _version.value = _version.value + 1
    }

    private val lrcLine = Regex("""^\[(\d+):(\d+)(?:[.:](\d+))?](.*)$""")

    fun parse(text: String): List<LyricLine> =
        text.lineSequence().mapNotNull { raw ->
            val m = lrcLine.matchEntire(raw.trim()) ?: return@mapNotNull null
            val min = m.groupValues[1].toLong()
            val sec = m.groupValues[2].toLong()
            val frac = m.groupValues[3]
            val ms = when (frac.length) {
                0 -> 0L
                1 -> frac.toLong() * 100
                2 -> frac.toLong() * 10
                else -> frac.take(3).toLong()
            }
            val line = m.groupValues[4].trim()
            if (line.isEmpty()) null else LyricLine((min * 60 + sec) * 1000 + ms, line)
        }.sortedBy { it.timeMs }.toList()

    fun format(lines: List<LyricLine>): String =
        lines.joinToString("\n") {
            val total = it.timeMs.coerceAtLeast(0)
            val min = total / 60000
            val sec = (total / 1000) % 60
            val cs = (total % 1000) / 10
            "[%02d:%02d.%02d]%s".format(min, sec, cs, it.text.replace('\n', ' '))
        }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
