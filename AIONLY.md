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

## 6. GitHub Repository & Alpha Releases

- **GitHub Repository**: https://github.com/PASTA726/AURA
- **Alpha Release (v0.1.0-alpha)**: https://github.com/PASTA726/AURA/releases/tag/v0.1.0-alpha
- **Alpha Release (v0.1.1-alpha)**: https://github.com/PASTA726/AURA/releases/tag/v0.1.1-alpha
- **Assets**: v0.1.0 `app-debug.apk`; v0.1.1 `AURA-v0.1.1-alpha-arm64.apk`
  (naming convention adopted: `AURA-v<ver>-alpha-arm64.apk`)
- **Branch**: `main` (clean working directory)

---

## 7. Session Log — 2026-11-01 — Core Integration (v0.1.1)

**User directive**: "Geliştir" → chosen direction: Rust core integration
(Phases 4/5/6), confirmed via question round.

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
  `.so` copied to `android/app/src/main/jniLibs/arm64-v8a/`.
- Gradle: `BUILD SUCCESSFUL in 1m 50s` → `app-debug.apk` 34.6 MB, SHA-256
  `3728d2f039e88dbacb4537000a914de43697ad94653eabf5294333b76915adb8`.
- Commits `2c27b94` + `bb02480` pushed; tag `v0.1.1-alpha` pushed; release published.

---

## 8. Session Log — 2026-11-02 — Phase 3: llama.cpp GGUF backend (v0.1.2-alpha COMPLETE)

**User directive**: "sonraki adıma geç" → Phase 3 (real on-device inference).
**Consent obtained**: small test model download approved (SmolLM2-135M-Instruct-Q4_K_M ~105 MB from bartowski HF repo).

### 8.1 Dependencies & environment
- `llama-cpp-2 v0.1.160` (+ `sampler` feature, optional) added to `aura-inference`
  behind `gguf` cargo feature (src/llama.rs, re-exported as `GgufBackend`).
- `aura-runtime` gained `gguf = ["aura-inference/gguf"]`; JNI `nativeLoadGguf`
  (blocking, JSON result), `AuraRuntime::load_gguf / load_gguf_blocking`;
  Kotlin `AuraEngine.loadGguf(path)` (safe when .so lacks the symbol).
- **NixOS host quirks discovered (critical for future builds)**:
  - cmake/ninja/clang must come from `nix-shell -p cmake ninja clang` (none in PATH otherwise).
  - Turkish locale breaks nix cc-wrapper setup-hook (`RANLİB`) → always `LC_ALL=C`.
  - Non-ASCII project path breaks the nix clang++ wrapper → **use stock g++ for
    CC/CXX; the wrapper mangles `Masaüstü` into `$Masa303274st`** (never set
    CC/CXX to nix clang for this repo on this machine).
  - bindgen (llama-cpp-sys-2) needs explicit:
    `LIBCLANG_PATH=/nix/store/7wxvhkgxnzarwzbc2vp1yzzwh2741xfn-clang-21.1.8-lib/lib`
    and `BINDGEN_EXTRA_CLANG_ARGS="-I<nix clang wrapper>/resource-root/include
    -isystem $(nix-build '<nixpkgs>' -A glibc.dev)/include"`.
- Test model at `models/SmolLM2-135M-Instruct-Q4_K_M.gguf` (105,454,432 bytes).
- Baseline re-verified post-refactor: `cargo test --all` 22/22,
  `cargo check -p aura-runtime --all-targets` clean (gguf feature off by default).

### 8.2 Untested/remaining (honest status)
- GGUF feature compile + smoke test: in progress at time of writing (§8.1 blockers
  fixed one-by-one; final result appended in §8.3).
- Android ARM64 cross-build of llama.cpp: unexplored (needs NDK cmake toolchain
  strategy, e.g. `CMAKE_ARGS -DCMAKE_SYSTEM_NAME=Android ...`).
- Streaming token output into the UI: not implemented (trait is completion-based).

### 8.3 Final results (v0.1.2-alpha)

**Host verification (all passed):**
- `cargo check -p aura-inference --features gguf` — clean (after 3 API fixes:
  `LlamaModelParams` lives in `model::params`; `token_to_piece` returns `Vec<u8>`;
  `LlamaChatMessage::new` returns `Result`).
- Real-model smoke tests (`AURA_GGUF_MODEL=…/SmolLM2-135M-Instruct-Q4_K_M.gguf`,
  `cargo test -p aura-inference --features gguf --release`): **2/2 passed** —
  load + generate works, JSON-action protocol exercisable. Model answers are
  weak (135M model) — pipeline proof, not quality proof.
