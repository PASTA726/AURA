use std::sync::atomic::Ordering;

use aura_agent::{AgentDeps, AgentLoop};
use aura_core::{AuraConfig, AuraError};
use aura_inference::InferenceManager;
use aura_memory::MemoryDb;
use aura_protocol::AgentStatus;
use aura_tools::{DateTimeTool, DeviceInfoTool, FileSearchTool, ToolRegistry};

/// Run one agent request against the runtime's components.
async fn execute_cycle(
    agent_loop: &mut AgentLoop,
    inference_manager: &InferenceManager,
    tool_registry: &ToolRegistry,
    memory: Option<&MemoryDb>,
    input: String,
) -> Result<String, AuraError> {
    let deps = AgentDeps {
        inference: inference_manager,
        tools: tool_registry,
        memory,
    };
    agent_loop.process_input(input, &deps).await
}

/// Top-level coordinator that owns the agent loop, tool registry, inference
/// manager, and the SQLite-backed persistent memory.
pub struct AuraRuntime {
    pub agent_loop: AgentLoop,
    pub tool_registry: ToolRegistry,
    pub inference_manager: InferenceManager,
    pub memory: Option<MemoryDb>,
    pub config: AuraConfig,
    pub tokio_rt: Option<tokio::runtime::Runtime>,
}

impl AuraRuntime {
    /// Build the runtime with default config and an empty in-memory registry.
    pub fn new() -> Result<Self, AuraError> {
        Self::build(None, None)
    }

    /// Build the runtime with persistent SQLite memory at `db_path`.
    pub fn with_storage(db_path: &str) -> Result<Self, AuraError> {
        Self::build(Some(db_path), None)
    }

    /// Build the runtime with a custom config and in-process memory only.
    pub fn with_config(config: AuraConfig) -> Result<Self, AuraError> {
        Self::build(None, Some(config))
    }

    /// Build the runtime.
    ///
    /// `memory_path == None` runs memory purely in-process (useful for tests and
    /// privacy-critical setups where nothing should touch permanent storage).
    fn build(db_path: Option<&str>, config: Option<AuraConfig>) -> Result<Self, AuraError> {
        let config = config.unwrap_or_default();
        let memory = match db_path {
            Some(path) => Some(MemoryDb::new(path)?),
            None => Some(MemoryDb::new_in_memory()?),
        };

        let mut agent_loop = AgentLoop::new(config.clone());

        // Restore the most recent conversation so chat history survives restarts.
        if let Some(db) = &memory {
            if let Ok(Some(conversation_id)) = db.latest_conversation() {
                if let Ok(messages) = db.get_conversation_messages(conversation_id) {
                    tracing::info!(
                        messages = messages.len(),
                        "Restored conversation history from storage"
                    );
                    agent_loop.restore_history(conversation_id, messages);
                }
            }
        }

        let mut tool_registry = ToolRegistry::new();
        tool_registry.register(DateTimeTool);
        tool_registry.register(DeviceInfoTool);
        // File search is registered with no roots by default; runtimes grant
        // permitted storage roots explicitly via [set_search_roots].
        tool_registry.register(FileSearchTool::new(Vec::new()));

        let inference_manager = InferenceManager::new();
        let tokio_rt = if tokio::runtime::Handle::try_current().is_ok() {
            None
        } else {
            tokio::runtime::Builder::new_multi_thread()
                .worker_threads(2)
                .enable_all()
                .build()
                .ok()
        };

        Ok(Self {
            agent_loop,
            tool_registry,
            inference_manager,
            memory,
            config,
            tokio_rt,
        })
    }

    /// Grant the file search tool the storage roots it may look into.
    pub fn set_search_roots(&mut self, roots: Vec<std::path::PathBuf>) {
        // Re-register to replace the previous instance.
        self.tool_registry.register(FileSearchTool::new(roots));
    }

    /// Record that the user granted a tool permission, enabling tools whose
    /// schema requires it.
    pub fn grant_permission(&mut self, permission: &str) {
        self.agent_loop
            .granted_permissions
            .insert(permission.to_string());
        if let Some(memory) = &self.memory {
            let _ = memory.log_audit("permission_granted", permission);
        }
    }

