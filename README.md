# WavePlayer — local music player

Kotlin + Jetpack Compose + Media3 + DataStore.

## Features
- Home = full vertical song list. Scan lives in Settings (progress, count, manual rescan).
- Pages: Home, Songs, Albums, Artists, Folders, Favorites, Settings.
- Lyrics: .lrc (all common timestamp styles, word-level <mm:ss.xxx> tags stripped), plain text,
  embedded MP3 (USLT/SYLT) and FLAC, manual add/import, LRCLIB lookup (internet).
- Vocal / Instrumental: real-time spectral soft-mask separation (STFT, not neural) in a Media3 AudioProcessor.
- Equalizer: android.media.audiofx Equalizer + BassBoost + Virtualizer on the ExoPlayer audio session,
  8 presets, per-band sliders, Reset. Settings persisted.
- Themes, dynamic artwork background, DataStore preferences.

## Not included
- Neural (Demucs) separation. Embedded lyrics for M4A/OGG.

## Build
Android Studio Hedgehog (JDK 17) -> Sync -> Build APK, or push to GitHub: the Actions workflow builds the APK.
