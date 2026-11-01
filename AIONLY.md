# AIONLY — AURA Autonomous Agent Context & Action Log
> **DO NOT DELETE**: This file persists the technical state, architecture decisions, and action history of Project AURA for AI memory retention across sessions.

---

## 1. Environment & Toolchain State (Linux x86_64 Host -> Android aarch64 Target)

- **Root Directory**: `/home/teto/Masaüstü/AURA`
- **Host OS**: NixOS Linux (x86_64)
- **Rust Toolchain**: 
  - `rustc 1.99.1` installed via `rustup 1.29.2` (managed under `~/.nix-profile/bin`)
  - Active Target: `x86_64-unknown-linux-gnu` (host)
  - Cross Target: `aarch64-linux-android` (installed and verified)
- **Android SDK**: `/home/teto/Android/Sdk`
  - Platform: `android-36`
  - Build-tools: `36.0.0`
- **Android NDK**: `r27c` (`27.2.12479018`) at `/home/teto/Android/Sdk/ndk/27.2.12479018`
  - Clang compiler: `aarch64-linux-android26-clang`
  - Configured symlinks: `aarch64-linux-android-clang` -> `aarch64-linux-android26-clang`
  - `.cargo/config.toml` configured with linker, ar, and `CC_aarch64_linux_android`
- **Java**: OpenJDK 17.0.20.1
- **Gradle**: 8.13 wrapper

---

## 2. Master Documentation Delivered

Located in `/home/teto/Masaüstü/AURA/docs/`:
1. **`ARCHITECTURE.md`**: Complete Unix-inspired modular design, crate boundaries, data flow diagrams, C-ABI JNI layer, async event loops.
2. **`UI_DESIGN.md`**: Dark-first Jetpack Compose design system (Graphite `#0D0D0F`, Surface `#111114`, Cyan `#00D4FF`, Violet `#7C3AED`), typography, spacing, 5 screen blueprints, component specifications.
3. **`SECURITY.md`**: Threat model, least-privilege tool execution, air-gap/offline policy by default, instantaneous atomic emergency stop, sandboxing, SQLite tamper-evident audit logging.
4. **`ROADMAP.md`**: Phased execution roadmap (Phases 0 through 9) with strict acceptance criteria and deliverables.
5. **`README.md`**: Workspace overview, setup instructions, build pipelines.

---

## 3. Rust Core Architecture (9 Modular Crates)

Located in `/home/teto/Masaüstü/AURA/crates/`:
- **`aura-core`**: Base types (`Message`, `MessageRole`, `ConversationId`, `TaskId`), error hierarchy (`AuraError`), config schemas (`AuraConfig`, `InferenceConfig`), and traits (`Agent`, `Inferable`, `MemoryStore`).
- **`aura-protocol`**: Inter-module message enum (`AgentMessage`), structured lifecycle events (`ExecutionEvent`), `AgentStatus` state machine, and JSON schema tool definitions (`ToolSchema`).
- **`aura-memory`**: SQLite persistent engine with schema (`memories`, `conversations`, `messages`, `audit_log`), in-memory testing capability, and search index.
- **`aura-tools`**: Extensible registry (`ToolRegistry`), async `Tool` trait, and built-in tools (`DateTimeTool`, `DeviceInfoTool`).
- **`aura-agent`**: Agent loop (`AgentLoop`), conversation context (`AgentContext`), token iteration bounding, broadcast channel, and atomic emergency stop (`Arc<AtomicBool>`).
- **`aura-planner`**: Multi-step goal decomposition (`Planner`), discrete step tracking (`Plan`, `PlanStep`, `StepStatus`).
- **`aura-inference`**: Pluggable inference backend abstraction (`InferenceBackend`, `InferenceManager`), mock provider for Phase 0-2, ready for llama.cpp GGUF in Phase 3.
- **`aura-vision`**: Screen observation representations (`ScreenContent`, `UiElement`, `Rect`), XML accessibility parsing stub.
- **`aura-runtime`**: Main orchestrator (`AuraRuntime`), multi-threaded Tokio runtime manager, and C-ABI export layer (`Java_app_aura_core_AuraEngine_*`).

---

## 4. Android Native GUI & Integration

Located in `/home/teto/Masaüstü/AURA/android/`:
- **Package**: `app.aura`
- **Application Icons**: Extracted from `AURA_Android_Icons.zip` into `res/mipmap-*` and `res/drawable/`.
- **Navigation**: 5-tab bottom navigation bar:
  1. `HomeScreen`: Status badge, model info, offline indicator, quick actions, recent tasks, resource telemetry.
  2. `ChatScreen`: Message bubbles (user violet, agent surface-variant), top bar, input composer, stop generation button, asynchronous link to `AuraEngine.processMessage`.
  3. `ActivityScreen`: Active task indicator, execution timeline, completed/failed task cards, clear history.
  4. `MemoryScreen`: Category filters (Preferences, Long-term, Conversations, Tasks), search bar, inspection cards.
  5. `SettingsScreen`: Theme toggles, GGUF import card, iteration boundaries, network security switches.
- **JNI Bridge**:
  - Kotlin singleton: `app.aura.core.AuraEngine`
  - Native shared library: `android/app/src/main/jniLibs/arm64-v8a/libaura_runtime.so`
  - Cross-compiled ELF 64-bit ARM64 verified.

