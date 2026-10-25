package com.willian.mp3pro.data

import android.content.Context
import android.content.SharedPreferences
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
 *
 * Guarda também o total já baixado ao longo do tempo (histórico), que não diminui
 * quando uma música é removida; o total atual é a soma do que está na pasta agora.
 */
object Offline {
    private const val KEY_LIFETIME = "lifetime_bytes"

    private lateinit var dir: File
    private lateinit var indexFile: File
    private lateinit var stats: SharedPreferences
    private var started = false

    private val _downloaded = MutableStateFlow<Map<String, DownloadedTrack>>(emptyMap())
    val downloaded: StateFlow<Map<String, DownloadedTrack>> = _downloaded.asStateFlow()

    /** Progresso (0 a 100) dos downloads em andamento, por caminho. */
    private val _progress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val progress: StateFlow<Map<String, Int>> = _progress.asStateFlow()

    /** Total de bytes já baixados desde sempre (histórico), mesmo das músicas removidas depois. */
    private val _lifetime = MutableStateFlow(0L)
    val lifetimeBytes: StateFlow<Long> = _lifetime.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        dir = File(app.filesDir, "music").apply { mkdirs() }
        indexFile = File(dir, "index.json")
        stats = app.getSharedPreferences("offline_stats", Context.MODE_PRIVATE)
        _downloaded.value = readIndex()

        // Quem já tinha músicas baixadas antes desta versão começa com o que existe hoje.
        val atual = _downloaded.value.values.sumOf { it.size }
        val salvo = stats.getLong(KEY_LIFETIME, -1L)
        val inicio = if (salvo < 0) atual else maxOf(salvo, atual)
        _lifetime.value = inicio
        if (salvo != inicio) stats.edit().putLong(KEY_LIFETIME, inicio).apply()
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

        val total = _lifetime.value + size
        _lifetime.value = total
        stats.edit().putLong(KEY_LIFETIME, total).apply()
    }

    fun remove(path: String) = removeAll(listOf(path))

    /** Apaga várias músicas de uma vez. O histórico de downloads não é alterado. */
    @Synchronized
    fun removeAll(paths: Collection<String>) {
        val atual = _downloaded.value
        val novo = atual.toMutableMap()
        for (p in paths) {
            val t = atual[p] ?: continue
            File(dir, t.fileName).delete()
            novo.remove(p)
        }
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
