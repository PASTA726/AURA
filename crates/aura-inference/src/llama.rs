//! Local GGUF inference backend built on llama.cpp (`llama-cpp-2`).
//!
//! Enabled with the `gguf` cargo feature. Fully offline: the model file is
//! loaded from disk (user consented import) and nothing leaves the
//! device/process.

use std::num::NonZeroU32;
use std::path::PathBuf;
use std::sync::{Arc, Mutex, MutexGuard};

use async_trait::async_trait;

use llama_cpp_2::context::params::LlamaContextParams;
use llama_cpp_2::llama_backend::LlamaBackend as LlamaCppBackend;
use llama_cpp_2::llama_batch::LlamaBatch;
use llama_cpp_2::model::params::LlamaModelParams;
use llama_cpp_2::model::{LlamaChatMessage, LlamaModel};
use llama_cpp_2::sampling::LlamaSampler;
use llama_cpp_2::send_logs_to_tracing;

use aura_core::{AuraError, InferenceConfig};

use crate::{InferenceBackend, ModelInfo};

/// Safety cap so a malformed model cannot request an absurd context.
const MAX_CONTEXT: u32 = 8192;

/// Truncation cap for a single rendered prompt (chars).
const MAX_PROMPT_CHARS: usize = 24_000;

/// Fixed sampling seed: same prompt + same settings → same answer, which
/// keeps the agent's tool-protocol behaviour reproducible.
const SAMPLER_SEED: u32 = 0x5A17_0001;

/// llama.cpp requires one initialized backend instance for the process; it
/// must outlive every context. The loaded model pins a clone of the Arc so
/// an unload during an active generation cannot invalidate the backend.
pub struct GgufBackend {
    backend: Arc<LlamaCppBackend>,
    /// Current model slot; replaced atomically at load/unload.
    model: Mutex<Option<Arc<LoadedModel>>>,
}

struct LoadedModel {
    model: LlamaModel,
    info: ModelInfo,
}

impl GgufBackend {
    /// Create a new engine with no model loaded yet.
    pub fn new() -> Result<Self, AuraError> {
        // Route llama.cpp logs into tracing instead of raw stderr.
        send_logs_to_tracing(llama_cpp_2::LogOptions::default());
        let backend = LlamaCppBackend::init().map(Arc::new).map_err(|e| {
            AuraError::InferenceError(format!("llama.cpp backend init failed: {e}"))
        })?;
        Ok(Self {
            backend,
            model: Mutex::new(None),
        })
    }

    fn slot(&self) -> MutexGuard<'_, Option<Arc<LoadedModel>>> {
        self.model.lock().expect("GgufBackend model lock poisoned")
    }
}

impl Default for GgufBackend {
    fn default() -> Self {
        Self::new().expect(
            "initializing llama.cpp backend should not fail on a healthy system; \
             if it does, check the native runtime libraries",
        )
    }
}

#[async_trait]
impl InferenceBackend for GgufBackend {
    async fn load_model(&mut self, path: &str) -> Result<ModelInfo, AuraError> {
        let path = path.to_string();
        let model_path = PathBuf::from(&path);
        if !model_path.is_file() {
            return Err(AuraError::InferenceError(format!(
                "GGUF model file not found: {path}"
            )));
        }
        let size_bytes = std::fs::metadata(&model_path)
            .map_err(|e| AuraError::InferenceError(format!("stat {path}: {e}")))?
            .len();

        // Heavy C++ work runs on the blocking thread pool.
        let backend = Arc::clone(&self.backend);
        let loaded = tokio::task::spawn_blocking(move || {
            let model = LlamaModel::load_from_file(
                backend.as_ref(),
                &model_path,
                &LlamaModelParams::default(),
            )
            .map_err(|e| AuraError::InferenceError(format!("failed to load model: {e}")))?;

            let ctx_len = model.n_ctx_train().min(MAX_CONTEXT);

            // Probe one context now to surface memory/context errors at load
            // time instead of at first question time.
            let probe = model
                .new_context(backend.as_ref(), context_params(ctx_len))
                .map(|_ctx| ())
                .map_err(|e| AuraError::InferenceError(format!("context creation failed: {e}")));

            if let Err(e) = probe {
                return Err(e);
            }

            let info = ModelInfo {
                name: model_path
                    .file_stem()
                    .map(|s| s.to_string_lossy().to_string())
                    .unwrap_or_else(|| path.to_string()),
                path: path.to_string(),
                size_bytes,
                context_length: ctx_len,
                quantization: model
                    .meta_val_str("general.quantization")
                    .or_else(|_| model.meta_val_str("general.file_type"))
                    .unwrap_or_else(|_| "unknown".to_string()),
            };

            Ok((
                Arc::new(LoadedModel {
                    model,
                    info: info.clone(),
                }),
                info,
            ))
        })
        .await
        .map_err(|e| AuraError::InferenceError(format!("model load task panicked: {e}")))?;

        let (loaded_model_arc, info) = loaded?;
        *self.slot() = Some(loaded_model_arc);
        tracing::info!(
            name = %info.name,
            bytes = info.size_bytes,
            ctx = info.context_length,
            "GGUF model loaded"
        );
        Ok(info)
    }