---

## 5. Verification & Test Evidence

- **Cargo Workspace Check**: `cargo check --all-targets` passed cleanly across all 9 crates.
- **Unit & Integration Tests**: `cargo test --package aura-runtime` passed 5/5 tests:
  - `test_runtime_initialization_and_status` (OK)
  - `test_tools_execution` (OK)
  - `test_memory_db_in_memory` (OK)
  - `test_planner_decomposition` (OK)
  - `test_emergency_stop` (OK)
- **ARM64 Native Compilation**:
  `cargo build --package aura-runtime --target aarch64-linux-android --features android`
  Produced: `libaura_runtime.so` (24,016,192 bytes, ELF 64-bit ARM aarch64, Android API 26+).
- **Android APK Build**:
  - Command: `./gradlew assembleDebug` (Gradle 8.13, AGP 8.10.1, Kotlin 2.1.0)
  - Status: `BUILD SUCCESSFUL in 12m 29s`
  - Output: `android/app/build/outputs/apk/debug/app-debug.apk` (24 MB)
  - SHA-256: `f199697877b75aaa9d392cf46802445b13fb17464c9b36b797786dca1762c40f`
  - Verified internal binary: contains `lib/arm64-v8a/libaura_runtime.so` and full Compose dex hierarchy.

---

## 6. GitHub Repository & Alpha Release

- **GitHub Repository**: https://github.com/PASTA726/AURA
- **Alpha Release (v0.1.0-alpha)**: https://github.com/PASTA726/AURA/releases/tag/v0.1.0-alpha
- **Attached Binary**: `app-debug.apk` (24 MB, asset name corrected from earlier notes)
- **Branch**: `main` (clean working directory)

---

## 7. Session Log — 2026-11-01 — Core Integration (v0.1.1)

**User directive**: "Geliştir" → chosen direction: Rust core integration (Phases 4/5/6), confirmed via question round.

### 7.1 Real Agent Execution Loop (`aura-agent`)
- Replaced the placeholder `process_input` with an actual reasoning/execution cycle:
  prompt building (protocol instructions + tool schemas + bounded history) →
  inference → JSON action parsing (`{"action":"respond"|"call_tool"}`) → tool run →
  observation message (role `Tool`) → loop until `respond`.
- Hard limits: `max_iterations` (config), `max_tool_calls` (config, default 10),
  3-consecutive-tool-failure circuit breaker, emergency stop checked at 3 safe
  checkpoints (pre-loop, post-generation, pre-tool-call).
- `AgentContext` gained `max_tool_calls`/`tool_calls_used`; `restore_history()`
  loads persisted conversations; planner emits `PlanningStep` events for the UI.
- Unparseable model output → surfaced verbatim as degraded response (never faked).
- Permission gate: a tool's `required_permissions` must be ⊆
  `AgentLoop.granted_permissions`, else the call is denied with an observation the
  model can see and explain.

### 7.2 Tool Registry Sandboxing (`aura-tools`)
- `ToolRegistry::execute(id, args, timeout_secs)`: JSON-schema argument validation
  (objects, required fields, property types) + `tokio::time::timeout` guard.
- New `FileSearchTool`: searches granted roots only (filename + content ≤1MB,
  depth-bounded), traversal cannot escape roots (tested).
- New `AppLauncherTool<L: AppLauncher>`: sensitive, requires
  `android.package_launcher` grant; the platform hook is injected as a trait.
- `validate_args` exported for schema validation reuse.

### 7.3 Persistent Memory (`aura-memory`, `aura-runtime`)
- `MemoryDb` additions: `count_messages`, `count_conversations`,
  `latest_conversation`, `wipe_messages`, `wipe_memories`;
  `save_message` now upserts and touches `conversations.updated_at`.
- `AuraRuntime` owns `MemoryDb`; constructors: `new()` (in-process), `with_storage(path)`
  (persists), `with_config(cfg)`; restores the latest conversation at start.
- `grant_permission` / `revoke_permission` audited to `audit_log`.
- JNI: added `nativeInitWithPath(String)`; Kotlin `AuraEngine.initialize(storagePath)`
  and MainActivity pass `filesDir` → conversations persist across app restarts.

### 7.4 Version, Docs
- Workspace version 0.1.0 → 0.1.1. ROADMAP phases 4/5/6 marked Completed. README
  gained "Alpha Status (v0.1.1)" section.

### 7.5 Verification Evidence
- `cargo test --all`: **22 passed, 0 failed** (6 aura-tools, 7 aura-agent, 9 aura-runtime incl. 5 pre-existing).
- `cargo clippy --workspace --all-targets`: **0 warnings**.
- `cargo fmt --all` applied.
- ARM64 rebuild: `cargo build --package aura-runtime --target aarch64-linux-android --features android` OK;
  `.so` copied to `android/app/src/main/jniLibs/arm64-v8a/` (31 MB debug).
- APK: `./gradlew assembleDebug` (see §7.6 result when recorded).

### 7.6 Next Steps
- Phase 3: llama.cpp GGUF backend (real on-device inference; needs a model download with user consent).
- Memory UI screens behind JNI accessors (inspect/wipe already exist in Rust).
- Release flow: commit → push → tag `v0.1.1-alpha` → attach APK (asset rename vs v0.1.0 lesson: use `AURA-v0.1.1-alpha-arm64.apk`).
