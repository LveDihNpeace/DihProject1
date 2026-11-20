package com.example.waveplayer.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) {
    SYSTEM("System"), LIGHT("Light"), DARK("Dark"), AMOLED("AMOLED Black"), DYNAMIC("Dynamic (artwork)")
}
enum class ArtShape(val label: String) { ROUNDED("Rounded"), SQUARE("Square"), CIRCLE("Circle") }
enum class ListStyle(val label: String) { COMFORTABLE("Comfortable"), COMPACT("Compact") }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: Int = SettingsRepository.DEFAULT_ACCENT,
    val background: Int = 0, // 0 = theme default
    val blur: Float = 0.6f,
    val artShape: ArtShape = ArtShape.ROUNDED,
    val listStyle: ListStyle = ListStyle.COMFORTABLE,
    val lyricSize: Float = 26f,
)

private val Context.dataStore by preferencesDataStore(name = "wave_settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            themeMode = p[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            accent = p[ACCENT] ?: DEFAULT_ACCENT,
            background = p[BACKGROUND] ?: 0,
            blur = p[BLUR] ?: 0.6f,
            artShape = p[ART_SHAPE]?.let { runCatching { ArtShape.valueOf(it) }.getOrNull() } ?: ArtShape.ROUNDED,
            listStyle = p[LIST_STYLE]?.let { runCatching { ListStyle.valueOf(it) }.getOrNull() } ?: ListStyle.COMFORTABLE,
            lyricSize = p[LYRIC_SIZE] ?: 26f,
        )
    }

    val favorites: Flow<Set<Long>> = store.data.map { p ->
        p[FAVORITES]?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
    }

    suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        store.edit { it[key] = value }
    }

    suspend fun toggleFavorite(id: Long) {
        store.edit { p ->
            val set = p[FAVORITES]?.toMutableSet() ?: mutableSetOf()
            if (!set.remove(id.toString())) set.add(id.toString())
            p[FAVORITES] = set
        }
    }

    companion object {
        val DEFAULT_ACCENT: Int = 0xFF7C5CFF.toInt()
        val THEME = stringPreferencesKey("theme_mode")
        val ACCENT = intPreferencesKey("accent")
        val BACKGROUND = intPreferencesKey("background")
        val BLUR = floatPreferencesKey("blur")
        val ART_SHAPE = stringPreferencesKey("art_shape")
        val LIST_STYLE = stringPreferencesKey("list_style")
        val LYRIC_SIZE = floatPreferencesKey("lyric_size")
        val FAVORITES = stringSetPreferencesKey("favorites")
    }
}
