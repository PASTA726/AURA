use crate::config::InferenceConfig;
use crate::error::AuraError;
use crate::types::{MemoryEntry, Message};
use async_trait::async_trait;

#[async_trait]
pub trait Agent: Send + Sync {
    async fn process_message(&mut self, msg: Message) -> Result<Message, AuraError>;
}

#[async_trait]
pub trait Inferable: Send + Sync {
    async fn generate(&self, prompt: &str, config: &InferenceConfig) -> Result<String, AuraError>;
}

#[async_trait]
pub trait MemoryStore: Send + Sync {
    async fn save(&self, entry: MemoryEntry) -> Result<(), AuraError>;
    async fn search(&self, query: &str) -> Result<Vec<MemoryEntry>, AuraError>;
}
