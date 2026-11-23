# TRISVAX Studio (Native Android)

100% offline-first native Android video editor built with Kotlin, Jetpack Compose, Material 3, Room, DataStore, CameraX, and AndroidX Media3 (ExoPlayer + Transformer + Composition).

## Project Structure

- `app/src/main/assets/music/` — Bundled offline audio tracks (`ambient_horizon.mp3`, `urban_pulse.mp3`, `golden_hour_lofi.mp3`)
- `app/src/main/java/com/trisvax/studio/`
  - `theme/` — Official TRISVAX dark palette tokens
  - `models/` — Serializable timeline, clip, crop, music, text overlay, voiceover models
  - `database/` — Room database (`TrisvaxDatabase`, `ProjectEntity`, `ProjectDao`)
  - `data/` — `MusicAssetCatalog` (loads `assets/music/`) & `PreferencesManager`
  - `repository/` — `ProjectRepository` (thumbnails, local storage metrics, app-private copy)
  - `video/timeline/` — `TimelineEngine` (trim, split, crop, rotate, speed, volume, reorder, delete)
  - `video/player/` — `TimelinePreviewPlayer` (ExoPlayer multi-clip + synced music & voiceover)
  - `video/audio/` — `VoiceoverRecorder` (`MediaRecorder` AAC/M4A)
  - `video/processing/` — `Media3ExportEngine` & `VideoExportService` (1080p H.264/AAC MP4 to `Movies/TRISVAX Studio/`)
  - `ui/` — Welcome, Projects, Media Picker, CameraX Capture, Editor, Export Progress, Save Confirmation

## Install & Test on Physical Android Phone

1. Open this `android-project` folder in **Android Studio** (Ladybug or newer).
2. Let Gradle sync download dependencies automatically.
3. On your Android phone, enable **Developer Options -> USB Debugging** and connect via USB.
4. Select your device in Android Studio and click **Run** (or run `./gradlew assembleDebug && adb install app/build/outputs/apk/debug/app-debug.apk`).
