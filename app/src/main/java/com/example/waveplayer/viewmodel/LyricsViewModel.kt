package com.example.waveplayer.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.waveplayer.data.LyricLine
import com.example.waveplayer.data.Lyrics
import com.example.waveplayer.data.LyricsRepository
import com.example.waveplayer.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class LyricsState(val lyrics: Lyrics? = null, val loading: Boolean = false, val message: String? = null)

class LyricsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = LyricsRepository(app)
    private val _state = MutableStateFlow(LyricsState())
    val state: StateFlow<LyricsState> = _state.asStateFlow()
    private var currentId: Long? = null
    private var loadedId: Long? = null

    /** Loads lyrics for [song]; does nothing if they are already loaded (reopening Now Playing is free). */
    fun load(song: Song?, force: Boolean = false) {
        if (song == null) { currentId = null; loadedId = null; _state.value = LyricsState(); return }
        if (!force && song.id == loadedId && song.id == currentId) return
        currentId = song.id
        loadedId = null
        viewModelScope.launch {
            _state.value = LyricsState(loading = true)
            val l = withContext(Dispatchers.IO) { repo.load(song) }
            if (currentId == song.id) { _state.value = LyricsState(lyrics = l); loadedId = song.id }
        }
    }

    fun saveText(song: Song, text: String) {
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.IO) { repo.save(song.id, text); repo.parse(song.id, text) }
            if (currentId == song.id) {
                _state.value = LyricsState(lyrics = parsed, message = if (parsed == null) "Teks lirik kosong" else null)
                loadedId = song.id
            }
        }
    }

    fun importUri(song: Song, uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            if (text.isNullOrBlank()) _state.value = _state.value.copy(message = "File tidak bisa dibaca")
            else saveText(song, text)
        }
    }

    fun fetchOnline(song: Song) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, message = null)
            val text = repo.fetchOnline(song)
            if (text == null) {
                if (currentId == song.id) _state.value = _state.value.copy(loading = false, message = "Lirik tidak ditemukan (atau tidak ada koneksi internet)")
            } else saveText(song, text)
        }
    }

    fun delete(song: Song) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.delete(song.id) }
            if (currentId == song.id) { _state.value = LyricsState(); loadedId = song.id }
        }
    }

    /** Word timings entered by the user for one line (real timestamps, nothing generated). */
    fun saveWordTiming(song: Song, line: LyricLine, times: List<Long>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.saveWordTiming(song.id, line, times) }
            load(song, force = true)
        }
    }

    fun clearWordTiming(song: Song, line: LyricLine) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.clearWordTiming(song.id, line) }
            load(song, force = true)
        }
    }
}
