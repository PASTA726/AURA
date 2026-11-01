use aura_core::{Message, MessageRole};
use aura_memory::MemoryDb;
use aura_planner::Planner;
use aura_runtime::AuraRuntime;
use aura_tools::{DateTimeTool, DeviceInfoTool, Tool};

#[test]
fn test_runtime_initialization_and_status() {
    let mut runtime = AuraRuntime::new().expect("Failed to create AuraRuntime");
    assert_eq!(runtime.get_status(), aura_protocol::AgentStatus::Idle);

    let reply = runtime
        .process_message("Hello AURA")
        .expect("Process message failed");
    assert!(!reply.is_empty());
}

#[tokio::test]
async fn test_tools_execution() {
    let dt_tool = DateTimeTool;
    let res = dt_tool
        .execute(serde_json::json!({}))
        .await
        .expect("DateTimeTool failed");
    assert!(res.get("utc").is_some());

    let dev_tool = DeviceInfoTool;
    let dev_res = dev_tool
        .execute(serde_json::json!({}))
        .await
        .expect("DeviceInfoTool failed");
    assert_eq!(
        dev_res.get("platform").and_then(|v| v.as_str()),
        Some("android")
    );
}

#[test]
fn test_memory_db_in_memory() {
    let db = MemoryDb::new_in_memory().expect("Failed to create in-memory db");
    let conv_id = uuid::Uuid::new_v4();
    let msg = Message::new(MessageRole::User, "Test message content");

    db.save_message(conv_id, &msg)
        .expect("Failed to save message");
    let retrieved = db
        .get_conversation_messages(conv_id)
        .expect("Failed to get messages");
    assert_eq!(retrieved.len(), 1);
    assert_eq!(retrieved[0].content, "Test message content");
}

#[test]
fn test_planner_decomposition() {
    let planner = Planner::new();
    let plan = planner.create_plan("Summarize local files and report");
    assert!(plan.step_count() >= 3);
}

#[test]
fn test_emergency_stop() {
    let runtime = AuraRuntime::new().expect("Failed to create AuraRuntime");
    runtime.emergency_stop();
    assert_eq!(runtime.get_status(), aura_protocol::AgentStatus::Paused);
}

// ─── Phase 4/5/6 integration tests ────────────────────────────────────────

use async_trait::async_trait;
use std::collections::VecDeque;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};

/// Scripted backend replacing the placeholder so the full agent pipeline
/// (inference → action parse → tool → observation → answer) is exercised.
struct ScriptedBackend {
    script: Mutex<VecDeque<String>>,
    stop_flag: Arc<AtomicBool>,
    fire_stop_on_call: usize,
    calls: Mutex<usize>,
}

impl ScriptedBackend {
    fn new(script: Vec<String>) -> Self {
        Self {
            script: Mutex::new(script.into()),
            stop_flag: Arc::new(AtomicBool::new(false)),
            fire_stop_on_call: usize::MAX,
            calls: Mutex::new(0),
        }
    }

    fn with_stop_hook(mut self, on_call: usize) -> Self {
        self.fire_stop_on_call = on_call;
        self
    }
}

#[async_trait]
impl aura_inference::InferenceBackend for ScriptedBackend {
    async fn load_model(
        &mut self,
        path: &str,
    ) -> Result<aura_inference::ModelInfo, aura_core::AuraError> {
        Ok(aura_inference::ModelInfo {
            name: "scripted".into(),
            path: path.into(),
            size_bytes: 0,
            context_length: 4096,
            quantization: "none".into(),
        })
    }
    async fn unload_model(&mut self) -> Result<(), aura_core::AuraError> {
        Ok(())
    }
    fn is_loaded(&self) -> bool {
        true
    }
    async fn generate(
        &self,
        prompt: &str,
        _config: &aura_core::InferenceConfig,
    ) -> Result<String, aura_core::AuraError> {
        assert!(
            prompt.contains("AVAILABLE TOOLS"),
            "prompt must expose tool schemas"
        );
        let mut calls = self.calls.lock().expect("lock");
        *calls += 1;
        if *calls == self.fire_stop_on_call {
            self.stop_flag.store(true, Ordering::SeqCst);
        }
        let mut script = self.script.lock().expect("lock");
        match script.pop_front() {
            Some(reply) => Ok(reply),
            None => Err(aura_core::AuraError::InferenceError(
                "script exhausted".into(),
            )),
        }
    }
}

