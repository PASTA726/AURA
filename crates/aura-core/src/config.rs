use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AuraConfig {
    pub max_iterations: u32,
    pub timeout_secs: u64,
    pub network_enabled: bool,
    pub debug_mode: bool,
    /// Maximum number of tool invocations permitted per user request.
    /// Guards against uncontrolled recursive tool execution.
    pub max_tool_calls: u32,
    /// Wall-clock timeout applied to every individual tool execution.
    pub tool_timeout_secs: u64,
    /// Number of recent context messages replayed into the inference prompt.
    pub context_history_limit: usize,
}

impl Default for AuraConfig {
    fn default() -> Self {
        Self {
            max_iterations: 20,
            timeout_secs: 120,
            network_enabled: false,
            debug_mode: false,
            max_tool_calls: 10,
            tool_timeout_secs: 30,
            context_history_limit: 24,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct InferenceConfig {
    pub max_tokens: u32,
    pub temperature: f32,
    pub top_p: f32,
    pub stop_sequences: Vec<String>,
}

impl Default for InferenceConfig {
    fn default() -> Self {
        Self {
            max_tokens: 2048,
            temperature: 0.7,
            top_p: 0.9,
            stop_sequences: vec![],
        }
    }
}
