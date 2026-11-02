# AURA — Autonomous Unified Reasoning Agent
### A Local-First Autonomous Android Agent
**Rust-Centered · Unix-Inspired · Native GUI**

---

## 1. Overview

**AURA** is a local-first, autonomous mobile AI agent built for Android. Inspired by OpenClaw and Hermes, AURA is designed to run entirely on-device, prioritizing privacy, deterministic tool calling, persistent memory, and transparent autonomous task execution without cloud lock-in.

- **Platform**: Android 14+ (ARM64)
- **Core Engine**: Rust 2021 Workspace (9 modular crates)
- **User Interface**: Native Jetpack Compose (Dark-first, cyan/violet visual identity)
- **Interoperability**: C-ABI JNI bridge (`libaura_runtime.so`)
- **Inference**: On-device quantized GGUF models via embedded runtime

---

## 2. System Architecture

```
AURA/
├── Cargo.toml                    # Workspace manifest
├── .cargo/config.toml            # Cross-compilation settings for Android ARM64
├── crates/
│   ├── aura-core/                # Shared types, error definitions, traits, configuration
│   ├── aura-protocol/            # Inter-module message types & schemas
│   ├── aura-agent/               # Agent loop, state machine, context manager
│   ├── aura-planner/             # Goal decomposition & step planning
│   ├── aura-inference/           # Model loading, inference manager, GGUF backend abstraction
│   ├── aura-tools/               # Extensible tool registry & built-in tools
│   ├── aura-vision/              # Screen understanding & UI element models
│   ├── aura-memory/              # SQLite persistent store, history & search
│   └── aura-runtime/             # C-ABI JNI export, Tokio runtime, central coordinator
├── android/
│   ├── app/                      # Android application module
│   │   ├── src/main/
│   │   │   ├── java/app/aura/    # Jetpack Compose UI & JNI bindings (AuraEngine)
│   │   │   ├── jniLibs/arm64-v8a/# Pre-built libaura_runtime.so
│   │   │   └── res/              # Vector icons, mipmaps, colors, themes
│   │   └── build.gradle
│   ├── build.gradle
│   └── settings.gradle
└── docs/
    ├── ARCHITECTURE.md           # Full system architecture reference
    ├── UI_DESIGN.md              # UI/UX design specification & component library
    ├── SECURITY.md               # Security posture, sandbox, permissions, and audit
    └── ROADMAP.md                # 10-phase master development roadmap
```

---

## 3. Native GUI Structure

The graphical interface is built with Jetpack Compose following the dark-first visual identity (deep graphite surfaces `#0D0D0F`, cyan accent `#00D4FF`, violet accent `#7C3AED`):

1. **Home**: Agent availability status, current model, resource telemetry, quick actions, recent task cards.
2. **Chat**: Full streaming conversation interface, message bubbles, stopping control, and direct bridge to Rust agent loop.
3. **Activity**: Real-time task execution timeline, tool call inspection, cancel/pause controls.
4. **Memory**: SQLite memory inspector, category filters, memory search, and deletion tools.
5. **Settings**: Theme, GGUF model manager, agent iteration boundaries, and security toggles.

---

## 4. Building from Source

### Prerequisites
- **Android SDK**: API 36, Build-Tools 36.0.0
- **Android NDK**: r27c (`27.2.12479018`)
- **Rust Toolchain**: 1.95+ with target `aarch64-linux-android`
- **JDK**: OpenJDK 17

### 1. Compile Rust Core (ARM64 Shared Library)
```bash
export PATH="$HOME/Android/Sdk/ndk/27.2.12479018/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH"
cargo build --package aura-runtime --target aarch64-linux-android --features android
cp target/aarch64-linux-android/debug/libaura_runtime.so android/app/src/main/jniLibs/arm64-v8a/
```

### 2. Build Android Debug APK
```bash
cd android
./gradlew assembleDebug
```
The resulting APK is generated at:  
`android/app/build/outputs/apk/debug/app-debug.apk`

---

## 5. Security & Privacy Guarantees

- **Offline-First**: Network access disabled by default; strictly opt-in per session.
- **Sandboxed Execution**: Tools run in isolated async tasks bounded by strict timeouts and schemas.
- **Kill-Switch**: Instantaneous emergency stop flag polled at every reasoning iteration.
- **No Root Required**: Built strictly atop standard Android APIs and AccessibilityServices.
- **Tamper-Evident Audit**: Append-only SQLite audit log recording all tool actions.

---

## 6. Alpha Status (v0.1.2)

Implemented and verified (22/22 cargo tests + 2/2 GGUF smoke tests, zero clippy warnings):

- **Real agent execution loop** (`aura-agent::AgentLoop`): reasoning → JSON action
  protocol (`respond` / `call_tool`) → tool execution → observation → answer, with
  hard caps on planning iterations and tool calls, plus an emergency stop checked
  at every safe checkpoint.
- **Tool registry sandboxing** (`aura-tools`): async execution under wall-clock
  timeouts, JSON-schema argument validation, permission gating
  (`android.package_launcher` for `AppLauncherTool`), `FileSearchTool` confined
  to explicitly granted roots with traversal protection.
- **Persistent memory** (`aura-memory`): conversations, messages, tool
  observations and audit records in SQLite; the Android app initializes
  `AgentEngine` with `filesDir` storage so chat history survives restarts.
- **On-device GGUF inference (llama.cpp)** — new in v0.1.2:
  - `aura-inference` embeds llama.cpp behind the `gguf` cargo feature
    (`GgufBackend`): model file loading (user-consented path, nothing auto-downloaded),
    chat-template rendering, tokenize → prefill → sampling (`temp`/`top_p`, fixed seed,
    greedy at `temp=0`), EOS + stop-sequence + max-token caps, context-budget guard.
  - `AuraEngine.loadGguf(path)` (JNI `nativeLoadGguf`) loads a GGUF file from device storage.
  - ARM64 `libaura_runtime.so` built with the NDK toolchain (`armv8.2-a+dotprod`,
    API 26), C++ runtime statically linked — no extra `libc++_shared.so` in the APK.
  - Verified on host against a real model (`SmolLM2-135M-Instruct-Q4_K_M`, 105 MB):
    load + generation + JSON-action protocol. On-device speed/memory on a specific
    phone is not yet measured; larger GGUF models run through the same path.
