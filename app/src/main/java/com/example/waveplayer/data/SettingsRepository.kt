package com.example.waveplayer.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
enum class ArtShape(val label: String) { CIRCLE("Circle (berputar)"), ROUNDED("Rounded"), SQUARE("Square") }
enum class ListStyle(val label: String) { COMFORTABLE("Comfortable"), COMPACT("Compact") }
enum class LyricsPosition(val label: String, val fraction: Float) {
    UPPER("Atas", 0.33f), CENTER("Tengah", 0.5f), LOWER("Bawah", 0.66f)
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accent: Int = SettingsRepository.DEFAULT_ACCENT,
    val background: Int = 0, // 0 = theme default
    val blur: Float = 0.6f,
    val artShape: ArtShape = ArtShape.CIRCLE,
    val listStyle: ListStyle = ListStyle.COMFORTABLE,
    val lyricSize: Float = 26f,
    val lyricsPosition: LyricsPosition = LyricsPosition.CENTER,
    val lyricBlur: Boolean = true,
    val lyricBlurIntensity: Float = 0.6f,
    val wordHighlight: Boolean = true,
    val lyricsAutoScroll: Boolean = true,
)

private val Context.dataStore by preferencesDataStore(name = "wave_settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            themeMode = p[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
            accent = p[ACCENT] ?: DEFAULT_ACCENT,
            background = p[BACKGROUND] ?: 0,
            blur = p[BLUR] ?: 0.6f,
            artShape = p[ART_SHAPE]?.let { runCatching { ArtShape.valueOf(it) }.getOrNull() } ?: ArtShape.CIRCLE,
            listStyle = p[LIST_STYLE]?.let { runCatching { ListStyle.valueOf(it) }.getOrNull() } ?: ListStyle.COMFORTABLE,
            lyricSize = p[LYRIC_SIZE] ?: 26f,
            lyricsPosition = p[LYRICS_POS]?.let { runCatching { LyricsPosition.valueOf(it) }.getOrNull() } ?: LyricsPosition.CENTER,
            lyricBlur = p[LYRIC_BLUR] ?: true,
            lyricBlurIntensity = p[LYRIC_BLUR_INT] ?: 0.6f,
            wordHighlight = p[WORD_HL] ?: true,
            lyricsAutoScroll = p[AUTO_SCROLL] ?: true,
        )
    }

    val favorites: Flow<Set<Long>> = store.data.map { p ->
        p[FAVORITES]?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
    }

    /** Most recently played first, max 50. Written by the playback service, so it also counts background playback. */
    val recent: Flow<List<Long>> = store.data.map { p ->
        p[RECENT]?.split(',')?.mapNotNull { it.toLongOrNull() } ?: emptyList()
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

    suspend fun pushRecent(id: Long) {
        store.edit { p ->
            val list = p[RECENT]?.split(',')?.mapNotNull { it.toLongOrNull() }?.toMutableList() ?: mutableListOf()
            list.remove(id)
            list.add(0, id)
            p[RECENT] = list.take(50).joinToString(",")
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
        val LYRICS_POS = stringPreferencesKey("lyrics_position")
        val LYRIC_BLUR = booleanPreferencesKey("lyric_blur")
        val LYRIC_BLUR_INT = floatPreferencesKey("lyric_blur_intensity")
        val WORD_HL = booleanPreferencesKey("word_highlight")
        val AUTO_SCROLL = booleanPreferencesKey("lyrics_autoscroll")
        val FAVORITES = stringSetPreferencesKey("favorites")
        val RECENT = stringPreferencesKey("recent")
    }
}