    async fn unload_model(&mut self) -> Result<(), AuraError> {
        *self.slot() = None;
        tracing::info!("GgufBackend: model unloaded");
        Ok(())
    }

    fn is_loaded(&self) -> bool {
        self.slot().is_some()
    }

    async fn generate(
        &self,
        prompt: &str,
        config: &InferenceConfig,
        cancel: Option<Arc<std::sync::atomic::AtomicBool>>,
    ) -> Result<String, AuraError> {
        let loaded = Arc::clone(self.slot().as_ref().ok_or_else(|| {
            AuraError::InferenceError("no model loaded — load a GGUF model first".to_string())
        })?);
        let backend = Arc::clone(&self.backend);
        let prompt: String = prompt.chars().take(MAX_PROMPT_CHARS).collect();
        let config = config.clone();

        tokio::task::spawn_blocking(move || {
            generate_sync(&loaded, &backend, &prompt, &config, cancel.as_ref())
        })
        .await
        .map_err(|e| AuraError::InferenceError(format!("generation task panicked: {e}")))?
    }
}

// ─── Synchronous generation core ─────────────────────────────────────────——

fn context_params(n_ctx: u32) -> LlamaContextParams {
    LlamaContextParams::default()
        .with_n_ctx(Some(NonZeroU32::new(n_ctx).expect("ctx_len > 0")))
        .with_n_threads(thread_count())
        .with_n_threads_batch(thread_count())
}

fn thread_count() -> i32 {
    std::thread::available_parallelism()
        .map(|n| n.get() as i32)
        .unwrap_or(2)
}

/// Emergency-stop check for the generation loop (cheap atomic read per token).
fn is_cancelled(cancel: Option<&Arc<std::sync::atomic::AtomicBool>>) -> bool {
    cancel
        .map(|flag| flag.load(std::sync::atomic::Ordering::SeqCst))
        .unwrap_or(false)
}

/// One full blocking generation: chat-template → tokenize → chunked prefill →
/// sample loop (up to `max_tokens`) with per-token cancel checks → detokenize.
fn generate_sync(
    loaded: &LoadedModel,
    backend: &LlamaCppBackend,
    prompt: &str,
    config: &InferenceConfig,
    cancel: Option<&Arc<std::sync::atomic::AtomicBool>>,
) -> Result<String, AuraError> {
    // Bail out before paying for prefill if a stop was already requested.
    if is_cancelled(cancel) {
        return Err(AuraError::InferenceError(
            "generation cancelled (emergency stop)".to_string(),
        ));
    }
    let LoadedModel { model, info } = loaded;
    let rendered = render_prompt(model, prompt)?;

    let tokens = model.vocab().tokenize(rendered.as_bytes(), true, true);

    let n_ctx = info.context_length;
    let budget = u32::from(config.max_tokens).min(n_ctx);
    if tokens.len() as u32 > n_ctx - budget {
        return Err(AuraError::InferenceError(format!(
            "prompt too long: {} tokens + {} generation budget exceeds context {}",
            tokens.len(),
            budget,
            n_ctx
        )));
    }

    let mut ctx = model
        .new_context(backend, context_params(n_ctx))
        .map_err(|e| AuraError::InferenceError(format!("context creation failed: {e}")))?;

    // Prefill in chunks: LlamaBatch has a fixed capacity and a >capacity batch
    // fails with InsufficientSpace, so long prompts (agent instructions +
    // history overflow the 512 default easily). Logits are produced only for
    // the very last token of the last chunk.
    const PREFILL_CHUNK: usize = 512;
    let mut batch = LlamaBatch::new(PREFILL_CHUNK, 1);
    let last = (tokens.len() - 1) as i32;
    let mut position: i32 = 0;
    while position <= last {
        batch.clear();
        let chunk_end = ((position as usize) + PREFILL_CHUNK).min(tokens.len());
        let chunk = &tokens[position as usize..chunk_end];
        for (i, token) in chunk.iter().enumerate() {
            let is_final = (position as usize) + i == tokens.len() - 1;
            batch
                .add(*token, position + i as i32, &[0], is_final)
                .map_err(|e| AuraError::InferenceError(format!("batch add failed: {e}")))?;
        }
        ctx.decode(&mut batch)
            .map_err(|e| AuraError::InferenceError(format!("prompt decode failed: {e}")))?;
        position = chunk_end as i32;
    }

    let mut sampler = if config.temperature <= f32::EPSILON {
        LlamaSampler::chain_simple([LlamaSampler::greedy()])
    } else {
        LlamaSampler::chain_simple([
            LlamaSampler::temp(config.temperature),
            LlamaSampler::top_p(config.top_p, 1),
            LlamaSampler::dist(SAMPLER_SEED),
        ])
    };

    let mut out: Vec<u8> = Vec::new();
    let mut produced: u32 = 0;
    let mut next_position = last;

    loop {
        if is_cancelled(cancel) {
            return Err(AuraError::InferenceError(
                "generation cancelled (emergency stop)".to_string(),
            ));
        }
        let token = sampler.sample(&ctx, batch.n_tokens() - 1);
        sampler.accept(token);

        if model.vocab().is_eog(token) {
            break;
        }

        let pieces = model.vocab().token_to_piece(token, true, None).to_vec();
        out.extend_from_slice(&pieces);

        produced += 1;

        // Stop sequence support: llama.cpp "stop" handling.
        let decoded = String::from_utf8_lossy(&out);
        if let Some(stop) = config
            .stop_sequences
            .iter()
            .find(|s| !s.is_empty() && decoded.contains(s.as_str()))
        {
            let head = decoded.split(stop.as_str()).next().unwrap_or(&decoded);
            return Ok(head.trim().to_string());
        }

        if produced >= budget {
            break;
        }

        batch.clear();
        next_position += 1;
        batch
            .add(token, next_position, &[0], true)
            .map_err(|e| AuraError::InferenceError(format!("batch add failed: {e}")))?;
        ctx.decode(&mut batch)
            .map_err(|e| AuraError::InferenceError(format!("decode failed: {e}")))?;
    }

    Ok(String::from_utf8_lossy(&out).trim().to_string())
}

