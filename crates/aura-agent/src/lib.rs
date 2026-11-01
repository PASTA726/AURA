use std::collections::HashSet;
use std::sync::{
    atomic::{AtomicBool, Ordering},
    Arc,
};

use serde::{Deserialize, Serialize};
use uuid::Uuid;

use aura_core::{AuraConfig, AuraError, InferenceConfig, Message, MessageRole};
use aura_inference::InferenceManager;
use aura_memory::MemoryDb;
use aura_planner::{Plan, PlanStatus, Planner, StepStatus};
use aura_protocol::{AgentMessage, AgentStatus, ExecutionEvent, ToolSchema};
use aura_tools::ToolRegistry;

// ─── Agent action protocol ────────────────────────────────────────────────

/// The response contract every inference backend must follow.
///
/// Backends receive conversation history, tool schemas and the user request,
/// and must answer with exactly one JSON object:
///
/// * `{"action":"respond","text":"…"}` — the model produced its final answer.
/// * `{"action":"call_tool","tool":"<tool_id>","args":{…}}` — request a tool run.
///
/// Output that cannot be parsed into one of these shapes is surfaced to the
/// user verbatim as the model's answer (degraded mode — never faked success).
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "action")]
pub enum AgentAction {
    #[serde(rename = "respond")]
    Respond { text: String },
    #[serde(rename = "call_tool")]
    CallTool {
        tool: String,
        #[serde(default)]
        args: serde_json::Value,
    },
}

/// Whether a completion is a final answer or a tool invocation.
#[derive(Debug, Clone, PartialEq)]
pub enum ParsedAction {
    Respond(String),
    CallTool {
        tool: String,
        args: serde_json::Value,
    },
}

/// Parse a backend completion into a [`ParsedAction`].
///
/// Strategy: strict JSON parse of the trimmed completion; on failure retry
/// with the segment between the first `{` and the last `}`; otherwise pass the
/// text through as the final response.
pub fn parse_action(raw: &str) -> ParsedAction {
    let candidate = raw.trim();
    let extracted = || {
        let start = candidate.find('{')?;
        let end = candidate.rfind('}')?;
        if end < start {
            return None;
        }
        serde_json::from_str::<serde_json::Value>(&candidate[start..=end]).ok()
    };
    let parsed = serde_json::from_str::<serde_json::Value>(candidate)
        .ok()
        .or_else(extracted)
        .filter(|object| !object.is_null());

    match parsed {
        Some(object) => match object.get("action").and_then(|a| a.as_str()) {
            Some("respond") => ParsedAction::Respond(
                object
                    .get("text")
                    .and_then(|t| t.as_str())
                    .unwrap_or_default()
                    .to_string(),
            ),
            Some("call_tool") => ParsedAction::CallTool {
                tool: object
                    .get("tool")
                    .and_then(|t| t.as_str())
                    .unwrap_or_default()
                    .to_string(),
                args: object.get("args").cloned().unwrap_or(serde_json::json!({})),
            },
            _ => ParsedAction::Respond(raw.to_string()),
        },
        None => ParsedAction::Respond(raw.to_string()),
    }
}

// ─── Agent context ───────────────────────────────────────────────────────

/// Holds everything the agent needs to maintain a conversation context.
pub struct AgentContext {
    pub conversation_id: Uuid,
    pub messages: Vec<Message>,
    pub current_plan: Option<Plan>,
    pub iteration_count: u32,
    pub max_iterations: u32,
    pub max_tool_calls: u32,
    pub tool_calls_used: u32,
    pub emergency_stop: Arc<AtomicBool>,
}

impl AgentContext {
    pub fn new(config: &AuraConfig) -> Self {
        Self {
            conversation_id: Uuid::new_v4(),
            messages: Vec::new(),
            current_plan: None,
            iteration_count: 0,
            max_iterations: config.max_iterations,
            max_tool_calls: config.max_tool_calls,
            tool_calls_used: 0,
            emergency_stop: Arc::new(AtomicBool::new(false)),
        }
    }

    /// Returns `true` if the agent should halt — either because an emergency stop
    /// was requested or the maximum iteration count has been reached.
    pub fn should_stop(&self) -> bool {
        self.emergency_stop.load(Ordering::SeqCst) || self.iteration_count >= self.max_iterations
    }

    pub fn increment_iteration(&mut self) {
        self.iteration_count += 1;
    }

    pub fn add_message(&mut self, msg: Message) {
        self.messages.push(msg);
    }

