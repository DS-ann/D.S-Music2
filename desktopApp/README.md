# SpatialFlow for Windows

This module is the first isolated desktop port of SpatialFlow. It is built with Kotlin/JVM and Compose Desktop; it does not change the Android application source or Android playback service.

## Build on Windows

Requirements: JDK 17 or newer and the repository Gradle wrapper.

Run the desktop application:

```powershell
.\gradlew.bat :desktopApp:run
```

Build the Windows installer:

```powershell
.\gradlew.bat :desktopApp:packageExe
```

The installer is written under:

`desktopApp/build/compose/binaries/main/exe/`

The `Windows Desktop` GitHub Actions workflow builds this installer and uploads it as a downloadable workflow artifact.

## Included in this first port

- A Windows desktop application shell with Explore, Library, Cache and Settings sections.
- Local music folder selection, saved folder preference and recursive library scanning.
- Local search by title, containing folder/artist label and extension.
- A queue-like track list with previous, play/pause and next controls.
- Local playback using Java Sound, MP3SPI and VorbisSPI.

The current Java Sound backend supports MP3, OGG, WAV, AIFF and AU files where the local Java Sound device supports the decoded PCM format. This first slice does not yet cover M4A/AAC, FLAC, Opus or WebM.

## Not yet ported

The Android UI and services cannot be compiled as a Windows application unchanged. In the Android module, the screen entry point is an Android `AppCompatActivity`, streaming extraction depends on Android-bound initialization and preferences, playback uses Media3/Android services, and lyrics/cache storage uses Android context and storage APIs.

The following features therefore remain future desktop-port work rather than fake or placeholder implementations:

- YouTube Music search, streaming, account support and downloads.
- Lyrics providers, synchronized lyrics and lyrics cache.
- Persistent playlists, listening history and library database.
- Offline stream cache and Android Media3 cache migration.
- Equalizer, audio effects, crossfade, visualizers and Android media-session integration.

The module is kept separate so these capabilities can be moved to platform-neutral Kotlin services incrementally. The Android app remains on its existing build path.
