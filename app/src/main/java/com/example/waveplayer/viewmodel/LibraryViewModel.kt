package com.example.waveplayer.viewmodel

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.waveplayer.data.Library
import com.example.waveplayer.data.MusicRepository
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
    private val _library = MutableStateFlow(Library())
    val library: StateFlow<Library> = _library.asStateFlow()

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
            // Everything heavy (MediaStore query, de-dup, grouping) runs on the IO dispatcher.
            val result = withContext(Dispatchers.IO) {
                val songs = MusicRepository.loadSongs(getApplication()) { done, total ->
                    _scan.update { it.copy(done = done, total = total, message = "Memindai $done / $total") }
                }
                if (songs == _library.value.songs) null else Library.build(songs)
            }
            if (result != null) _library.value = result // unchanged result: no recomposition
            val count = _library.value.songs.size
            _scan.value = ScanState(false, count, count, "$count lagu ditemukan")
        }
    }

    override fun onCleared() {
        getApplication<Application>().contentResolver.unregisterContentObserver(observer)
        super.onCleared()
    }
}
