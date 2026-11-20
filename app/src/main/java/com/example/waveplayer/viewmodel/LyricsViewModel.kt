package com.example.waveplayer.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.waveplayer.data.LrcParser
import com.example.waveplayer.data.Lyrics
import com.example.waveplayer.data.LyricsRepository
import com.example.waveplayer.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LyricsState(val lyrics: Lyrics? = null, val loading: Boolean = false, val message: String? = null)

class LyricsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = LyricsRepository(app)
    private val _state = MutableStateFlow(LyricsState())
    val state: StateFlow<LyricsState> = _state.asStateFlow()
    private var currentId: Long? = null

    fun load(song: Song?) {
        currentId = song?.id
        if (song == null) { _state.value = LyricsState(); return }
        viewModelScope.launch {
            _state.value = LyricsState(loading = true)
            val l = withContext(Dispatchers.IO) { repo.load(song) }
            if (currentId == song.id) _state.value = LyricsState(lyrics = l)
        }
    }

    fun saveText(song: Song, text: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.save(song.id, text) }
            val parsed = LrcParser.parse(text)
            if (currentId == song.id) _state.value = LyricsState(lyrics = parsed, message = if (parsed == null) "Teks lirik kosong" else null)
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
            if (currentId == song.id) _state.value = LyricsState()
        }
    }
}
