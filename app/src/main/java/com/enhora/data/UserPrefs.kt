package com.enhora.data

import android.content.Context

/** Preferencias del usuario: su nombre (primera apertura) y la apariencia elegida. */
class UserPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("enhora_user", Context.MODE_PRIVATE)

    var name: String?
        get() = prefs.getString(KEY_NAME, null)?.takeIf { it.isNotBlank() }
        set(value) { prefs.edit().putString(KEY_NAME, value?.trim()).apply() }

    /** 0 = sistema, 1 = claro, 2 = noche. */
    var themeMode: Int
        get() = prefs.getInt(KEY_MODE, 0)
        set(value) { prefs.edit().putInt(KEY_MODE, value).apply() }

    /** Índice en `AppPalettes`, o `AUTO` (por defecto) para que el color cambie según la hora del día. */
    var paletteIndex: Int
        get() = when {
            prefs.contains(KEY_PALETTE_V2) -> prefs.getInt(KEY_PALETTE_V2, AUTO)
            else -> AUTO
        }
        set(value) { prefs.edit().putInt(KEY_PALETTE_V2, value).apply() }

    private companion object {
        /** Debe coincidir con `AutoPaletteIndex` (número de paletas fijas). */
        const val AUTO = 3

        const val KEY_NAME = "name"
        const val KEY_MODE = "theme_mode"
        const val KEY_PALETTE_V2 = "palette_v2"
    }
}
