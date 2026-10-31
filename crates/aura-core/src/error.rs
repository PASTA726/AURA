use thiserror::Error;

#[derive(Error, Debug)]
pub enum AuraError {
    #[error("Agent error: {0}")]
    AgentError(String),

    #[error("Inference error: {0}")]
    InferenceError(String),

    #[error("Memory error: {0}")]
    MemoryError(String),

    #[error("Tool error: {0}")]
    ToolError(String),

    #[error("Config error: {0}")]
    ConfigError(String),

    #[error("IO error: {0}")]
    IoError(String),
}
