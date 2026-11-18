# Neymis Files

A file manager for Android: local files, archives and network folders in one
app. No ads, no analytics, no tracking.

## Features

- Browse, copy, move, rename and delete files
- Search and sorting (folders first)
- Create folders
- Open files with other apps
- Extract ZIP archives
- Install APK files
- Dark theme (follows system)
- Works offline; internet is only needed for network folders (planned)

> Network folders (FTP/SMB) are on the roadmap and not yet implemented in the
> first release.

## Build

You need JDK 17 and Android SDK (API 34).

```sh
./gradlew assembleDebug
```

The APK appears at:

```
app/build/outputs/apk/debug/app-debug.apk
```

For a release build you need your own keystore:

```sh
./gradlew assembleRelease
```

Do not commit `*.keystore` to the repository.

## Build in GitHub Actions

Push a tag starting with `v` (for example `v1.0`) — the workflow builds the APK
and attaches it to the run as an artifact. A Gradle wrapper (`gradlew`) must be
committed in the repository.

## License

MIT. See [LICENSE](LICENSE).