    pub fn revoke_permission(&mut self, permission: &str) {
        self.agent_loop.granted_permissions.remove(permission);
        if let Some(memory) = &self.memory {
            let _ = memory.log_audit("permission_revoked", permission);
        }
    }

    pub fn process_message(&mut self, input: &str) -> Result<String, AuraError> {
        let input_str = input.to_string();
        // Destructure so each field is borrowed independently (needed for
        // block_on + &mut agent_loop within the same call frame).
        let AuraRuntime {
            agent_loop,
            inference_manager,
            tool_registry,
            memory,
            tokio_rt,
            ..
        } = self;

        if let Ok(handle) = tokio::runtime::Handle::try_current() {
            tokio::task::block_in_place(|| {
                handle.block_on(execute_cycle(
                    agent_loop,
                    inference_manager,
                    tool_registry,
                    memory.as_ref(),
                    input_str,
                ))
            })
        } else if let Some(ref rt) = tokio_rt {
            rt.block_on(execute_cycle(
                agent_loop,
                inference_manager,
                tool_registry,
                memory.as_ref(),
                input_str,
            ))
        } else {
            let rt = tokio::runtime::Builder::new_current_thread()
                .enable_all()
                .build()
                .map_err(|e| AuraError::AgentError(e.to_string()))?;
            rt.block_on(execute_cycle(
                agent_loop,
                inference_manager,
                tool_registry,
                memory.as_ref(),
                input_str,
            ))
        }
    }

    pub fn emergency_stop(&self) {
        self.agent_loop.emergency_stop();
    }

    pub fn get_status(&self) -> AgentStatus {
        if self
            .agent_loop
            .context
            .emergency_stop
            .load(Ordering::SeqCst)
        {
            // A stopped agent must not resume work without a restart.
            AgentStatus::Paused
        } else {
            AgentStatus::Idle
        }
    }

    /// Swap in an inference backend (tests, and future llama.cpp wiring).
    pub fn set_inference_backend(
        &mut self,
        backend: impl aura_inference::InferenceBackend + 'static,
    ) {
        self.inference_manager = aura_inference::InferenceManager::with_backend(backend);
    }

    /// Load a GGUF model as the active inference backend (feature `gguf`).
    /// Models are only ever loaded from an explicit user-provided path —
    /// nothing is downloaded automatically.
    #[cfg(any(feature = "gguf", feature = "gguf-android"))]
    pub async fn load_gguf(&mut self, path: &str) -> Result<aura_inference::ModelInfo, AuraError> {
        // Destructure so the two fields borrow independently of `self`.
        let AuraRuntime {
            inference_manager,
            memory,
            ..
        } = self;
        load_gguf_core(inference_manager, memory.as_mut(), path).await
    }

    /// Blocking wrapper for [AuraRuntime::load_gguf] usable from JNI.
    #[cfg(any(feature = "gguf", feature = "gguf-android"))]
    pub fn load_gguf_blocking(
        &mut self,
        path: &str,
    ) -> Result<aura_inference::ModelInfo, AuraError> {
        // Same destructuring pattern as `process_message`: the runtime must be
        // borrowed independently of the fields it drives via block_on.
        let AuraRuntime {
            inference_manager,
            memory,
            tokio_rt,
            ..
        } = self;
        let path_owned = path.to_string();
        if let Some(ref rt) = tokio_rt {
            rt.block_on(load_gguf_core(
                inference_manager,
                memory.as_mut(),
                &path_owned,
            ))
        } else {
            let rt = tokio::runtime::Builder::new_current_thread()
                .enable_all()
                .build()
                .map_err(|e| AuraError::AgentError(e.to_string()))?;
            rt.block_on(load_gguf_core(
                inference_manager,
                memory.as_mut(),
                &path_owned,
            ))
        }
    }

    /// SQLite-backed memory accessor for UI screens (inspect / search / wipe).
    pub fn memory(&self) -> Option<&MemoryDb> {
        self.memory.as_ref()
    }

