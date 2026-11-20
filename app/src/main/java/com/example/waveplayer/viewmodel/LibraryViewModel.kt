package com.example.waveplayer.viewmodel

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.waveplayer.data.MusicRepository
import com.example.waveplayer.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ScanState(
    val isScanning: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val message: String = "",
)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _scan = MutableStateFlow(ScanState())
    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    private var started = false
    private var debounce: Job? = null

    // New songs appearing in MediaStore are picked up automatically (debounced).
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (!started) return
            debounce?.cancel()
            debounce = viewModelScope.launch { delay(3000); scan() }
        }
    }

    init {
        app.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
    }

    /** First load only: does nothing if the library was already scanned (e.g. after rotation). */
    fun ensureLoaded() { if (!started) scan() }

    fun scan() {
        started = true
        if (_scan.value.isScanning) return
        viewModelScope.launch {
            _scan.value = ScanState(isScanning = true, message = "Memindai…")
            val list = withContext(Dispatchers.IO) {
                MusicRepository.loadSongs(getApplication()) { done, total ->
                    _scan.update { it.copy(done = done, total = total, message = "Memindai $done / $total") }
                }
            }
            if (list != _songs.value) _songs.value = list // unchanged result: no recomposition, no playlist churn
            _scan.value = ScanState(false, list.size, list.size, "${list.size} lagu ditemukan")
        }
    }

    override fun onCleared() {
        getApplication<Application>().contentResolver.unregisterContentObserver(observer)
        super.onCleared()
    }
}
