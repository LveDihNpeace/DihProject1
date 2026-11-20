# WavePlayer — local music player (Salt Player style)

Kotlin + Jetpack Compose + Media3 (ExoPlayer + MediaSession).

Features: scan MediaStore, Songs/Albums/Artists tabs, album art, mini player,
full Now Playing screen (seek, shuffle, repeat), background playback with
notification + lockscreen/Bluetooth controls, Material You dynamic colors.

Build: open in Android Studio Hedgehog (JDK 17) -> Sync -> Run.
APK: Build > Build Bundle(s)/APK(s) > Build APK(s)
     or ./gradlew assembleRelease (output: app/build/outputs/apk/release/)