    /// Rendered chat history of the latest conversation as JSON for the UI:
    /// `{"conversationCount":N,"messages":[{"role","content","timestamp"}]}`.
    /// Timestamps are RFC-3339; the Kotlin layer formats them for display.
    pub fn history_json(&self) -> String {
        let mut messages = Vec::new();
        if let Some(mem) = &self.memory {
            if let Ok(Some(cid)) = mem.latest_conversation() {
                if let Ok(msgs) = mem.get_conversation_messages(cid) {
                    for m in msgs {
                        messages.push(serde_json::json!({
                            "role": format!("{:?}", m.role).to_lowercase(),
                            "content": m.content,
                            "timestamp": m.timestamp.to_rfc3339(),
                        }));
                    }
                }
            }
        }
        let conversation_count = self
            .memory
            .as_ref()
            .and_then(|m| m.count_conversations().ok())
            .unwrap_or(0);
        serde_json::json!({
            "conversationCount": conversation_count,
            "messages": messages,
        })
        .to_string()
    }

    /// Delete all chat history (memories + audit preserved). Returns nothing
    /// meaningful on failure except the JSON error wrapper count; see JNI.
    pub fn clear_history(&mut self) -> Result<(), AuraError> {
        if let Some(mem) = &self.memory {
            mem.wipe_messages()?;
        }
        self.start_fresh_agent_loop();
        Ok(())
    }

    /// Replace the agent loop with a fresh one (same config, permissions and
    /// inference settings) so chat truly starts over after history wipe.
    fn start_fresh_agent_loop(&mut self) {
        let mut fresh = aura_agent::AgentLoop::new(self.agent_loop.config.clone());
        fresh.inference_config = self.agent_loop.inference_config.clone();
        fresh.granted_permissions = self.agent_loop.granted_permissions.clone();
        self.agent_loop = fresh;
    }

    /// Engine status for the UI as JSON:
    /// `{"agentStatus":"idle|paused","modelLoaded":bool,"maxIterations":N,
    ///   "conversationCount":N}`.
    pub fn status_json(&self) -> String {
        let agent_status = match self.get_status() {
            AgentStatus::Idle => "idle",
            AgentStatus::Paused => "paused",
            _ => {
                // Thinking / Planning / ExecutingTool / WaitingForUser / Error /
                // Completed are transient single-request states; the UI reads
                // live state via the chat screen, status JSON reports the rest.
                "working"
            }
        };
        let model_loaded = self.inference_manager.is_loaded();
        let conversation_count = self
            .memory
            .as_ref()
            .and_then(|m| m.count_conversations().ok())
            .unwrap_or(0);
        serde_json::json!({
            "agentStatus": agent_status,
            "modelLoaded": model_loaded,
            "maxIterations": self.agent_loop.context.max_iterations,
            "conversationCount": conversation_count,
        })
        .to_string()
    }

    /// Long-term memory entries for the Memory screen as JSON array:
    /// `[{"id","content","entryType","timestamp","tags"}]` (newest first).
    pub fn memories_json(&self, limit: u32) -> String {
        use aura_core::MemoryEntryType;
        let entries = self
            .memory
            .as_ref()
            .and_then(|m| m.list_memories(limit).ok())
            .unwrap_or_default();
        let list: Vec<serde_json::Value> = entries
            .iter()
            .map(|e| {
                let t = match e.entry_type {
                    MemoryEntryType::Preference => "preference",
                    MemoryEntryType::LongTerm => "long_term",
                    MemoryEntryType::Conversation => "conversation",
                    MemoryEntryType::TaskHistory => "task_history",
                    MemoryEntryType::TempContext => "temp_context",
                };
                serde_json::json!({
                    "id": e.id.to_string(),
                    "content": e.content,
                    "entryType": t,
                    "timestamp": e.created_at.to_rfc3339(),
                    "tags": e.tags,
                })
            })
            .collect();
        serde_json::Value::Array(list).to_string()
    }

    /// Persist a long-term memory the user typed on the Memory screen.
    pub fn save_memory(&self, content: &str) -> Result<(), AuraError> {
        use aura_core::{MemoryEntry, MemoryEntryType};
        let entry = MemoryEntry::new(content.to_string(), MemoryEntryType::LongTerm);
        if let Some(mem) = &self.memory {
            mem.save_entry_sync(&entry)?;
        }
        Ok(())
    }

