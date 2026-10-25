package com.willian.mp3pro.data

import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/**
 * Lê uma pasta do OneDrive compartilhada com "qualquer pessoa com o link",
 * sem login. Tenta mais de um caminho de acesso e guarda o que funcionar.
 */
class OneDriveClient(private val shareUrl: String) {

    private enum class Kind { ONEDRIVE_API, GRAPH }

    private data class Candidate(val kind: Kind, val base: String, val shareId: String, val label: String)

    private var working: Candidate? = null
    private var resolvedUrl: String? = null
    private var resolved = false

    companion object {
        private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus")

        fun encodeShare(url: String): String {
            val b64 = Base64.encodeToString(
                url.toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE
            )
            return "u!$b64"
        }

        private fun encodePath(path: String): String =
            path.split("/").filter { it.isNotEmpty() }.joinToString("/") { Uri.encode(it) }
    }

    /** Lista pastas e músicas em [path] ("" = raiz da pasta compartilhada). */
    suspend fun list(path: String): List<RemoteEntry> = withContext(Dispatchers.IO) {
        val diag = mutableListOf<String>()

        val known = working
        if (known != null) {
            try {
                return@withContext fetch(known, path)
            } catch (e: Exception) {
                diag += "${known.label}: ${e.message}"
            }
        }

        for (c in buildCandidates(diag)) {
            if (c == known) continue
            try {
                val result = fetch(c, path)
                working = c
                return@withContext result
            } catch (e: Exception) {
                diag += "${c.label}: ${e.message}"
            }
        }
        throw OneDriveException(diag)
    }

    private fun buildCandidates(diag: MutableList<String>): List<Candidate> {
        if (!resolved) {
            resolved = true
            resolvedUrl = try {
                val req = Request.Builder()
                    .url(shareUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) Mp3ProPlayer")
                    .build()
                Http.client.newCall(req).execute().use { it.request.url.toString() }
            } catch (e: Exception) {
                diag += "Resolver redirecionamento do link: ${e.message}"
                null
            }
        }
        val ids = mutableListOf("link original" to encodeShare(shareUrl))
        resolvedUrl?.takeIf { it != shareUrl }?.let { ids += "link expandido" to encodeShare(it) }

        val list = mutableListOf<Candidate>()
        for ((name, id) in ids) {
            list += Candidate(Kind.ONEDRIVE_API, "https://api.onedrive.com/v1.0", id, "api.onedrive.com [$name]")
        }
        for ((name, id) in ids) {
            list += Candidate(Kind.GRAPH, "https://graph.microsoft.com/v1.0", id, "graph.microsoft.com [$name]")
        }
        return list
    }

    private fun listUrl(c: Candidate, path: String): String {
        val enc = encodePath(path)
        return when (c.kind) {
            Kind.ONEDRIVE_API ->
                if (enc.isEmpty()) "${c.base}/shares/${c.shareId}/root/children?top=200"
                else "${c.base}/shares/${c.shareId}/root:/$enc:/children?top=200"
            Kind.GRAPH ->
                if (enc.isEmpty()) "${c.base}/shares/${c.shareId}/driveItem/children?\$top=200"
                else "${c.base}/shares/${c.shareId}/driveItem:/$enc:/children?\$top=200"
        }
    }

    private fun contentUrl(c: Candidate, path: String): String {
        val enc = encodePath(path)
        return when (c.kind) {
            Kind.ONEDRIVE_API -> "${c.base}/shares/${c.shareId}/root:/$enc:/content"
            Kind.GRAPH -> "${c.base}/shares/${c.shareId}/driveItem:/$enc:/content"
        }
    }

    private fun fetch(c: Candidate, path: String): List<RemoteEntry> {
        val out = mutableListOf<RemoteEntry>()
        var url: String? = listUrl(c, path)
        var pages = 0

        while (pages < 50) {
            val current: String = url ?: break
            pages++
            val req = Request.Builder().url(current).header("Accept", "application/json").build()
            Http.client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}${errorHint(text)}")
                val json = JSONObject(text)
                val arr = json.optJSONArray("value")
                    ?: throw IOException("resposta sem a lista de itens")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val name = o.optString("name")
                    if (name.isEmpty()) continue
                    val isFolder = o.has("folder")
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (!isFolder && ext !in AUDIO_EXT) continue
                    val entryPath = if (path.isEmpty()) name else "$path/$name"
                    out += RemoteEntry(
                        name = name,
                        path = entryPath,
                        isFolder = isFolder,
                        size = o.optLong("size", 0L),
                        contentUrl = if (isFolder) null else contentUrl(c, entryPath)
                    )
                }
                url = json.optString("@odata.nextLink").takeIf { it.isNotEmpty() }
            }
        }

        return out.sortedWith(
            compareByDescending<RemoteEntry> { it.isFolder }.thenBy { it.name.lowercase() }
        )
    }

    private fun errorHint(body: String): String = try {
        val err = JSONObject(body).optJSONObject("error")
        val code = err?.optString("code").orEmpty()
        val msg = err?.optString("message").orEmpty().take(120)
        if (code.isNotEmpty() || msg.isNotEmpty()) " ($code: $msg)" else ""
    } catch (e: Exception) {
        ""
    }
}