- `cargo test --all`: **22/22** (baseline unaffected); `cargo clippy --all
  --all-targets`: **0 warnings**; `cargo fmt` clean.

**Feature-matrix fix (found on first android cross-build):**
- `llama-cpp-2`'s default features include `android-shared-stdcxx` → clashed with
  our `static-stdcxx`. Resolution: `llama-cpp-2 = { default-features = false,
  features = ["sampler", "common"] }`; `llama-cpp-sys-2` optional dep with
  `default-features = false, features = ["static-stdcxx"]`, enabled by
  `gguf-android`. (sys crate default is just `common`, no stdcxx conflict.)
- `aura-runtime` fixes: `InferenceBackend` trait import for `load_model`; borrow
  conflict in `load_gguf_blocking` resolved via field destructuring +
  `load_gguf_core(inference_manager, memory, path)` shared async helper.

**ARM64 cross-build (passed):**
- `ANDROID_NDK=…/ndk/27.2.12479018 ANDROID_API_LEVEL=26
  GGML_CPU_ARM_ARCH=armv8.2-a+dotprod cargo build -p aura-runtime
  --features "android,gguf-android" --target aarch64-linux-android --release`
  (bindgen needs `LIBCLANG_PATH` + glibc include args as §8.1; llama.cpp builds
  via the sys crate's built-in NDK cmake config).
- Result: `libaura_runtime.so` 9,537,832 bytes, ELF aarch64 for Android 26,
  NEEDED = libdl/libm/libc only (**no `libc++_shared.so`** — static c++ works),
  `Java_app_aura_core_AuraEngine_nativeLoadGguf` exported.
- APK: BUILD SUCCESSFUL in 3m30s → 24,600,344 bytes,
  SHA-256 `35d1b1cf93c35c724116315e739275ff030abbe63ad1ce0f5af8291b6fb431d1`;
  contains `lib/arm64-v8a/libaura_runtime.so`.

**Release:** v0.1.2-alpha published with `AURA-v0.1.2-alpha-arm64.apk`.

**Next up (Phase 3 leftovers):**
- On-device: measure load time / tokens-per-second with a ~0.5–3B GGUF (Reeder S71).
- Token streaming JNI callbacks into Compose UI.
- Model-picker UI (SAF document picker) + first-run onboarding copy.

---

## 9. Session Log — 2026-11-02 — GUI overhaul: every screen wired to the real engine (v0.1.3)

**User report**: "GUI tamamen bozuk, hiçbir şey olması gerektiği gibi değil."
**Root cause found in code review**: the Compose screens were a cosmetic mock —
hardcoded fake chat history, fake memory cards, fake task list, a theme toggle
that changed nothing, settings sliders disconnected from the engine, and the
Phase 3 `loadGguf` path reachable from no screen.

### 9.1 Native (Rust) additions
- `aura-memory`: new readers `list_memories(limit)` + `recent_audit(limit)`
  (shared `parse_memory_rows`), explicit synchronous insert
  `save_entry_sync` (the async `MemoryStore::save` delegates to it).
- `aura-runtime`: UI-facing API — `history_json`,
  `clear_history` (wipe + fresh `AgentLoop`, keeps permissions/config),
  `status_json`, `memories_json(limit)`, `save_memory(content)`,
  `audit_json(limit)`, `set_max_iterations(1..=64)`.
- JNI bridge: `nativeGetHistory`, `nativeNewConversation`,
  `nativeGetStatus`, `nativeGetMemories`, `nativeSaveMemory`,
  `nativeWipeMemories`, `nativeGetAudit`, `nativeSetMaxIterations`
  (all verified exported in the release `.so`).

### 9.2 Kotlin/UI overhaul
- `AuraEngine`: wrapped every new JNI entry point with safe fallthroughs.
- New `EngineState` singleton: engine status snapshot, model load via SAF
  document picker (copied into `filesDir/models`, single active model kept,
  nothing auto-downloaded), persisted dark-theme + max-iterations preferences.
- `Theme`: dark AND light schemes; brand palette distributed through
  `LocalAuraPalette` CompositionLocal (screens no longer read hardcoded dark
  constants).
- `ChatScreen`: loads the REAL persisted conversation on open, real
  timestamps, engine errors shown verbatim in error color (never silent),
  "New Conversation" actually wipes history + resets the agent loop.
- `MemoryScreen`: real SQLite memories, working search/type filter,
  add-memory dialog (saved into `long_term`), wipe-memories wired, honest
  empty state.
- `ActivityScreen`: real audit log (tool executions, permission changes,
  model loads), honest empty state, refresh button.
- `SettingsScreen`: working dark-theme switch, GGUF model loader
  (OpenDocument picker → copy → load → status), max-iterations slider writes
  through to the engine.
- `HomeScreen`: real engine/model/conversation status, quick actions, honest
  "placeholder until you load a model" copy.

### 9.3 Verification
- `cargo test --all`: 22/22; clippy on touched crates: 0 warnings; fmt clean.
- ARM64 release rebuild: finished 8s (llama.cpp cached), 8 new JNI symbols in
  the ELF, still no `libc++_shared.so` NEEDED.
- Gradle assembleDebug: BUILD SUCCESSFUL; APK 24,600,344 bytes, contains the
  new `libaura_runtime.so` (9.7 MB unpacked).
- Real-device runtime behaviour still untested (no emulator/USB device
  attached) — flagged honestly: SAF picker flow, chat restore, memory add and
  theme switching need a handset session.

### 9.4 Next
- Device test session (logcat crash triage if any).
- Token streaming UI (Phase 3 leftover).
- Auto-refresh statuses on screen navigation instead of init-only.

---

## 10. Session Log — 2026-11-08 — QA fix round (v0.1.4-alpha)

**Input**: independent QA audit (DeepSeek subagent, role-played a real user) on
v0.1.3-alpha produced findings B1–B25. Handled in this round: B1, B2, B3, B4,
B5, B6, B8, B9, B12, B13, B14, B15, B16, B22, B23 (B7, B10, B11, B17, B24, B25
deferred).

### 10.1 Native (Rust)
- **B2** `InferenceBackend::generate` gained `cancel: Option<Arc<AtomicBool>>`;
  the agent loop passes its `emergency_stop` flag; `generate_sync` bails
  pre-prefill and checks **per token** in the sample loop (`is_cancelled`).
  Stopped-agent message now explains recovery (new conversation).
- **B3** chunked prefill (`PREFILL_CHUNK=512`): batches cleared and decoded in
  slices, logits only on the very last token of the last chunk. Fixes the
  `LlamaBatch(512)` InsufficientSpace crash on long agent prompts. Host GGUF
  smoke tests (incl. the long protocol prompt) pass 2/2.
- **B8** tool executions are audit-logged (`tool_executed` / `tool_failed`,
  160-char detail head) right after execution, fixing the empty Activity feed.
- **B22** placeholder backend answer now honestly says no GGUF model is loaded
  and nothing is downloaded automatically.
- clippy clean (`clamp` instead of `max/min`), fmt clean, 22/22 tests.

### 10.2 Android (Kotlin)
- **B1** `AuraEngine.emergencyStop()` is NOT `@Synchronized` anymore (it would
  queue behind `processMessage`'s monitor for the whole generation → ANR);
  the badge click runs on the IO dispatcher.
- **B4** `tool`/`system` history rows render as compact neutral chips, not
  assistant bubbles.
- **B5** startup auto-reload: `filesDir/models/*.gguf` (newest) is reloaded
  silently; a corrupt file is deleted instead of failing on every launch.
- **B6** init race fixed: `EngineState.awaitEngine()` (`CompletableDeferred`)
  gates every screen; `AuraEngine` fallback paths no longer create a
  non-persistent engine that would block persistent init.
- **B9** iteration slider commits via `onValueChangeFinished` (prefs + engine,
  IO); `refreshStatus` no longer clobbers the value mid-drag.
- **B12** Home "Start a new conversation" really resets the agent first.
- **B13** new-conversation button: result `ok` checked; disabled during
  generation; failures surfaced in the transcript.
- **B14** manifest: `INTERNET`/`FOREGROUND_SERVICE`/`READ_EXTERNAL_STORAGE`/
  `READ_MEDIA_IMAGES` removed; `allowBackup=false`.
- **B15** `versionCode 5`, `versionName 0.1.4`, buildConfig enabled, About
  shows the version.
- **B16** wipe-memories confirmation dialog.
- **B23** dead "Language" row removed; Home badge says "No model yet — load one
  in Settings"; Memory filter chips reduced to All + Long-term.

### 10.3 Verification & release
- `cargo test --all` 22/22 · clippy 0 · fmt clean · GGUF host tests 2/2
  (chunked prefill, real SmolLM2-135M).
- ARM64 `.so` rebuilt (NDK r27c, API 26, `armv8.2-a+dotprod`), APK
  assembleDebug BUILD SUCCESSFUL.
- Released as v0.1.4-alpha (asset `AURA-v0.1.4-alpha-arm64.apk`), pre-release.
- Deferred, honestly untested on device: SAF picker flow runtime behaviour,
  chat restore, theme switching, per-token cancel latency on hardware.
