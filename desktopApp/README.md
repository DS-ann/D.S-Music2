# D.S Music for Windows

This module is the Windows desktop application in the same repository as the Android phone app. The Windows branch contains a native Windows desktop implementation, synchronized with shared D.S Music features where platform equivalents exist; Android platform code remains under `app/`, while Windows-specific adapters and UI remain under `desktopApp/`.

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

Windows uses its own Java Sound and JavaFX playback, Windows storage and Compose Desktop UI. Android-only services and widgets are not copied into the Windows JVM; equivalent features are implemented using Windows-native components.
