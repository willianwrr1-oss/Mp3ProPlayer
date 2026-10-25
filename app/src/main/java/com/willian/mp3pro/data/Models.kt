package com.willian.mp3pro.data

/** Item da pasta do OneDrive: uma subpasta ou um arquivo de áudio. */
data class RemoteEntry(
    val name: String,
    /** Caminho relativo à pasta compartilhada, com "/" (vazio = raiz). */
    val path: String,
    val isFolder: Boolean,
    val size: Long,
    /** URL estável de conteúdo (redireciona para o download). Nulo para pastas. */
    val contentUrl: String?
) {
    private val baseName: String get() = name.substringBeforeLast('.', name)

    /** Nomes no formato "Título - Artista.mp3". */
    val title: String
        get() = if (baseName.contains(" - ")) baseName.substringBefore(" - ").trim() else baseName

    val artist: String
        get() = if (baseName.contains(" - ")) baseName.substringAfter(" - ").trim() else ""

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
