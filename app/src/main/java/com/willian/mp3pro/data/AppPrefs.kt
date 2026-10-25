package com.willian.mp3pro.data

import android.content.Context

/** Preferências simples do app (por enquanto, apenas o link da pasta do OneDrive). */
object AppPrefs {
    const val DEFAULT_SHARE_URL =
        "https://1drv.ms/f/c/92227ab28c9d7cdd/IgCHCbBPDbr_R7QDU3NuyFkqAcyvSe5RMBeigIXsweLfrs4?e=4ghplE"

    private const val FILE = "app_prefs"
    private const val KEY_SHARE = "share_url"

    fun shareUrl(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_SHARE, null)
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_SHARE_URL

    private const val KEY_SKIN = "player_skin"

    /** Skin do player escolhida pelo usuário ("vinil" é a padrão). */
    fun skin(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_SKIN, null) ?: "vinil"

    fun setSkin(context: Context, id: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_SKIN, id).apply()
    }

    private const val KEY_THEME = "theme_dark"

    /** Visual do app: true = escuro (noturno, padrão), false = claro. */
    fun darkTheme(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_THEME, true)

    fun setDarkTheme(context: Context, dark: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_THEME, dark).apply()
    }

    fun setShareUrl(context: Context, url: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_SHARE, url.trim()).apply()
    }
}