    /// Latest audit-log records as JSON array:
    /// `[{"timestamp","event","detail"}]` (newest first).
    pub fn audit_json(&self, limit: u32) -> String {
        let records = self
            .memory
            .as_ref()
            .and_then(|m| m.recent_audit(limit).ok())
            .unwrap_or_default();
        let list: Vec<serde_json::Value> = records
            .iter()
            .map(|r| {
                serde_json::json!({
                    "timestamp": r.timestamp.to_rfc3339(),
                    "event": r.event_type,
                    "detail": r.description,
                })
            })
            .collect();
        serde_json::Value::Array(list).to_string()
    }

    /// Clamp the agent's planning-iteration budget (Settings slider).
    pub fn set_max_iterations(&mut self, n: u32) {
        let n = n.clamp(1, 64);
        self.agent_loop.config.max_iterations = n;
        self.agent_loop.context.max_iterations = n;
    }
}

/// GGUF load pipeline shared by the async API and the JNI blocking wrapper:
/// create backend → load model file → swap it into the manager → audit log.
#[cfg(any(feature = "gguf", feature = "gguf-android"))]
async fn load_gguf_core(
    inference_manager: &mut aura_inference::InferenceManager,
    memory: Option<&mut MemoryDb>,
    path: &str,
) -> Result<aura_inference::ModelInfo, AuraError> {
    use aura_inference::InferenceBackend;

    let mut backend = aura_inference::GgufBackend::new()?;
    let info = backend.load_model(path).await?;
    *inference_manager = aura_inference::InferenceManager::with_backend(backend);
    if let Some(mem) = memory {
        let _ = mem.log_audit("model_loaded", path);
    }
    Ok(info)
}

impl Default for AuraRuntime {
    fn default() -> Self {
        Self::new().expect("Failed to initialize default AuraRuntime")
    }
}

// ─── JNI bridge (Android feature) ─────────────────────────────────────────

#[cfg(feature = "android")]
pub mod jni_bridge {
    use super::*;
    use jni::objects::{JClass, JString};
    use jni::sys::{jlong, jstring};
    use jni::JNIEnv;

    #[no_mangle]
    pub extern "system" fn Java_app_aura_core_AuraEngine_nativeInit(
        _env: JNIEnv,
        _class: JClass,
    ) -> jlong {
        match AuraRuntime::new() {
            Ok(runtime) => Box::into_raw(Box::new(runtime)) as jlong,
            Err(_) => 0,
        }
    }

    /// Initialize with a persistent SQLite database in the app's private
    /// storage directory so conversations survive process restarts.
    #[no_mangle]
    pub extern "system" fn Java_app_aura_core_AuraEngine_nativeInitWithPath(
        mut env: JNIEnv,
        _class: JClass,
        jpath: JString,
    ) -> jlong {
        let path: String = match env.get_string(&jpath) {
            Ok(s) => s.into(),
            Err(_) => return 0,
        };
        let db_path = format!("{path}/aura-memory.db");
        match AuraRuntime::with_storage(&db_path) {
            Ok(runtime) => Box::into_raw(Box::new(runtime)) as jlong,
            Err(e) => {
                tracing::error!(db = %db_path, "Runtime init failed: {e}");
                0
            }
        }
    }

    #[no_mangle]
    pub extern "system" fn Java_app_aura_core_AuraEngine_nativeProcessMessage(
        mut env: JNIEnv,
        _class: JClass,
        ptr: jlong,
        input: JString,
    ) -> jstring {
        if ptr == 0 {
            let output = env.new_string("Error: Engine not initialized").unwrap();
            return output.into_raw();
        }

        let runtime = unsafe { &mut *(ptr as *mut AuraRuntime) };
        let input_rust: String = match env.get_string(&input) {
            Ok(s) => s.into(),
            Err(_) => {
                let output = env.new_string("Error: Invalid string").unwrap();
                return output.into_raw();
            }
        };

        match runtime.process_message(&input_rust) {
            Ok(reply) => env.new_string(reply).unwrap().into_raw(),
            Err(e) => env.new_string(format!("Error: {e}")).unwrap().into_raw(),
        }
    }

