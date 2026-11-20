package com.example.waveplayer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.waveplayer.data.AppSettings
import com.example.waveplayer.data.ArtShape
import com.example.waveplayer.data.ListStyle
import com.example.waveplayer.data.LyricsPosition
import com.example.waveplayer.data.SettingsRepository
import com.example.waveplayer.data.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository(app)

    val settings: StateFlow<AppSettings> =
        repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val favorites: StateFlow<Set<Long>> =
        repo.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val recent: StateFlow<List<Long>> =
        repo.recent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setTheme(m: ThemeMode) = launch { repo.put(SettingsRepository.THEME, m.name) }
    fun setAccent(c: Int) = launch { repo.put(SettingsRepository.ACCENT, c) }
    fun setBackground(c: Int) = launch { repo.put(SettingsRepository.BACKGROUND, c) }
    fun setBlur(v: Float) = launch { repo.put(SettingsRepository.BLUR, v) }
    fun setArtShape(s: ArtShape) = launch { repo.put(SettingsRepository.ART_SHAPE, s.name) }
    fun setListStyle(s: ListStyle) = launch { repo.put(SettingsRepository.LIST_STYLE, s.name) }
    fun setLyricSize(v: Float) = launch { repo.put(SettingsRepository.LYRIC_SIZE, v) }
    fun setLyricsPosition(p: LyricsPosition) = launch { repo.put(SettingsRepository.LYRICS_POS, p.name) }
    fun setLyricBlur(on: Boolean) = launch { repo.put(SettingsRepository.LYRIC_BLUR, on) }
    fun setLyricBlurIntensity(v: Float) = launch { repo.put(SettingsRepository.LYRIC_BLUR_INT, v) }
    fun setWordHighlight(on: Boolean) = launch { repo.put(SettingsRepository.WORD_HL, on) }
    fun setLyricsAutoScroll(on: Boolean) = launch { repo.put(SettingsRepository.AUTO_SCROLL, on) }
    fun toggleFavorite(id: Long) = launch { repo.toggleFavorite(id) }

    private fun launch(block: suspend () -> Unit) { viewModelScope.launch { block() } }
}
