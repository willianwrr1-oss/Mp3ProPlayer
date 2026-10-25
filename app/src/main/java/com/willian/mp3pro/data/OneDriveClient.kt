package com.willian.mp3pro.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Lê uma pasta do OneDrive compartilhada com "qualquer pessoa com o link", sem login.
 *
 * Links novos do OneDrive pessoal (1drv.ms/f/c/...) são lidos como o navegador faz para
 * visitantes anônimos: um token de visitante + o serviço my.microsoftpersonalcontent.com.
 * O caminho antigo (api.onedrive.com) fica como reserva para links no formato antigo.
 *
 * As músicas ganham um endereço interno "onedrive://file?p=<caminho>", que só é
 * convertido no link real de download na hora de tocar ou baixar (esses links expiram).
 */
class OneDriveClient private constructor(val shareUrl: String) {

    private interface Backend {
        val label: String
        fun list(path: String): List<RemoteEntry>
        fun downloadUrl(path: String): String
    }

    @Volatile private var working: Backend? = null
    private var candidates: List<Backend>? = null
    private val notes = mutableListOf<String>()

    private val urlCache = ConcurrentHashMap<String, Pair<String, Long>>()

    private var token: String? = null
    private var tokenAt = 0L

    companion object {
        const val SCHEME = "onedrive"

        private val AUDIO_EXT = AudioFormats.extensions
        private const val BADGER_APP_ID = "1141147648"
        private const val BADGER_APP_UUID = "5cbed6ac-a083-4e14-b191-b4ba07653de2"
        private const val BADGER_URL = "https://api-badgerp.svc.ms/v1.0/token"
        private const val PERSONAL_API = "https://my.microsoftpersonalcontent.com/_api/v2.0/shares"
        private const val ONEDRIVE_API = "https://api.onedrive.com/v1.0/shares"
        private const val UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0 Mobile Safari/537.36"
        private const val URL_TTL_MS = 20 * 60 * 1000L

        @Volatile private var instance: OneDriveClient? = null

        /** Cliente único do processo; é recriado se o link da pasta mudar. */
        fun get(context: Context): OneDriveClient {
            val url = AppPrefs.shareUrl(context)
            val atual = instance
            if (atual != null && atual.shareUrl == url) return atual
            return synchronized(this) {
                val de_novo = instance
                if (de_novo != null && de_novo.shareUrl == url) de_novo
                else OneDriveClient(url).also { instance = it }
            }
        }

        fun pseudoUrl(path: String): String =
            Uri.Builder().scheme(SCHEME).authority("file").appendQueryParameter("p", path).build().toString()

        private fun b64url(s: String): String = Base64.encodeToString(
            s.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE
        )

        private fun encodePath(path: String): String =
            path.split("/").filter { it.isNotEmpty() }.joinToString("/") { Uri.encode(it) }

        private fun errorHint(body: String): String = try {
            val err = JSONObject(body).optJSONObject("error")
            val code = err?.optString("code").orEmpty()
            val msg = err?.optString("message").orEmpty().take(120)
            if (code.isNotEmpty() || msg.isNotEmpty()) " ($code: $msg)" else ""
        } catch (e: Exception) {
            ""
        }
    }

    // ------------------------------------------------------------------ API pública

    /** Lista pastas e músicas em [path] ("" = raiz da pasta compartilhada). */
    suspend fun list(path: String): List<RemoteEntry> = withContext(Dispatchers.IO) { listBlocking(path) }

    /** Link real e temporário de download da música em [path]. Bloqueante: use fora da thread principal. */
    fun downloadUrl(path: String): String {
        val b = working ?: run {
            listBlocking(path.substringBeforeLast('/', ""))
            working ?: throw IOException("OneDrive indisponível")
        }
        return b.downloadUrl(path)
    }

    private fun listBlocking(path: String): List<RemoteEntry> {
        val diag = mutableListOf<String>()

        val known = working
        if (known != null) {
            try {
                return known.list(path)
            } catch (e: Exception) {
                diag += "${known.label}: ${e.message}"
            }
        }

        for (b in backends()) {
            if (b === known) continue
            try {
                val result = b.list(path)
                working = b
                return result
            } catch (e: Exception) {
                diag += "${b.label}: ${e.message}"
            }
        }
        throw OneDriveException(synchronized(notes) { notes.toList() } + diag)
    }

    // ------------------------------------------------------------------ escolha dos caminhos de acesso

    @Synchronized
    private fun backends(): List<Backend> {
        candidates?.let { return it }

        var redeemDoRedirect: String? = null
        try {
            val req = Request.Builder().url(shareUrl).header("User-Agent", UA).build()
            Http.client.newCall(req).execute().use { resp ->
                val finalUrl = resp.request.url
                redeemDoRedirect = finalUrl.queryParameter("redeem")
                notes += "Link aberto em ${finalUrl.host} (código de acesso " +
                    (if (redeemDoRedirect != null) "encontrado)" else "não encontrado)")
            }
        } catch (e: Exception) {
            notes += "Falha ao abrir o link: ${e.message}"
        }

        val lista = mutableListOf<Backend>()
        val redeems = listOfNotNull(redeemDoRedirect, b64url(shareUrl)).distinct()
        redeems.forEachIndexed { i, r ->
            lista += BadgerBackend(r, "my.microsoftpersonalcontent.com [acesso ${i + 1}]")
        }
        lista += OneDriveApiBackend("u!" + b64url(shareUrl))
        candidates = lista
        return lista
    }