    /// Most recent `limit` messages, oldest first, for prompt construction.
    pub fn recent_history(&self, limit: usize) -> Vec<&Message> {
        let start = self.messages.len().saturating_sub(limit);
        self.messages[start..].iter().collect()
    }
}

// ─── Dependencies ────────────────────────────────────────────────────────

/// Borrowed dependencies for a single agent request. The loop stays decoupled
/// from how runtimes wire inference, tools and memory together.
pub struct AgentDeps<'a> {
    pub inference: &'a InferenceManager,
    pub tools: &'a ToolRegistry,
    pub memory: Option<&'a MemoryDb>,
}

/// Largest number of characters of any single message replayed into a prompt.
const MAX_PROMPT_MESSAGE_CHARS: usize = 600;

// ─── Agent loop ──────────────────────────────────────────────────────────

/// The top-level agent loop that drives a conversation:
/// `INPUT → REASONING → PLANNING → TOOL SELECTION → EXECUTION → OBSERVATION → RESULT`.
pub struct AgentLoop {
    pub context: AgentContext,
    pub event_tx: tokio::sync::broadcast::Sender<ExecutionEvent>,
    pub config: AuraConfig,
    pub inference_config: InferenceConfig,
    /// Permissions the user has granted. A tool runs only when every permission
    /// in its schema is present here.
    pub granted_permissions: HashSet<String>,
    planner: Planner,
    /// Tool schemas captured at the start of each request.
    listed_schemas: Vec<ToolSchema>,
}

impl AgentLoop {
    pub fn new(config: AuraConfig) -> Self {
        let (event_tx, _) = tokio::sync::broadcast::channel(256);
        let context = AgentContext::new(&config);
        Self {
            context,
            event_tx,
            config,
            inference_config: InferenceConfig::default(),
            granted_permissions: HashSet::new(),
            planner: Planner::new(),
            listed_schemas: Vec::new(),
        }
    }

    /// Subscribe to the agent's event stream.
    pub fn subscribe(&self) -> tokio::sync::broadcast::Receiver<ExecutionEvent> {
        self.event_tx.subscribe()
    }

    /// Trigger an emergency stop — the agent will halt at the next safe checkpoint.
    pub fn emergency_stop(&self) {
        self.context.emergency_stop.store(true, Ordering::SeqCst);
        tracing::warn!(
            "Emergency stop requested for conversation {}",
            self.context.conversation_id
        );
        let _ = self
            .event_tx
            .send(ExecutionEvent::new(AgentMessage::StatusUpdate {
                status: AgentStatus::Error,
                message: "Emergency stop activated".to_string(),
            }));
    }

    fn send(&self, event: AgentMessage) {
        let _ = self.event_tx.send(ExecutionEvent::new(event));
    }

    /// Load a past conversation (persisted in SQLite) into context so the agent
    /// remembers previous exchanges across restarts.
    pub fn restore_history(&mut self, conversation_id: Uuid, messages: Vec<Message>) {
        self.context.conversation_id = conversation_id;
        self.context.messages = messages;
    }

    /// Build the structured reasoning prompt sent to the inference backend.
    fn build_prompt(&mut self) -> String {
        let schemas: Vec<String> = self
            .active_schemas()
            .iter()
            .filter_map(|s| serde_json::to_string(s).ok())
            .collect();

        let mut history_pairs = String::new();
        for msg in self
            .context
            .recent_history(self.config.context_history_limit)
        {
            let author = match msg.role {
                MessageRole::User => "USER",
                MessageRole::Assistant => "ASSISTANT",
                MessageRole::System => "SYSTEM",
                MessageRole::Tool => "OBSERVATION",
            };
            let content: String = msg.content.chars().take(MAX_PROMPT_MESSAGE_CHARS).collect();
            history_pairs.push_str(&format!("{author}: {content}\n"));
        }

        format!(
            "You are AURA, a fully local-first Android automation agent.\n\
             Respond with exactly ONE JSON object and nothing else.\n\
             Protocol:\n\
             - Final answer: {{\"action\":\"respond\",\"text\":\"<your answer>\"}}\n\
             - Request a tool: {{\"action\":\"call_tool\",\"tool\":\"<tool_id>\",\"args\":{{...}}}}\n\
             After each tool call you receive its result as an OBSERVATION message.\n\
             Never invent tool ids outside the AVAILABLE TOOLS list.\n\n\
             AVAILABLE TOOLS: [{}]\n\n\
             CONVERSATION (oldest first):\n{history_pairs}\n\
             Respond to the USER's latest request with your next single action now.",
            schemas.join(", ")
        )
    }

