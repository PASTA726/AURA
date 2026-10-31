use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub enum AgentStatus {
    Idle,
    Thinking,
    Planning,
    ExecutingTool,
    WaitingForUser,
    Paused,
    Error,
    Completed,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum AgentMessage {
    UserInput {
        content: String,
        conversation_id: Uuid,
    },
    AssistantResponse {
        content: String,
        is_streaming: bool,
    },
    PlanningStep {
        description: String,
        step_index: u32,
        total_steps: u32,
    },
    ToolCall {
        tool_id: String,
        tool_name: String,
        arguments: serde_json::Value,
    },
    ToolResult {
        tool_id: String,
        success: bool,
        output: serde_json::Value,
        error: Option<String>,
    },
    AgentError {
        message: String,
        recoverable: bool,
    },
    StatusUpdate {
        status: AgentStatus,
        message: String,
    },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ExecutionEvent {
    pub id: Uuid,
    pub timestamp: DateTime<Utc>,
    pub event: AgentMessage,
}

impl ExecutionEvent {
    pub fn new(event: AgentMessage) -> Self {
        Self {
            id: Uuid::new_v4(),
            timestamp: Utc::now(),
            event,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ToolSchema {
    pub id: String,
    pub name: String,
    pub description: String,
    pub parameters: serde_json::Value,
    pub required_permissions: Vec<String>,
}