    /// Load a GGUF model file into the inference manager (blocking).
    /// Returns a JSON result the Kotlin side can surface directly:
    /// `{"ok":true,“name”:…}` or `{"ok":false,"error":…}`.
    #[cfg(any(feature = "gguf", feature = "gguf-android"))]
    #[no_mangle]
    pub extern "system" fn Java_app_aura_core_AuraEngine_nativeLoadGguf(
        mut env: JNIEnv,
        _class: JClass,
        ptr: jlong,
        jpath: JString,
    ) -> jstring {
        if ptr == 0 {
            let output = env
                .new_string(r#"{"ok":false,"error":"engine not initialized"}"#)
                .unwrap();
            return output.into_raw();
        }
        let runtime = unsafe { &mut *(ptr as *mut AuraRuntime) };
        let path: String = match env.get_string(&jpath) {
            Ok(s) => s.into(),
            Err(_) => {
                let output = env
                    .new_string(r#"{"ok":false,"error":"invalid path"}"#)
                    .unwrap();
                return output.into_raw();
            }
        };

        let reply = match runtime.load_gguf_blocking(&path) {
            Ok(info) => serde_json::json!({
                "ok": true,
                "name": info.name,
                "contextLength": info.context_length,
                "quantization": info.quantization,
                "sizeBytes": info.size_bytes,
            }),
            Err(e) => serde_json::json!({ "ok": false, "error": e.to_string() }),
        };
        env.new_string(reply.to_string()).unwrap().into_raw()
    }

    #[no_mangle]
    pub extern "system" fn Java_app_aura_core_AuraEngine_nativeEmergencyStop(
        _env: JNIEnv,
        _class: JClass,
        ptr: jlong,
    ) {
        if ptr != 0 {
            let runtime = unsafe { &*(ptr as *const AuraRuntime) };
            runtime.emergency_stop();
        }
    }

    #[no_mangle]
    pub extern "system" fn Java_app_aura_core_AuraEngine_nativeDestroy(
        _env: JNIEnv,
        _class: JClass,
        ptr: jlong,
    ) {
        if ptr != 0 {
            let _ = unsafe { Box::from_raw(ptr as *mut AuraRuntime) };
        }
    }

    /// Chat history of the latest conversation (JSON) for the Chat screen.
    #[no_mangle]
    pub extern "system" fn Java_app_aura_core_AuraEngine_nativeGetHistory(
        env: JNIEnv,
        _class: JClass,
        ptr: jlong,
    ) -> jstring {
        let payload: serde_json::Value = if ptr == 0 {
            serde_json::json!({
                "conversationCount": 0,
                "messages": [],
                "error": "engine not initialized"
            })
        } else {
            let runtime = unsafe { &*(ptr as *const AuraRuntime) };
            serde_json::from_str(&runtime.history_json()).unwrap_or(serde_json::Value::Null)
        };
        env.new_string(payload.to_string()).unwrap().into_raw()
    }

    /// Start a new conversation: wipes chat history and resets the agent loop.
    /// Memories and the audit log are preserved.
    #[no_mangle]
    pub extern "system" fn Java_app_aura_core_AuraEngine_nativeNewConversation(
        env: JNIEnv,
        _class: JClass,
        ptr: jlong,
    ) -> jstring {
        let payload: serde_json::Value = if ptr == 0 {
            serde_json::json!({ "ok": false, "error": "engine not initialized" })
        } else {
            let runtime = unsafe { &mut *(ptr as *mut AuraRuntime) };
            match runtime.clear_history() {
                Ok(()) => serde_json::json!({ "ok": true }),
                Err(e) => serde_json::json!({ "ok": false, "error": e.to_string() }),
            }
        };
        env.new_string(payload.to_string()).unwrap().into_raw()
    }

    /// Engine status (JSON) for the Home and Settings screens.
    #[no_mangle]
    pub extern "system" fn Java_app_aura_core_AuraEngine_nativeGetStatus(
        env: JNIEnv,
        _class: JClass,
        ptr: jlong,
    ) -> jstring {
        let payload: serde_json::Value = if ptr == 0 {
            serde_json::json!({
                "agentStatus": "paused",
                "modelLoaded": false,
                "maxIterations": 0,
                "conversationCount": 0,
                "error": "engine not initialized"
            })
        } else {
            let runtime = unsafe { &*(ptr as *const AuraRuntime) };
            serde_json::from_str(&runtime.status_json()).unwrap_or(serde_json::Value::Null)
        };
        env.new_string(payload.to_string()).unwrap().into_raw()
    }

    /// Long-term memory entries (JSON array, newest first).
    #[no_mangle]
    pub extern "system" fn Java_app_aura_core_AuraEngine_nativeGetMemories(
        env: JNIEnv,
        _class: JClass,
        ptr: jlong,
        jlimit: jni::sys::jint,
    ) -> jstring {
        let payload: serde_json::Value = if ptr == 0 {
            serde_json::json!([])
        } else {
            let runtime = unsafe { &*(ptr as *const AuraRuntime) };
            serde_json::from_str(&runtime.memories_json(jlimit.max(1) as u32))
                .unwrap_or(serde_json::Value::Null)
        };
        env.new_string(payload.to_string()).unwrap().into_raw()
    }

    /// Save a long-term memory entry typed by the user.
    #[no_mangle]
    pub extern "system" fn Java_app_aura_core_AuraEngine_nativeSaveMemory(
        mut env: JNIEnv,
        _class: JClass,
        ptr: jlong,
        jcontent: JString,
    ) -> jstring {
        let content: String = match env.get_string(&jcontent) {
            Ok(s) => s.into(),
            Err(_) => {
                let output = env
                    .new_string(r#"{"ok":false,"error":"invalid content"}"#)
                    .unwrap();
                return output.into_raw();
            }
        };
        let payload: serde_json::Value = if ptr == 0 {
            serde_json::json!({ "ok": false, "error": "engine not initialized" })
        } else {
            let runtime = unsafe { &*(ptr as *const AuraRuntime) };
            match runtime.save_memory(&content) {
                Ok(()) => serde_json::json!({ "ok": true }),
                Err(e) => serde_json::json!({ "ok": false, "error": e.to_string() }),
            }
        };
        env.new_string(payload.to_string()).unwrap().into_raw()
    }

    /// Delete all long-term memory entries.
    #[no_mangle]
    pub extern "system" fn Java_app_aura_core_AuraEngine_nativeWipeMemories(
        env: JNIEnv,
        _class: JClass,
        ptr: jlong,
    ) -> jstring {
        let payload: serde_json::Value = if ptr == 0 {
            serde_json::json!({ "ok": false, "error": "engine not initialized" })
        } else {
            let runtime = unsafe { &*(ptr as *mut AuraRuntime) };
            match runtime
                .memory()
                .ok_or_else(|| AuraError::MemoryError("no memory store".into()))
                .and_then(|m| m.wipe_memories())
            {
                Ok(()) => serde_json::json!({ "ok": true }),
                Err(e) => serde_json::json!({ "ok": false, "error": e.to_string() }),
            }
        };
        env.new_string(payload.to_string()).unwrap().into_raw()
    }

    /// Latest audit-log records (JSON array, newest first) for the Activity
    /// screen — real tool actions/model loads/permission changes.
    #[no_mangle]
    pub extern "system" fn Java_app_aura_core_AuraEngine_nativeGetAudit(
        env: JNIEnv,
        _class: JClass,
        ptr: jlong,
        jlimit: jni::sys::jint,
    ) -> jstring {
        let payload: serde_json::Value = if ptr == 0 {
            serde_json::json!([])
        } else {
            let runtime = unsafe { &*(ptr as *const AuraRuntime) };
            serde_json::from_str(&runtime.audit_json(jlimit.max(1) as u32))
                .unwrap_or(serde_json::Value::Null)
        };
        env.new_string(payload.to_string()).unwrap().into_raw()
    }

    /// Clamp & set the agent's planning-iteration budget (Settings slider).
    #[no_mangle]
    pub extern "system" fn Java_app_aura_core_AuraEngine_nativeSetMaxIterations(
        _env: JNIEnv,
        _class: JClass,
        ptr: jlong,
        value: jni::sys::jint,
    ) {
        if ptr != 0 {
            let runtime = unsafe { &mut *(ptr as *mut AuraRuntime) };
            runtime.set_max_iterations(value.max(1) as u32);
        }
    }
}