    fn active_schemas(&self) -> Vec<ToolSchema> {
        self.listed_schemas.clone()
    }

    /// Publish the visible plan produced by the planner so UIs can show
    /// execution progress without implying LLM-driven decomposition.
    fn publish_plan_events(&mut self, plan: &Plan) {
        for step in plan.steps.iter() {
            self.send(AgentMessage::PlanningStep {
                description: step.description.clone(),
                step_index: step.index,
                total_steps: plan.steps.len() as u32,
            });
        }
    }

    /// Process a user text input and return the agent's response string.
    pub async fn process_input(
        &mut self,
        input: String,
        deps: &AgentDeps<'_>,
    ) -> Result<String, AuraError> {
        if self.context.emergency_stop.load(Ordering::SeqCst) {
            return Err(AuraError::AgentError("Agent is stopped".to_string()));
        }

        // Cache tool schemas for this request (subscription to enabling changes).
        self.listed_schemas = deps.tools.list_schemas();

        // ── Plan visibility (deterministic bookkeeping, not fake intelligence) ──
        let plan = self.planner.create_plan(&input);
        self.publish_plan_events(&plan);
        self.context.current_plan = Some(plan);

        // ── Record & publish user input ──
        let user_msg = Message::new(MessageRole::User, input.clone());
        if let Some(memory) = deps.memory {
            memory
                .save_message(self.context.conversation_id, &user_msg)
                .map_err(|e| {
                    AuraError::AgentError(format!("Persisting user message failed: {e}"))
                })?;
        }
        self.context.add_message(user_msg.clone());
        self.send(AgentMessage::UserInput {
            content: input.clone(),
            conversation_id: self.context.conversation_id,
        });

        let mut consecutive_tool_failures: u32 = 0;

        // ── Reasoning / execution loop ──
        loop {
            if self.context.emergency_stop.load(Ordering::SeqCst) {
                self.finalize_plan(PlanStatus::Cancelled);
                return Err(AuraError::AgentError(
                    "Agent halted: emergency stop".to_string(),
                ));
            }
            if self.context.iteration_count >= self.context.max_iterations {
                self.finalize_plan(PlanStatus::Failed);
                self.send(AgentMessage::AgentError {
                    message: format!(
                        "Stopped: reached maximum planning iterations ({})",
                        self.context.max_iterations
                    ),
                    recoverable: true,
                });
                return Err(AuraError::AgentError(
                    "Agent halted: maximum planning iterations reached".to_string(),
                ));
            }

            // ── Reasoning ──
            self.send(AgentMessage::StatusUpdate {
                status: AgentStatus::Thinking,
                message: "Reasoning…".to_string(),
            });

            let prompt = self.build_prompt();
            let raw = deps
                .inference
                .generate(&prompt, &self.inference_config)
                .await?;

            // Safe checkpoint: the user may have pressed emergency stop while
            // generation was running. Discard the output and halt immediately.
            if self.context.emergency_stop.load(Ordering::SeqCst) {
                self.finalize_plan(PlanStatus::Cancelled);
                return Err(AuraError::AgentError(
                    "Agent halted: emergency stop".to_string(),
                ));
            }
            self.context.increment_iteration();

            match parse_action(&raw) {
                ParsedAction::Respond(text) => {
                    let assistant_msg = Message::new(MessageRole::Assistant, text.clone());
                    if let Some(memory) = deps.memory {
                        memory
                            .save_message(self.context.conversation_id, &assistant_msg)
                            .map_err(|e| {
                                AuraError::AgentError(format!(
                                    "Persisting assistant message failed: {e}"
                                ))
                            })?;
                    }
                    self.context.add_message(assistant_msg.clone());
                    self.send(AgentMessage::AssistantResponse {
                        content: text.clone(),
                        is_streaming: false,
                    });
                    self.finalize_plan(PlanStatus::Completed);
                    self.send(AgentMessage::StatusUpdate {
                        status: AgentStatus::Idle,
                        message: "Ready".to_string(),
                    });
                    return Ok(text);
                }

                ParsedAction::CallTool { tool, args } => {
                    if self.context.emergency_stop.load(Ordering::SeqCst) {
                        self.finalize_plan(PlanStatus::Cancelled);
                        return Err(AuraError::AgentError(
                            "Agent halted: emergency stop".to_string(),
                        ));
                    }
                    // ── Limit enforcement ──
                    if self.context.tool_calls_used >= self.context.max_tool_calls {
                        self.finalize_plan(PlanStatus::Failed);
                        self.send(AgentMessage::AgentError {
                            message: format!(
                                "Stopped: reached maximum tool calls ({})",
                                self.context.max_tool_calls
                            ),
                            recoverable: true,
                        });
                        return Err(AuraError::AgentError(
                            "Agent halted: maximum tool calls reached".to_string(),
                        ));
                    }
                    self.context.tool_calls_used += 1;

                    let call_id = Uuid::new_v4().to_string();
                    self.send(AgentMessage::ToolCall {
                        tool_id: call_id.clone(),
                        tool_name: tool.clone(),
                        arguments: args.clone(),
                    });

                    // ── Permission gate: deny with an observation (not a hard
                    // error) so the model can adapt and the user sees why. ──
                    let denied = deps.tools.get(&tool).map(|t| t.schema()).map(|schema| {
                        schema
                            .required_permissions
                            .iter()
                            .filter(|p| !self.granted_permissions.contains(p.as_str()))
                            .cloned()
                            .collect::<Vec<String>>()
                    });

                    let result = match denied {
                        Some(missing) if !missing.is_empty() => Err(AuraError::ToolError(format!(
                            "Permission denied for tool '{tool}'. Missing grants: {}. The user can grant these in the AURA settings.",
                            missing.join(", ")
                        ))),
                        _ => deps
                            .tools
                            .execute(&tool, args, self.config.tool_timeout_secs)
                            .await,
                    };

                    // ── Observation ──
                    let (observation, succeeded, error_note) = match &result {
                        Ok(value) => (value.clone(), true, None),
                        Err(err) => (
                            serde_json::json!({ "error": err.to_string() }),
                            false,
                            Some(err.to_string()),
                        ),
                    };
                    // ── Store the result of the tool call in memory ──
                    if succeeded {
                        consecutive_tool_failures = 0;
                    } else {
                        consecutive_tool_failures += 1;
                    }

                    let observation_text =
                        format!("{{\"tool\":\"{}\",\"result\":{}}}", tool, observation);
                    let obs_msg = Message::new(MessageRole::Tool, observation_text);
                    if let Some(memory) = deps.memory {
                        memory
                            .save_message(self.context.conversation_id, &obs_msg)
                            .map_err(|e| {
                                AuraError::AgentError(format!(
                                    "Persisting tool observation failed: {e}"
                                ))
                            })?;
                    }
                    self.context.add_message(obs_msg);

                    self.send(AgentMessage::ToolResult {
                        tool_id: call_id,
                        success: succeeded,
                        output: observation,
                        error: error_note,
                    });

                    // Guard against the model endlessly re-calling a failing tool.
                    if consecutive_tool_failures >= 3 {
                        self.finalize_plan(PlanStatus::Failed);
                        self.send(AgentMessage::AgentError {
                            message: "Stopped: 3 consecutive tool failures".to_string(),
                            recoverable: true,
                        });
                        return Err(AuraError::AgentError(
                            "Agent halted: 3 consecutive tool failures".to_string(),
                        ));
                    }
                }
            }
        }
    }

