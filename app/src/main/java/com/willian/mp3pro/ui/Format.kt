package com.willian.mp3pro.ui

import java.util.Locale

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return String.format(Locale.US, "%d:%02d", m, s)
}

/** Sempre em MB, com separador de milhar (ex.: 1.234,5 MB). */
fun formatMb(bytes: Long): String =
    String.format(Locale("pt", "BR"), "%,.1f MB", bytes / (1024.0 * 1024.0))

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) String.format(Locale("pt", "BR"), "%.1f GB", mb / 1024)
    else String.format(Locale("pt", "BR"), "%.1f MB", mb)
}
