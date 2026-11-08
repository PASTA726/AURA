<div align="center">
  <img src="AURA_Android_Icons/play_store_icon_512.png" alt="AURA app icon" width="148" />
  <h1>AURA</h1>
  <p><strong>A private AI agent for Android</strong><br />Local models. Useful tools. Your data stays on your device.</p>
  <p>
    <a href="https://github.com/PASTA726/AURA/releases/tag/v0.1.6-alpha"><img src="https://img.shields.io/badge/alpha-v0.1.6-00D4FF?style=for-the-badge" alt="Latest alpha release v0.1.6" /></a>
    <img src="https://img.shields.io/badge/platform-Android%208%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android 8 and newer" />
    <img src="https://img.shields.io/badge/architecture-ARM64-7C3AED?style=for-the-badge" alt="ARM64" />
    <img src="https://img.shields.io/badge/Rust-core-000000?style=for-the-badge&logo=rust&logoColor=white" alt="Rust core" />
  </p>
  <p>
    <a href="https://github.com/PASTA726/AURA/releases/download/v0.1.6-alpha/AURA-v0.1.6-alpha-arm64.apk"><strong>↓ Download the latest APK</strong></a>
    · <a href="#build-from-source">Build from source</a>
    · <a href="docs/ROADMAP.md">Roadmap</a>
  </p>
</div>

---

> **Alpha build:** the downloadable APK is signed with the Android debug certificate and installs as `app.aura.debug`. It runs alongside a regular `app.aura` install. A GGUF model is not bundled; choose one from Settings after installing.

## What AURA does

AURA is an Android agent built around a Rust runtime and a native Jetpack Compose interface. It can reason through bounded steps, call registered tools, keep chat history and user-approved memories in a local database, and run a GGUF language model on-device.

| Chat | Memory | Activity | On-device inference |
| --- | --- | --- | --- |
| Talk to the agent and stop a running response. | Save, search and remove local memories. | Review tool and model events. | Import a GGUF file from your device. |

The app includes Home, Chat, Activity, Memory and Settings screens. The interface shows when no model is loaded; AURA does not download a model for you.

## Privacy by design

- The Android app has no `INTERNET` permission. Inference and stored data remain on-device.
- Conversations, memories and audit records use SQLite in app-private storage; Android backup is disabled.
- You choose the model file with Android’s document picker. AURA copies it into private storage.
- Tool calls have schema checks and time limits. File search is limited to configured roots; no roots are granted by default.

Read the [security notes](docs/SECURITY.md) and [architecture overview](docs/ARCHITECTURE.md).

## Get the alpha

Download **[AURA v0.1.6-alpha for ARM64](https://github.com/PASTA726/AURA/releases/download/v0.1.6-alpha/AURA-v0.1.6-alpha-arm64.apk)** from the [GitHub release page](https://github.com/PASTA726/AURA/releases/tag/v0.1.6-alpha).

The app supports Android 8.0+ (API 26) on 64-bit ARM and targets API 36. After installing, open **Settings → Load GGUF model from device** and pick a model you already have. Model size and generation speed depend on the device.

### v0.1.6-alpha changes

- Chat stays locked until an emergency stop has returned, so another request cannot queue behind it.
- Starting a new conversation asks before clearing chat history and displays reset errors.
- Memory deletion displays failures; memory saving and model import wait for engine initialization.
- Refreshed launcher icon across adaptive, round, density and store assets.
- Shares llama.cpp's process-wide backend across model reloads, budgets replies to the context left after the prompt, and serializes context generation per model.

## Build from source

### Requirements

- Android SDK and Build Tools 36.0.0
- Android NDK r27c (`27.2.12479018`)
- Rust 1.95+ with the `aarch64-linux-android` target
- OpenJDK 17

Build the Rust runtime for ARM64:

```bash
export PATH="$HOME/Android/Sdk/ndk/27.2.12479018/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH"
cargo build --package aura-runtime --target aarch64-linux-android --features android
cp target/aarch64-linux-android/debug/libaura_runtime.so android/app/src/main/jniLibs/arm64-v8a/
```

Build the Android app:

```bash
cd android
./gradlew assembleDebug
```

The APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`.

## Project layout

```text
android/app/       Jetpack Compose UI, JNI bridge and Android resources
crates/            Rust agent, inference, tools, memory and runtime modules
docs/              Architecture, security, UI design and roadmap
AURA_Android_Icons/ Launcher and store icon assets
```

The Rust workspace contains nine crates: `aura-core`, `aura-protocol`, `aura-agent`, `aura-planner`, `aura-inference`, `aura-tools`, `aura-vision`, `aura-memory` and `aura-runtime`.

## Project status

AURA is an early alpha. Debug and release Gradle builds pass. Device-specific inference speed and memory use have not been measured, and a connected-device UI walkthrough is still needed. See the [roadmap](docs/ROADMAP.md) for planned work.