/// Render the agent prompt through the model's own chat template, falling
/// back to the raw text when the model ships without one.
fn render_prompt(model: &LlamaModel, user_text: &str) -> Result<String, AuraError> {
    let template = match model.chat_template(None) {
        Ok(template) => template,
        Err(_) => {
            tracing::warn!("model has no embedded chat template, using raw prompt");
            return Ok(user_text.to_string());
        }
    };
    let message = LlamaChatMessage::new("user".to_string(), user_text.to_string())
        .map_err(|e| AuraError::InferenceError(format!("chat message build failed: {e}")))?;
    model
        .apply_chat_template(&template, &[message], true)
        .map_err(|e| AuraError::InferenceError(format!("chat template failed: {e}")))
}

// ─── Tests (real model smoke test; skipped without AURA_GGUF_MODEL) ─────———

#[cfg(all(test, feature = "gguf"))]
mod tests {
    use super::*;

    /// End-to-end: load a real GGUF model and generate a completion.
    /// Needs a local model; set AURA_GGUF_MODEL to enable, e.g.
    /// `AURA_GGUF_MODEL=models/SmolLM2-135M-Instruct-Q4_K_M.gguf cargo test -p aura-inference --features gguf`
    #[tokio::test]
    async fn real_model_load_and_generate() {
        let Some(model_path) = std::env::var("AURA_GGUF_MODEL").ok() else {
            eprintln!("skipping real-model smoke test: set AURA_GGUF_MODEL");
            return;
        };

        let mut backend = GgufBackend::new().expect("backend init");
        let info = backend
            .load_model(&model_path)
            .await
            .expect("model should load");
        tracing::info!(?info, "loaded model");
        assert!(backend.is_loaded());
        assert_eq!(info.path, model_path);

        let config = InferenceConfig {
            max_tokens: 128,
            temperature: 0.7,
            top_p: 0.9,
            stop_sequences: vec![],
        };
        let answer = backend
            .generate("Say 'hello world' and nothing else.", &config, None)
            .await
            .expect("generation should work");
        assert!(
            !answer.trim().is_empty(),
            "model produced an answer: {answer:?}"
        );
        eprintln!("MODEL ANSWER: {answer}");
    }

    /// The agent's JSON-action protocol: the model must be able to emit the
    /// respond action when asked. (Loose: tiny models may paraphrase.)
    #[tokio::test]
    async fn real_model_protocol_compliance() {
        let Some(model_path) = std::env::var("AURA_GGUF_MODEL").ok() else {
            eprintln!("skipping protocol smoke test: set AURA_GGUF_MODEL");
            return;
        };

        let mut backend = GgufBackend::new().expect("backend init");
        backend.load_model(&model_path).await.expect("model loads");

        let config = InferenceConfig {
            max_tokens: 96,
            temperature: 0.3,
            top_p: 0.9,
            stop_sequences: vec![],
        };
        let answer = backend
            .generate(
                "Respond with exactly one JSON object and nothing else using this protocol: {\"action\":\"respond\",\"text\":\"<your answer>\"} — put the word ok in the text field.",
                &config,
                None,
            )
            .await
            .expect("generation");
        eprintln!("PROTOCOL ANSWER: {answer}");
        assert!(
            answer.contains("action"),
            "expected JSON action output, got: {answer}"
        );
    }
}
