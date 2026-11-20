# WavePlayer — local music player

Kotlin + Jetpack Compose + Media3 + DataStore.

## Phase 1 (this version)
- Smart scanner: MediaStore (all folders/volumes), duplicate filter, song count, live progress,
  manual rescan, auto-pickup of new songs, filters by artist / album / genre / folder.
- Pages: Home, Songs, Albums, Artists, Folders, Favorites, Settings.
- Lyrics: .lrc (synced) + plain text, highlight + auto-scroll, tap line to seek, manual add/edit,
  import file, online lookup (LRCLIB, needs internet), saved in app storage.
- Dynamic Apple-Music-style background from artwork colors (blur image on Android 12+).
- Themes: System / Light / Dark / AMOLED / Dynamic(artwork); accent, background, artwork shape,
  list style, blur, lyric size - all persisted with DataStore.

## Not yet (Phase 2)
- Vocal / instrumental separation: real-time stereo DSP (not neural). Neural (Demucs) not included.
- Embedded lyrics: MP3 (ID3 USLT) and FLAC (Vorbis comment). M4A/OGG not yet.

## Build
Android Studio Hedgehog (JDK 17) -> Sync -> Build APK, or push to GitHub: the Actions workflow builds the APK.
