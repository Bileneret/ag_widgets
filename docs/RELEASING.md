# Releasing AG Widgets

## Products

Two Gradle product flavors share one codebase:

| Flavor | applicationId | App name | ZCode |
|---|---|---|---|
| `full` | `dev.agwidgets` | AG Widgets | yes |
| `context` | `dev.agwidgets.context` | AG Context | no |

## Signing key

The Android release key lives outside the repository:

```text
%LOCALAPPDATA%\ag-widgets\keys\
  ag-widgets-release.jks
  keystore.properties
  README.txt
```

`keystore.properties` contains:

```properties
storeFile=C:/Users/<user>/AppData/Local/ag-widgets/keys/ag-widgets-release.jks
storePassword=<secret>
keyAlias=agwidgets
keyPassword=<secret>
```

Never commit or copy these files into the project. Back them up: if the key
is lost, existing users cannot install an update over the app.

CI can override the path with `AGW_KEYSTORE_PROPERTIES`.

## Build signed community APKs

`-Pcommunity=true` keeps `BuildConfig.AGW_TOPIC` empty, so no personal ntfy
topic is embedded. Use `--no-configuration-cache` for release builds: the
signing key is read from an external file, which Gradle's configuration cache
does not track.

```powershell
cd android
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleFullRelease :app:assembleContextRelease `
  -Pcommunity=true --no-configuration-cache
```

Outputs:

```text
android/app/build/outputs/apk/full/release/app-full-release.apk
android/app/build/outputs/apk/context/release/app-context-release.apk
```

## Verify

```powershell
$apksigner = "$env:LOCALAPPDATA\Android\Sdk\build-tools\37.0.0\apksigner.bat"
$full = "android\app\build\outputs\apk\full\release\app-full-release.apk"
$context = "android\app\build\outputs\apk\context\release\app-context-release.apk"

& $apksigner verify --verbose --print-certs $full
& $apksigner verify --verbose --print-certs $context

& "$env:LOCALAPPDATA\Android\Sdk\build-tools\37.0.0\aapt.exe" dump badging $full
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\37.0.0\aapt.exe" dump badging $context
```

Check package ids and empty topics in the generated BuildConfig files:

```powershell
Get-Content android\app\build\generated\source\buildConfig\full\release\dev\agwidgets\BuildConfig.java |
  Select-String "APPLICATION_ID|AGW_TOPIC|ZCODE_ENABLED"
Get-Content android\app\build\generated\source\buildConfig\context\release\dev\agwidgets\BuildConfig.java |
  Select-String "APPLICATION_ID|AGW_TOPIC|ZCODE_ENABLED"
```

Expected:

- full: `dev.agwidgets`, `AGW_TOPIC=""`, `ZCODE_ENABLED=true`
- context: `dev.agwidgets.context`, `AGW_TOPIC=""`, `ZCODE_ENABLED=false`

`aapt dump badging` does **not** print `AGW_TOPIC`; use BuildConfig or scan the
DEX if you need to prove that no topic is embedded.

## Tests

```powershell
.\gradlew.bat :app:testFullDebugUnitTest :app:testContextDebugUnitTest
```

## Release assets

Use one tag per release, for example `v1.0.0`, with:

```text
AG-Widgets-1.0.0.apk
AG-Widgets-1.0.0.apk.sha256
AG-Context-1.0.0.apk
AG-Context-1.0.0.apk.sha256
```

Release notes are in `CHANGELOG.md`; keep the GitHub release description short
and include both SHA-256 values.

## Personal build (not for publication)

A personal APK embeds your own `ntfy_topic` from the runtime config. Build it
locally only, keep it outside the repository, and never attach it to a public
release:

1. Add `agw.topic=<topic>` to `android/local.properties`.
2. Run `.\gradlew.bat :app:assembleFullRelease --no-configuration-cache`
   (or `assembleFullDebug` for a debug build).
3. Copy the APK to `%LOCALAPPDATA%\ag-widgets\`.
4. Remove `agw.topic` from `local.properties` immediately afterwards.
