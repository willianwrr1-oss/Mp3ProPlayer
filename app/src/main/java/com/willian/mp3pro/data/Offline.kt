package com.willian.mp3pro.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Músicas baixadas para ouvir sem internet. Os arquivos ficam na pasta privada
 * do app, com um índice em JSON para listar sem precisar de rede.
 */
object Offline {
    private lateinit var dir: File
    private lateinit var indexFile: File
    private var started = false

    private val _downloaded = MutableStateFlow<Map<String, DownloadedTrack>>(emptyMap())
    val downloaded: StateFlow<Map<String, DownloadedTrack>> = _downloaded.asStateFlow()

    /** Progresso (0 a 100) dos downloads em andamento, por caminho. */
    private val _progress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val progress: StateFlow<Map<String, Int>> = _progress.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (started) return
        started = true
        dir = File(context.applicationContext.filesDir, "music").apply { mkdirs() }
        indexFile = File(dir, "index.json")
        _downloaded.value = readIndex()
    }

    fun targetFile(path: String, extension: String): File = File(dir, "${sha1(path)}.$extension")

    fun tempFile(path: String): File = File(dir, "${sha1(path)}.part")

    fun fileOf(path: String): File? {
        val t = _downloaded.value[path] ?: return null
        val f = File(dir, t.fileName)
        return if (f.exists() && f.length() > 0) f else null
    }

    @Synchronized
    fun register(path: String, name: String, size: Long, file: File) {
        val novo = _downloaded.value.toMutableMap()
        novo[path] = DownloadedTrack(path, name, size, file.name)
        _downloaded.value = novo
        writeIndex(novo)
    }

    @Synchronized
    fun remove(path: String) {
        val atual = _downloaded.value
        val t = atual[path] ?: return
        File(dir, t.fileName).delete()
        val novo = atual.toMutableMap().apply { remove(path) }
        _downloaded.value = novo
        writeIndex(novo)
    }

    fun setProgress(path: String, percent: Int?) {
        val novo = _progress.value.toMutableMap()
        if (percent == null) novo.remove(path) else novo[path] = percent
        _progress.value = novo
    }

    fun totalBytes(): Long = _downloaded.value.values.sumOf { it.size }

    private fun readIndex(): Map<String, DownloadedTrack> {
        if (!indexFile.exists()) return emptyMap()
        return try {
            val arr = JSONArray(indexFile.readText())
            val map = LinkedHashMap<String, DownloadedTrack>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val t = DownloadedTrack(
                    o.getString("path"), o.getString("name"),
                    o.optLong("size"), o.getString("file")
                )
                if (File(dir, t.fileName).exists()) map[t.path] = t
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun writeIndex(map: Map<String, DownloadedTrack>) {
        val arr = JSONArray()
        map.values.forEach {
            arr.put(
                JSONObject()
                    .put("path", it.path).put("name", it.name)
                    .put("size", it.size).put("file", it.fileName)
            )
        }
        try {
            indexFile.writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
