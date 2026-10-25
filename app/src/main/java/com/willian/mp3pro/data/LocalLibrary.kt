package com.willian.mp3pro.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Música encontrada no armazenamento do aparelho. */
data class LocalTrack(
    val id: Long,
    val uri: String,
    val name: String,
    val title: String,
    val artist: String,
    /** Pasta relativa, sempre terminando em "/" (ex.: "Music/Rock/"); vazio = sem pasta. */
    val dir: String,
    val size: Long
) {
    fun toEntry() = RemoteEntry(
        name = name,
        path = "local:$id",
        isFolder = false,
        size = size,
        contentUrl = uri,
        // Sem etiqueta de artista, o título do índice é só o nome do arquivo:
        // nesse caso deixa o app separar "Título - Artista" pelo nome.
        metaTitle = title.takeIf { artist.isNotEmpty() },
        metaArtist = artist
    )
}

/**
 * Procura as músicas do celular (memória interna e cartão SD) pelo índice de mídia do
 * Android. Precisa da permissão "Música e áudio" (READ_MEDIA_AUDIO).
 */
object LocalLibrary {

    suspend fun scan(context: Context): List<LocalTrack> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.RELATIVE_PATH
        )
        // Ignora toques, notificações, alarmes e gravações de voz.
        val selection = "${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND " +
            "${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 AND " +
            "${MediaStore.Audio.Media.IS_ALARM} = 0 AND " +
            "${MediaStore.Audio.Media.IS_RECORDING} = 0"
        val order = "${MediaStore.Audio.Media.DISPLAY_NAME} COLLATE NOCASE ASC"

        val out = mutableListOf<LocalTrack>()
        context.contentResolver.query(collection, projection, selection, null, order)?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iSize = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val iDir = c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            while (c.moveToNext()) {
                val name = c.getString(iName) ?: continue
                if (!AudioFormats.isAudio(name)) continue
                val id = c.getLong(iId)
                val artist = c.getString(iArtist).orEmpty().takeUnless { it == "<unknown>" }.orEmpty()
                val dir = c.getString(iDir).orEmpty().let {
                    if (it.isEmpty() || it.endsWith("/")) it else "$it/"
                }
                out += LocalTrack(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    name = name,
                    title = c.getString(iTitle).orEmpty(),
                    artist = artist,
                    dir = dir,
                    size = c.getLong(iSize)
                )
            }
        }
        out
    }
}
