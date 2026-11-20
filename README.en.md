# FL音坊 · Fluentic Audio Workshop

[中文](README.md) | **English** | [Official user guide](https://www.fluentic.cn/config/soft/)

An Android voice cloning and text-to-speech client built with Kotlin, Jetpack Compose, and Material 3. The current provider is Alibaba Cloud Model Studio CosyVoice. Bring your own API key.

> Early version: the core workflow has been verified on an Android emulator. Device compatibility and failure handling are still being improved. The repository is named FL.

## About the name

“FL音坊” takes its name from `fluentic`, the domain name of the author's personal website. It conveys the idea of a workshop for fluent voice and timbre processing.

## Features

- **Voice cloning:** record audio or import it through the system picker. The app checks the actual media duration (5–30 seconds) and a 10 MB size limit.
- **Persistent review status:** voice IDs are stored as soon as the provider returns them. Pending voices are checked while the app is running and the provider is configured and enabled.
- **Text to speech:** use a preset or an approved cloned voice, enter up to 500 characters, and adjust speed, volume, and pitch.
- **Playback and export:** play, pause, resume, replay, and export MP3 through the system save dialog.
- **Draft retention:** preserve forms across tab changes, settings navigation, and supported UI state restoration. Samples must be selected again if their cache files are cleared.
- **Provider configuration:** save your own key and enable the provider. Missing credentials produce a configuration prompt, not simulated success.
- **Dark interface:** neon gradients and glass-style components.

Only Alibaba Cloud is currently implemented. The data model leaves room for additional providers. The app interface is primarily Chinese; bilingual documentation does not imply an English app interface.

## Getting started

See the [official FL音坊 user guide](https://www.fluentic.cn/config/soft/) for the full tutorial. It is also accessible from the app settings.

1. Enable the required speech model services in the [Alibaba Cloud Model Studio console](https://bailian.console.aliyun.com/) and create an API key with access to them. The current code uses the Beijing-region endpoint.
2. Open the app → **设置** (Settings) → **服务商管理** (Providers) → **阿里云 · 百炼 CosyVoice**. Enter the key and enable the provider.
3. Open **克隆音色** (Clone voice), confirm voice authorization, enter a name, select the sample language, and record or upload clear speech from a single speaker.
4. Start cloning. The entry appears under **我的音色** (My voices); wait for **可使用** (Ready). Reopen the app to resume status checks after an interruption.
5. Tap a ready voice, or select one in **文字转语音** (Text to speech). Enter text, generate speech, and use the playback or download control.

### Models, languages, and billing

- Cloning and synthesis currently use `cosyvoice-v3-flash`. A cloned voice is bound to its target model and cannot be switched to an arbitrary model.
- The interface offers Chinese, English, Japanese, Korean, French, German, Spanish, Russian, and Cantonese. Actual support depends on the model and voice. Selecting a language does not translate text or guarantee that every combination works. Start with the same language as the reference sample.
- The app does not provide API keys or collect provider fees. Actual cloning/synthesis prices, quotas, and activation requirements are determined by Alibaba Cloud. Users pay their own provider charges.
- Provider reference: [voice cloning HTTP API](https://help.aliyun.com/zh/model-studio/voice-clone-design-http-api).

## Privacy and voice authorization

- Clone only your own voice or voices for which you have explicit permission.
- Starting a clone uploads the selected audio to Alibaba Cloud. Starting synthesis sends text, the voice ID, and synthesis parameters to Alibaba Cloud. Requests authenticate with the key supplied by the user.
- API keys and voice profiles are saved in DataStore in the app-private directory. Recordings, imported samples, and synthesis results use local cache files; exports are written to the location chosen by the user.
- **Keys are not yet encrypted with Android Keystore, and backup rules do not yet specifically exclude credentials.** Use a restricted test key and assess this limitation before using shared devices or distributing a production release.
- This repository contains no usable keys, account configuration, signing files, test recordings, or generated audio. Do not commit these files to Git.

## Build

These are the versions configured in this repository, not general minimum-version recommendations:

| Component | Current configuration |
| --- | --- |
| Android Gradle Plugin | 9.4.0 |
| Gradle Wrapper | 9.8.0 |
| Gradle daemon JDK | 25 |
| Compose compiler plugin | 2.2.10 |
| Compose BOM | 2026.02.01 |
| compileSdk / targetSdk | 37 / 37 |
| minSdk | 24 (Android 7.0) |

Use an Android Studio version compatible with this toolchain, install Android SDK Platform 37, and configure JDK 25. The Gradle daemon Java version is declared in `gradle/gradle-daemon-jvm.properties`. Java source compatibility remains 11; that is separate from the JDK used to run Gradle.

Android Studio generates your local `local.properties`. Alternatively, create it with your SDK location:

```properties
# Example only: replace with your SDK path. Do not commit this file.
sdk.dir=/path/to/Android/Sdk
```

If Gradle cannot locate JDK 25, configure `org.gradle.java.installations.paths` in your **user-level** `~/.gradle/gradle.properties`. Windows example: `org.gradle.java.installations.paths=C\:/path/to/jdk-25`. Do not put machine-specific absolute paths in the repository.

Windows PowerShell:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest lintDebug
```

macOS / Linux:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug
```

The first build needs network access to download Gradle, plugins, and dependencies; JDK auto-resolution may also require network access. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Install it through Android Studio or with `adb install -r <APK-path>`.

## Tests

- `RegressionTest` covers draft restoration, missing-key prompts, credential input, back navigation, cache removal, actual media duration, voice persistence and menu updates, and audio pause/replay.
- Verified with real requests: upload an approximately 18.9-second official sample → create a voice → review status `OK` → synthesize new Chinese text using that cloned voice → play, pause, resume, and replay.
- `CloudSmokeTest` is **skipped by default**. To reproduce it manually, configure a key in the app, upload and clone a voice named `UploadTest1120`, wait for approval, then explicitly run:

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class com.example.myapplication.CloudSmokeTest -e runCloudSmoke true com.example.myapplication.test/androidx.test.runner.AndroidJUnitRunner
```

This test makes a real synthesis request and may incur charges. Gradle device tests may reinstall or uninstall the target app. To preserve configured data, build the test APK, install it with `adb install -r`, and run it with `adb shell am instrument`. Regular regression tests also clear the app cache; use a dedicated test device.

## Source layout

```text
app/src/main/java/com/example/myapplication/
├── MainActivity.kt
├── data/       # Provider/voice models and DataStore persistence
├── service/    # CosyVoice HTTP calls, sample validation, playback
└── ui/         # Cloning, synthesis, settings, providers, and theme
```

## Known limitations

- Coverage across device manufacturers, unreliable networks, background transitions, and process termination is incomplete. Restoring a form does not guarantee that a cloud task continues in every situation.
- Pending voice status is polled while the app runs; no background scheduler is implemented. Removing a local voice entry does not delete the provider's cloud voice.
- Full synthesis history, account synchronization, online updates, and an English interface are not implemented. The update action explains that no online update service is connected.
- Credential encryption, backup policy, versioning, and release signing need further work before a production release. The current application ID is `com.example.myapplication` and its version is `1.0`.
- Not every combination of language, preset voice, and model has been validated. Refer to current provider documentation for pricing and capabilities.

Report problems through [Issues](https://github.com/YingLi-kid/FL/issues), including device model, Android version, reproduction steps, and redacted errors. Do not attach API keys or recordings without permission.

## License

[GNU General Public License v3.0](LICENSE). Follow its terms when modifying or distributing the project.

Copyright © 2026 影篱 YingLi
