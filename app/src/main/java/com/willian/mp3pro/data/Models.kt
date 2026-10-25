package com.willian.mp3pro.data

/** Formatos de áudio que o player reconhece (OneDrive e celular). */
object AudioFormats {
    val extensions = setOf(
        "mp3", "m4a", "m4b", "aac", "flac", "wav", "ogg", "oga", "opus",
        "amr", "awb", "mka", "weba", "ac3", "eac3", "3gp"
    )

    fun isAudio(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in extensions
}

/** Item da pasta do OneDrive ou do celular: uma subpasta ou um arquivo de áudio. */
data class RemoteEntry(
    val name: String,
    /** Identificador único. No OneDrive é o caminho relativo à pasta compartilhada (vazio = raiz). */
    val path: String,
    val isFolder: Boolean,
    val size: Long,
    /** Endereço de reprodução (interno do OneDrive ou content:// do celular). Nulo para pastas. */
    val contentUrl: String?,
    /** Título e artista vindos das etiquetas do arquivo (celular); se vazios, usa o nome do arquivo. */
    val metaTitle: String? = null,
    val metaArtist: String? = null
) {
    private val baseName: String get() = name.substringBeforeLast('.', name)

    /** Nomes no formato "Título - Artista.mp3". */
    val title: String
        get() = metaTitle?.takeIf { it.isNotBlank() }
            ?: if (baseName.contains(" - ")) baseName.substringBefore(" - ").trim() else baseName

    val artist: String
        get() = metaArtist?.takeIf { it.isNotBlank() }
            ?: if (baseName.contains(" - ")) baseName.substringAfter(" - ").trim() else ""

    val extension: String get() = name.substringAfterLast('.', "mp3").lowercase()
}

/** Música baixada para uso offline. */
data class DownloadedTrack(
    val path: String,
    val name: String,
    val size: Long,
    val fileName: String
) {
    private val baseName: String get() = name.substringBeforeLast('.', name)
    val title: String
        get() = if (baseName.contains(" - ")) baseName.substringBefore(" - ").trim() else baseName
    val artist: String
        get() = if (baseName.contains(" - ")) baseName.substringAfter(" - ").trim() else ""
}

class OneDriveException(val details: List<String>) :
    Exception(details.lastOrNull() ?: "Falha ao acessar o OneDrive")
