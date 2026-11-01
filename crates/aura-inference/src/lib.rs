use async_trait::async_trait;
use serde::{Deserialize, Serialize};

use aura_core::{AuraError, InferenceConfig};

/// Metadata about a loaded model.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ModelInfo {
    pub name: String,
    pub path: String,
    pub size_bytes: u64,
    pub context_length: u32,
    pub quantization: String,
}

/// Current status of the inference backend.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum ModelStatus {
    NotLoaded,
    Loading,
    Ready,
    Error(String),
}

/// Abstraction over different inference backends (llama.cpp, candle, etc.).
#[async_trait]
pub trait InferenceBackend: Send + Sync {
    async fn load_model(&mut self, path: &str) -> Result<ModelInfo, AuraError>;
    async fn unload_model(&mut self) -> Result<(), AuraError>;
    fn is_loaded(&self) -> bool;
    async fn generate(&self, prompt: &str, config: &InferenceConfig) -> Result<String, AuraError>;
}

// ─── Placeholder backend ──────────────────────────────────────────────────

/// A stand-in backend that returns mock responses.
/// Will be replaced by a real llama.cpp integration in Phase 3.
pub struct PlaceholderBackend {
    model_info: Option<ModelInfo>,
}

impl PlaceholderBackend {
    pub fn new() -> Self {
        Self { model_info: None }
    }
}

impl Default for PlaceholderBackend {
    fn default() -> Self {
        Self::new()
    }
}

#[async_trait]
impl InferenceBackend for PlaceholderBackend {
    async fn load_model(&mut self, path: &str) -> Result<ModelInfo, AuraError> {
        tracing::info!("PlaceholderBackend: simulating model load from {path}");
        let info = ModelInfo {
            name: "placeholder-model".to_string(),
            path: path.to_string(),
            size_bytes: 0,
            context_length: 4096,
            quantization: "Q4_K_M (placeholder)".to_string(),
        };
        self.model_info = Some(info.clone());
        Ok(info)
    }

    async fn unload_model(&mut self) -> Result<(), AuraError> {
        tracing::info!("PlaceholderBackend: unloading model");
        self.model_info = None;
        Ok(())
    }

    fn is_loaded(&self) -> bool {
        self.model_info.is_some()
    }

    async fn generate(
        &self,
        _prompt: &str,
        _config: &InferenceConfig,
    ) -> Result<String, AuraError> {
        Ok(
            "(Inference backend not yet connected — llama.cpp integration pending Phase 3)"
                .to_string(),
        )
    }
}

// ─── InferenceManager ─────────────────────────────────────────────────────

/// Owns an inference backend and tracks model status.
pub struct InferenceManager {
    backend: Box<dyn InferenceBackend + Send + Sync>,
    status: ModelStatus,
}

impl InferenceManager {
    /// Create a manager backed by the placeholder backend.
    pub fn new() -> Self {
        Self {
            backend: Box::new(PlaceholderBackend::new()),
            status: ModelStatus::NotLoaded,
        }
    }

    /// Create a manager with a custom backend (useful for testing or future backends).
    pub fn with_backend(backend: impl InferenceBackend + 'static) -> Self {
        Self {
            backend: Box::new(backend),
            status: ModelStatus::NotLoaded,
        }
    }

    pub fn status(&self) -> &ModelStatus {
        &self.status
    }

    pub async fn load_model(&mut self, path: &str) -> Result<ModelInfo, AuraError> {
        self.status = ModelStatus::Loading;
        match self.backend.load_model(path).await {
            Ok(info) => {
                self.status = ModelStatus::Ready;
                Ok(info)
            }
            Err(e) => {
                self.status = ModelStatus::Error(e.to_string());
                Err(e)
            }
        }
    }

    pub async fn generate(
        &self,
        prompt: &str,
        config: &InferenceConfig,
    ) -> Result<String, AuraError> {
        self.backend.generate(prompt, config).await
    }

    pub fn is_loaded(&self) -> bool {
        self.backend.is_loaded()
    }
}

impl Default for InferenceManager {
    fn default() -> Self {
        Self::new()
    }
}
