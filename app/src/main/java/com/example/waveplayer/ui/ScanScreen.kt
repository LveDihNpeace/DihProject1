package com.example.waveplayer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.waveplayer.viewmodel.LibraryViewModel

/** Scan Music page: the existing ScanCard (progress, song count, rescan). Progress is collected only here. */
@Composable
fun ScanScreen(library: LibraryViewModel) {
    val scan by library.scan.collectAsStateWithLifecycle()
    val lib by library.library.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        ScanCard(scan, lib.songs.size, library::scan)
    }
}