    fn finalize_plan(&mut self, status: PlanStatus) {
        if let Some(plan) = self.context.current_plan.as_mut() {
            plan.status = status;
            for step in plan.steps.iter_mut() {
                step.status = match plan.status {
                    PlanStatus::Completed => StepStatus::Completed,
                    PlanStatus::Failed => StepStatus::Failed,
                    PlanStatus::Cancelled => StepStatus::Skipped,
                    PlanStatus::InProgress | PlanStatus::Pending => StepStatus::Pending,
                };
            }
        }
    }
}

// ─── Tests ────────────────────────────────────────────────────────────────

#[cfg(test)]
mod tests {
    use super::*;
    use async_trait::async_trait;
    use aura_inference::InferenceBackend;
    use aura_tools::{DateTimeTool, ToolRegistry};
    use std::collections::VecDeque;
    use std::sync::atomic::AtomicBool;
    use std::sync::Mutex;

    /// Backend that replays a fixed script of completions and records the
    /// prompts it was shown. Proves the loop mechanics without an LLM.
    struct ScriptedBackend {
        script: Mutex<VecDeque<String>>,
        prompts_seen: Arc<Mutex<Vec<String>>>,
        stop_flag: Arc<AtomicBool>,
        fire_stop_on_call: usize,
        calls: Mutex<usize>,
    }

