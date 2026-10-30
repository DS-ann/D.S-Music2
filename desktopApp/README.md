# SpatialFlow for Windows

This module is the Windows desktop application in the same repository as the Android phone app. The Windows branch is kept current with the Android `master` source snapshot; Android platform code remains under `app/`, while Windows-specific adapters and UI remain under `desktopApp/`.

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

The installer is written under `desktopApp/build/compose/binaries/main/exe/`.

## Included in the Windows desktop port

- Compose Desktop shell with Explore, Library, Cache and Settings.
- Local music folder selection, recursive scan, local search and playback.
- YouTube Music search adapted from the current phone search pipeline: mixed results, category-specific result shelves, best-match selection, live search, podcast shows and episode/video result typing.

Online result playback, Android Media3 cache migration, lyrics provider/cache integration, account/session features, and phone-only widgets/services still require Windows-specific implementations. Android code is synchronized into this branch, but Android-only classes are not loaded into the Windows JVM.
