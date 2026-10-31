use std::sync::{
    atomic::{AtomicBool, Ordering},
    Arc,
};

use uuid::Uuid;

use aura_core::{AuraConfig, AuraError, Message, MessageRole};
use aura_protocol::{AgentMessage, AgentStatus, ExecutionEvent};

/// Represents the operational state of the agent at any given moment.
#[derive(Debug, Clone)]
pub enum AgentState {
    Idle,
    Processing,
    Planning,
    ExecutingTool(String),
    WaitingForApproval,
    Paused,
    Error(String),
}

/// Holds everything the agent needs to maintain a conversation context.
pub struct AgentContext {
    pub conversation_id: Uuid,
    pub messages: Vec<Message>,
    pub current_plan: Option<Vec<String>>,
    pub iteration_count: u32,
    pub max_iterations: u32,
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
            emergency_stop: Arc::new(AtomicBool::new(false)),
        }
    }

    /// Returns `true` if the agent should halt — either because an emergency stop
    /// was requested or the maximum iteration count has been reached.
    pub fn should_stop(&self) -> bool {
        self.emergency_stop.load(Ordering::SeqCst)
            || self.iteration_count >= self.max_iterations
    }

    pub fn increment_iteration(&mut self) {
        self.iteration_count += 1;
    }

    pub fn add_message(&mut self, msg: Message) {
        self.messages.push(msg);
    }
}

/// The top-level agent loop that drives a conversation.
pub struct AgentLoop {
    pub context: AgentContext,
    pub event_tx: tokio::sync::broadcast::Sender<ExecutionEvent>,
    pub config: AuraConfig,
}

impl AgentLoop {
    pub fn new(config: AuraConfig) -> Self {
        let (event_tx, _) = tokio::sync::broadcast::channel(256);
        let context = AgentContext::new(&config);
        Self {
            context,
            event_tx,
            config,
        }
    }

    /// Subscribe to the agent's event stream.
    pub fn subscribe(&self) -> tokio::sync::broadcast::Receiver<ExecutionEvent> {
        self.event_tx.subscribe()
    }

    /// Trigger an emergency stop — the agent will halt at the next safe checkpoint.
    pub fn emergency_stop(&self) {
        self.context.emergency_stop.store(true, Ordering::SeqCst);
        tracing::warn!("Emergency stop requested for conversation {}", self.context.conversation_id);
        let _ = self.event_tx.send(ExecutionEvent::new(AgentMessage::StatusUpdate {
            status: AgentStatus::Error,
            message: "Emergency stop activated".to_string(),
        }));
    }

    /// Process a user text input and return the agent's response string.
    ///
    /// At this stage inference is not yet connected; returns a placeholder
    /// response while still exercising the full event-publishing pipeline.
    pub async fn process_input(&mut self, input: String) -> Result<String, AuraError> {
        if self.context.should_stop() {
            return Err(AuraError::AgentError(
                "Agent is stopped or has reached the maximum iteration limit".to_string(),
            ));
        }

        // Record user message
        let user_msg = Message::new(MessageRole::User, input.clone());
        self.context.add_message(user_msg.clone());

        // Publish: received user input
        let _ = self.event_tx.send(ExecutionEvent::new(AgentMessage::UserInput {
            content: input.clone(),
            conversation_id: self.context.conversation_id,
        }));

        // Publish: thinking
        let _ = self.event_tx.send(ExecutionEvent::new(AgentMessage::StatusUpdate {
            status: AgentStatus::Thinking,
            message: "Processing input…".to_string(),
        }));

        self.context.increment_iteration();

        // Placeholder response — real inference will be wired in Phase 3
        let response_text =
            "AURA agent loop placeholder — inference not yet connected".to_string();

        let assistant_msg = Message::new(MessageRole::Assistant, response_text.clone());
        self.context.add_message(assistant_msg);

        // Publish: assistant response
        let _ = self.event_tx.send(ExecutionEvent::new(AgentMessage::AssistantResponse {
            content: response_text.clone(),
            is_streaming: false,
        }));

        // Publish: back to idle
        let _ = self.event_tx.send(ExecutionEvent::new(AgentMessage::StatusUpdate {
            status: AgentStatus::Idle,
            message: "Ready".to_string(),
        }));

        tracing::debug!(
            iteration = self.context.iteration_count,
            conversation = %self.context.conversation_id,
            "Agent loop cycle complete"
        );

        Ok(response_text)
    }
}
