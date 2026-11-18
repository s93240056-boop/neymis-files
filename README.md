# Neymis Files

A full-featured file manager for Android. Open source, no ads, no analytics.

## Features

**Files**
- Browse folders, navigate up, breadcrumb path
- Create folders and files, rename, delete
- Copy, cut, paste (with collision-safe names)
- Multi-select and batch copy/cut/delete/zip/share
- Search in current folder or recursively everywhere
- Sort by name, size or date (ascending/descending)
- Show/hide hidden files
- ZIP compression and extraction
- Open files with other apps, install APK
- Built-in text editor for .txt/.md/.log/.json/.xml/.csv
- Share one or many files
- File properties: size, path, dates, permissions
- Favorites (Download, DCIM, Pictures, Documents, Music, Movies)
- Storage usage info
- App manager: list installed apps, save their APK, launch them

**Themes**
- Purple-blue gradient (default)
- Light
- Dark

Switch the theme in the menu → **Тема**. The choice is remembered.

## Build

You need JDK 17 and Android SDK (API 34).

```sh
gradle assembleDebug
```

The APK appears at `app/build/outputs/apk/debug/app-debug.apk`.

For a release build you need your own keystore; never commit `*.keystore`.

## Build in GitHub Actions

Push a tag starting with `v` — the workflow builds the APK and attaches it to
a GitHub Release.

## License

MIT. See [LICENSE](LICENSE).
