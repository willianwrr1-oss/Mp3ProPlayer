package com.willian.mp3pro.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Pasta do celular escolhida pelo usuário no seletor de pastas do Android. */
data class SafRoot(val treeUri: String, val name: String)

/** Pasta dentro de uma [SafRoot] (o primeiro item do caminho é a própria pasta escolhida). */
data class SafCrumb(val docId: String, val name: String)

/**
 * Navegação por qualquer pasta do celular usando o seletor de pastas do Android.
 * Não depende do índice de mídia, então encontra também pastas que ele ignora.
 */
object SafLibrary {
    private const val PREFS = "saf_folders"
    private const val KEY = "list"

    fun load(context: Context): List<SafRoot> {
        val text = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        return try {
            val arr = JSONArray(text)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                SafRoot(o.getString("uri"), o.getString("name"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, list: List<SafRoot>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("uri", it.treeUri).put("name", it.name)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    fun rootDocId(treeUri: String): String = DocumentsContract.getTreeDocumentId(Uri.parse(treeUri))

    /** Nome da pasta escolhida, como aparece no gerenciador de arquivos. */
    fun displayName(context: Context, treeUri: String): String {
        val tree = Uri.parse(treeUri)
        val docId = DocumentsContract.getTreeDocumentId(tree)
        val fallback = docId.substringAfterLast(':').substringAfterLast('/').ifEmpty { "Pasta" }
        return try {
            val uri = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
            context.contentResolver.query(uri, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                ?.takeIf { it.isNotBlank() } ?: fallback
        } catch (e: Exception) {
            fallback
        }
    }

    /** Subpastas e músicas diretamente dentro de [docId]. */
    suspend fun list(
        context: Context,
        root: SafRoot,
        docId: String
    ): Pair<List<SafCrumb>, List<RemoteEntry>> = withContext(Dispatchers.IO) {
        val tree = Uri.parse(root.treeUri)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE
        )
        val folders = mutableListOf<SafCrumb>()
        val tracks = mutableListOf<RemoteEntry>()
        context.contentResolver.query(children, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val mime = c.getString(2).orEmpty()
                if (mime == Document.MIME_TYPE_DIR) {
                    folders += SafCrumb(id, name)
                } else if (AudioFormats.isAudio(name)) {
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(tree, id).toString()
                    tracks += RemoteEntry(
                        name = name,
                        path = docUri,
                        isFolder = false,
                        size = c.getLong(3),
                        contentUrl = docUri
                    )
                }
            }
        }
        folders.sortBy { it.name.lowercase() }
        tracks.sortBy { it.name.lowercase() }
        folders to tracks
    }
}
