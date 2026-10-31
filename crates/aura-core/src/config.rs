use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AuraConfig {
    pub max_iterations: u32,
    pub timeout_secs: u64,
    pub network_enabled: bool,
    pub debug_mode: bool,
}

impl Default for AuraConfig {
    fn default() -> Self {
        Self {
            max_iterations: 20,
            timeout_secs: 120,
            network_enabled: false,
            debug_mode: false,
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