    // ------------------------------------------------------------------ conversão da resposta

    private fun parseInto(out: MutableList<RemoteEntry>, json: JSONObject, path: String) {
        val arr = json.optJSONArray("value") ?: throw IOException("resposta sem a lista de itens")
        val agora = System.currentTimeMillis()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val name = o.optString("name")
            if (name.isEmpty()) continue
            val isFolder = o.has("folder")
            val ext = name.substringAfterLast('.', "").lowercase()
            if (!isFolder && ext !in AUDIO_EXT) continue
            val entryPath = if (path.isEmpty()) name else "$path/$name"
            if (!isFolder) {
                val dl = o.optString("@content.downloadUrl")
                    .ifEmpty { o.optString("@microsoft.graph.downloadUrl") }
                if (dl.isNotEmpty()) urlCache[entryPath] = dl to agora
            }
            out += RemoteEntry(
                name = name,
                path = entryPath,
                isFolder = isFolder,
                size = o.optLong("size", 0L),
                contentUrl = if (isFolder) null else pseudoUrl(entryPath)
            )
        }
    }

    private fun sorted(list: List<RemoteEntry>): List<RemoteEntry> =
        list.sortedWith(compareByDescending<RemoteEntry> { it.isFolder }.thenBy { it.name.lowercase() })

    // ------------------------------------------------------------------ caminho novo (token de visitante)

    @Synchronized
    private fun badgerToken(force: Boolean): String {
        val agora = System.currentTimeMillis()
        val atual = token
        if (!force && atual != null && agora - tokenAt < 15 * 60 * 1000L) return atual

        val body = "{\"appId\":\"$BADGER_APP_UUID\"}".toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url(BADGER_URL)
            .header("AppId", BADGER_APP_ID)
            .header("User-Agent", UA)
            .post(body)
            .build()
        return Http.client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("token de visitante: HTTP ${resp.code}")
            val novo = JSONObject(text).optString("token")
            if (novo.isEmpty()) throw IOException("token de visitante vazio")
            token = novo
            tokenAt = agora
            novo
        }
    }

    private fun badgerGet(url: String): String {
        for (tentativa in 0..1) {
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", UA)
                .header("Authorization", "Badger ${badgerToken(force = tentativa == 1)}")
                .header("Prefer", "autoredeem")
                .build()
            Http.client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (resp.isSuccessful) return text
                if (resp.code != 401 || tentativa == 1) {
                    throw IOException("HTTP ${resp.code}${errorHint(text)}")
                }
            }
        }
        throw IOException("falha de autenticação")
    }

    private inner class BadgerBackend(val redeem: String, override val label: String) : Backend {

        private val base = "$PERSONAL_API/u!$redeem"

        override fun list(path: String): List<RemoteEntry> {
            val enc = encodePath(path)
            val primeira = if (enc.isEmpty()) "$base/driveitem/children" else "$base/driveitem:/$enc:/children"
            return try {
                collect(primeira, path)
            } catch (e: IOException) {
                if (enc.isEmpty()) throw e
                collect("$base/root:/$enc:/children", path)
            }
        }

        private fun collect(start: String, path: String): List<RemoteEntry> {
            val out = mutableListOf<RemoteEntry>()
            var url: String? = start
            var pages = 0
            while (pages < 50) {
                val atual: String = url ?: break
                pages++
                val json = JSONObject(badgerGet(atual))
                parseInto(out, json, path)
                url = json.optString("@odata.nextLink").takeIf { it.isNotEmpty() }
            }
            return sorted(out)
        }

        override fun downloadUrl(path: String): String {
            cached(path)?.let { return it }
            // Link expirou (ou nunca foi lido): relê a pasta e pega o link novo.
            list(path.substringBeforeLast('/', ""))
            return cached(path) ?: throw IOException("link de download indisponível")
        }

        private fun cached(path: String): String? {
            val e = urlCache[path] ?: return null
            return if (System.currentTimeMillis() - e.second < URL_TTL_MS) e.first else null
        }
    }

    // ------------------------------------------------------------------ caminho antigo (reserva)

    private inner class OneDriveApiBackend(val shareId: String) : Backend {
        override val label = "api.onedrive.com [formato antigo]"

        override fun list(path: String): List<RemoteEntry> {
            val enc = encodePath(path)
            val start = if (enc.isEmpty()) "$ONEDRIVE_API/$shareId/root/children?top=200"
            else "$ONEDRIVE_API/$shareId/root:/$enc:/children?top=200"

            val out = mutableListOf<RemoteEntry>()
            var url: String? = start
            var pages = 0
            while (pages < 50) {
                val atual: String = url ?: break
                pages++
                val req = Request.Builder().url(atual).header("Accept", "application/json").build()
                Http.client.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}${errorHint(text)}")
                    val json = JSONObject(text)
                    parseInto(out, json, path)
                    url = json.optString("@odata.nextLink").takeIf { it.isNotEmpty() }
                }
            }
            return sorted(out)
        }

        override fun downloadUrl(path: String): String =
            "$ONEDRIVE_API/$shareId/root:/${encodePath(path)}:/content"
    }
}