fn respond(text: &str) -> String {
    serde_json::json!({ "action": "respond", "text": text }).to_string()
}

fn call_tool(id: &str, args: serde_json::Value) -> String {
    serde_json::json!({ "action": "call_tool", "tool": id, "args": args }).to_string()
}

#[test]
fn test_runtime_runs_real_agent_pipeline() {
    let mut runtime = AuraRuntime::new().expect("runtime");
    runtime.set_inference_backend(ScriptedBackend::new(vec![
        call_tool("datetime", serde_json::json!({})),
        call_tool("device_info", serde_json::json!({})),
        respond("the clock says now, the device has 8 cores"),
    ]));

    let reply = runtime
        .process_message("what time is it and what hardware do I have")
        .expect("pipeline should finish");
    assert!(reply.contains("clock"), "got: {reply}");
    assert_eq!(runtime.agent_loop.context.tool_calls_used, 2);
}

#[test]
fn test_runtime_denies_ungranted_permission_then_explains() {
    struct TestLauncher;
    impl aura_tools::AppLauncher for TestLauncher {
        fn launch(&self, package: &str) -> Result<serde_json::Value, aura_core::AuraError> {
            Ok(serde_json::json!({ "launched": package }))
        }
    }

    let mut runtime = AuraRuntime::new().expect("runtime");
    runtime
        .tool_registry
        .register(aura_tools::AppLauncherTool::new(TestLauncher));
    runtime.set_inference_backend(ScriptedBackend::new(vec![
        call_tool(
            "app_launcher",
            serde_json::json!({ "package_name": "com.example.app" }),
        ),
        respond("launch was denied; permission required"),
    ]));

    let reply = runtime
        .process_message("open the app")
        .expect("should recover from denial");
    assert!(reply.contains("denied"), "got: {reply}");

    // After the user grants the permission, the very same call succeeds.
    runtime.grant_permission("android.package_launcher");
    runtime.set_inference_backend(ScriptedBackend::new(vec![
        call_tool(
            "app_launcher",
            serde_json::json!({ "package_name": "com.example.app" }),
        ),
        respond("app launched"),
    ]));
    let reply = runtime
        .process_message("open the app again")
        .expect("granted call should work");
    assert!(reply.contains("launched"), "got: {reply}");
}

#[test]
fn test_conversation_persists_across_runtime_restart() {
    let dir = std::env::temp_dir().join(format!("aura-runtime-db-{}", uuid::Uuid::new_v4()));
    std::fs::create_dir_all(&dir).expect("create dir");
    let db_path = dir.join("memory.db");
    let db_path_str = db_path.to_string_lossy().to_string();

    {
        let mut runtime = AuraRuntime::with_storage(&db_path_str).expect("runtime with storage");
        runtime.set_inference_backend(ScriptedBackend::new(vec![respond(
            "final answer for the record",
        )]));
        runtime
            .process_message("remember this exchange")
            .expect("process");
        assert!(
            runtime.agent_loop.context.messages.len() >= 2,
            "messages should be in context"
        );
    }

    // Fresh runtime over the same database must restore history.
    let runtime = AuraRuntime::with_storage(&db_path_str).expect("reopened runtime");
    assert!(
        runtime.agent_loop.context.messages.len() >= 2,
        "conversation should persist across restarts"
    );

    let _ = std::fs::remove_dir_all(&dir);
}

#[test]
fn test_emergency_stop_mid_execution() {
    let mut runtime = AuraRuntime::new().expect("runtime");
    let backend = ScriptedBackend::new(vec![
        call_tool("datetime", serde_json::json!({})),
        respond("never delivered"),
    ])
    .with_stop_hook(2);
    let stop_flag = backend.stop_flag.clone();
    runtime.set_inference_backend(backend);
    runtime.agent_loop.context.emergency_stop = stop_flag;

    let err = runtime
        .process_message("dangerous multi-step task")
        .expect_err("must halt on emergency stop");
    assert!(err.to_string().contains("emergency stop"), "got: {err}");
    assert_eq!(runtime.get_status(), aura_protocol::AgentStatus::Paused);

    // A stopped runtime refuses all further work.
    let err = runtime
        .process_message("continue")
        .expect_err("stopped runtime must refuse");
    assert!(err.to_string().contains("stopped"), "got: {err}");
}
