# WavePlayer — local music player

Kotlin + Jetpack Compose + Media3 + DataStore.

## Features
- Redesigned UI (own typography/shapes, floating mini player + pill navigation, album-art centric lists).
- Home = full vertical song list with Shuffle / Play All; Scan Music lives in Settings.
- Now Playing: rotating circular album art (follows real playback state), dynamic artwork background,
  transition rising from the mini player.
- Lyrics: centered active line, neighbours visible, optional blur, word-by-word highlight (only with real word
  timings: enhanced LRC `<mm:ss.xxx>` or timings you set in the editor), auto-scroll on/off, position, font size.
- Home-screen widget (art, title, artist, prev/play/next) controlling the same player.
- Launcher shortcuts: Open WavePlayer, Favorites, Recently Played, Open Lyrics.
- Vocal / Instrumental separation (real-time spectral soft-mask, not neural), Equalizer (+Bass Boost, Virtualizer).
- Embedded lyrics: MP3 (USLT/SYLT) and FLAC.

## Build
Android Studio Hedgehog (JDK 17) -> Sync -> Build APK, or push to GitHub: the Actions workflow builds the APK.