    impl ScriptedBackend {
        fn new(script: Vec<String>) -> Self {
            Self {
                script: Mutex::new(script.into()),
                prompts_seen: Arc::new(Mutex::new(Vec::new())),
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
    impl InferenceBackend for ScriptedBackend {
        async fn load_model(&mut self, path: &str) -> Result<aura_inference::ModelInfo, AuraError> {
            Ok(aura_inference::ModelInfo {
                name: "scripted".into(),
                path: path.into(),
                size_bytes: 0,
                context_length: 4096,
                quantization: "none".into(),
            })
        }
        async fn unload_model(&mut self) -> Result<(), AuraError> {
            Ok(())
        }
        fn is_loaded(&self) -> bool {
            true
        }
        async fn generate(
            &self,
            prompt: &str,
            _config: &aura_core::InferenceConfig,
        ) -> Result<String, AuraError> {
            self.prompts_seen
                .lock()
                .expect("lock")
                .push(prompt.to_string());
            let mut calls = self.calls.lock().expect("lock");
            *calls += 1;
            if *calls == self.fire_stop_on_call {
                // Simulate the mid-execution emergency stop button press.
                self.stop_flag.store(true, Ordering::SeqCst);
            }
            let mut script = self.script.lock().expect("lock");
            match script.pop_front() {
                Some(reply) => Ok(reply),
                None => Err(AuraError::InferenceError("script exhausted".into())),
            }
        }
    }

    fn deps<'a>(inference: &'a InferenceManager, tools: &'a ToolRegistry) -> AgentDeps<'a> {
        AgentDeps {
            inference,
            tools,
            memory: None,
        }
    }

    fn respond(text: &str) -> String {
        serde_json::json!({ "action": "respond", "text": text }).to_string()
    }

    fn call_tool(id: &str, args: serde_json::Value) -> String {
        serde_json::json!({ "action": "call_tool", "tool": id, "args": args }).to_string()
    }

    #[test]
    fn parse_action_understands_protocol() {
        assert_eq!(
            parse_action(&respond("hello")),
            ParsedAction::Respond("hello".to_string())
        );
        let parsed = parse_action(&call_tool("datetime", serde_json::json!({})));
        match parsed {
            ParsedAction::CallTool { tool, .. } => assert_eq!(tool, "datetime"),
            other => panic!("expected CallTool, got {other:?}"),
        }
        // Degraded mode: unparseable output becomes the answer verbatim.
        assert_eq!(
            parse_action("plain text answer"),
            ParsedAction::Respond("plain text answer".to_string())
        );
        // JSON extraction from embedded text.
        assert_eq!(
            parse_action("junk {\"action\":\"respond\",\"text\":\"ok\"} junk"),
            ParsedAction::Respond("ok".to_string())
        );
    }

    #[tokio::test]
    async fn loop_runs_tool_then_answers() {
        let backend = ScriptedBackend::new(vec![
            call_tool("datetime", serde_json::json!({})),
            respond("done according to clock"),
        ]);
        let mut manager = InferenceManager::with_backend(backend);
        manager.load_model("scripted.gguf").await.expect("load");

        let mut registry = ToolRegistry::new();
        registry.register(DateTimeTool);

        let mut agent = AgentLoop::new(AuraConfig::default());
        let deps = deps(&manager, &registry);
        let result = agent
            .process_input("what time is it".into(), &deps)
            .await
            .expect("loop should complete");
        assert_eq!(result, "done according to clock");
        assert_eq!(agent.context.tool_calls_used, 1);
        // Tool observation should have been fed back into context.
        assert!(agent
            .context
            .messages
            .iter()
            .any(|m| m.role == MessageRole::Tool));
    }

    #[tokio::test]
    async fn loop_enforces_max_tool_calls() {
        // Endless tool calls: the cap must hard-stop the loop.
        let backend = ScriptedBackend::new(vec![call_tool("datetime", serde_json::json!({})); 64]);
        let mut manager = InferenceManager::with_backend(backend);
        manager.load_model("scripted.gguf").await.expect("load");

        let mut registry = ToolRegistry::new();
        registry.register(DateTimeTool);

        let config = AuraConfig {
            max_tool_calls: 3,
            max_iterations: 64,
            ..AuraConfig::default()
        };
        let mut agent = AgentLoop::new(config);
        let deps = deps(&manager, &registry);
        let err = agent
            .process_input("loop forever".into(), &deps)
            .await
            .expect_err("expected max-tool-calls halt");
        assert!(err.to_string().contains("maximum tool calls"), "got: {err}");
        assert_eq!(agent.context.tool_calls_used, 3);
    }

    #[tokio::test]
    async fn loop_halts_on_emergency_stop() {
        // The second backend call simulates the UI pressing emergency stop.
        let backend = ScriptedBackend::new(vec![
            call_tool("datetime", serde_json::json!({})),
            respond("never reached"),
        ])
        .with_stop_hook(2);
        let stop_flag = backend.stop_flag.clone();
        let mut manager = InferenceManager::with_backend(backend);
        manager.load_model("scripted.gguf").await.expect("load");

        let mut registry = ToolRegistry::new();
        registry.register(DateTimeTool);

        let mut agent = AgentLoop::new(AuraConfig::default());
        agent.context.emergency_stop = stop_flag.clone();
        let deps = deps(&manager, &registry);
        let err = agent
            .process_input("hazardous task".into(), &deps)
            .await
            .expect_err("expected emergency stop");
        assert!(err.to_string().contains("emergency stop"), "got: {err}");
        // Re-stopped agent refuses further work immediately.
        let err = agent
            .process_input("try again".into(), &deps)
            .await
            .expect_err("expected stopped refusal");
        assert!(err.to_string().contains("stopped"));
    }

    #[tokio::test]
    async fn loop_blocks_tool_without_granted_permission() {
        use aura_tools::AppLauncherTool;

        struct TestLauncher;
        impl aura_tools::AppLauncher for TestLauncher {
            fn launch(&self, package: &str) -> Result<serde_json::Value, AuraError> {
                Ok(serde_json::json!({"launched": package}))
            }
        }

        // The backend first tries the sensitive tool, then answers.
        let backend = ScriptedBackend::new(vec![
            call_tool(
                "app_launcher",
                serde_json::json!({"package_name": "com.example"}),
            ),
            respond("the launch was denied by permissions"),
        ]);
        let prompts = backend.prompts_seen.clone();
        let mut manager = InferenceManager::with_backend(backend);
        manager.load_model("scripted.gguf").await.expect("load");

        let mut registry = ToolRegistry::new();
        registry.register(AppLauncherTool::new(TestLauncher));

        let mut agent = AgentLoop::new(AuraConfig::default());
        agent.granted_permissions.clear(); // nothing granted
        let deps = deps(&manager, &registry);
        let answer = agent
            .process_input("open the app".into(), &deps)
            .await
            .expect("loop should finish with a response");
        assert!(answer.contains("denied"));
        // The second prompt must contain the denial observation so the model
        // can explain it to the user.
        let seen = prompts.lock().expect("lock");
        assert!(seen.len() >= 2);
        assert!(seen[1].contains("Permission denied"));
        assert!(seen[1].contains("android.package_launcher"));
    }

    #[tokio::test]
    async fn loop_stops_after_three_consecutive_tool_failures() {
        let backend = ScriptedBackend::new(vec![call_tool("ghost_tool", serde_json::json!({})); 8]);
        let mut manager = InferenceManager::with_backend(backend);
        manager.load_model("scripted.gguf").await.expect("load");

        let mut registry = ToolRegistry::new();
        registry.register(DateTimeTool);

        let config = AuraConfig {
            max_iterations: 32,
            ..AuraConfig::default()
        };
        let mut agent = AgentLoop::new(config);
        let deps = deps(&manager, &registry);
        let err = agent
            .process_input("do the impossible".into(), &deps)
            .await
            .expect_err("expected consecutive-failure halt");
        assert!(
            err.to_string().contains("consecutive tool failures"),
            "got: {err}"
        );
    }

    #[tokio::test]
    async fn memory_records_messages_when_wired() {
        let backend = ScriptedBackend::new(vec![respond("stored reply")]);
        let mut manager = InferenceManager::with_backend(backend);
        manager.load_model("scripted.gguf").await.expect("load");
        let registry = ToolRegistry::new();
        let memory = MemoryDb::new_in_memory().expect("memory");

        let mut agent = AgentLoop::new(AuraConfig::default());
        let deps = AgentDeps {
            inference: &manager,
            tools: &registry,
            memory: Some(&memory),
        };
        agent
            .process_input("remember me".into(), &deps)
            .await
            .expect("ok");
        assert_eq!(
            memory
                .count_messages(agent.context.conversation_id)
                .expect("count"),
            2
        );
    }
}
