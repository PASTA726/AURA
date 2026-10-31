use aura_agent::AgentLoop;
use aura_core::{AuraConfig, AuraError};
use aura_inference::InferenceManager;
use aura_protocol::AgentStatus;
use aura_tools::{DateTimeTool, DeviceInfoTool, ToolRegistry};

/// Top-level coordinator that owns the agent loop, tool registry, and inference manager.
pub struct AuraRuntime {
    pub agent_loop: AgentLoop,
    pub tool_registry: ToolRegistry,
    pub inference_manager: InferenceManager,
    pub config: AuraConfig,
    pub tokio_rt: Option<tokio::runtime::Runtime>,
}

impl AuraRuntime {
    pub fn new() -> Result<Self, AuraError> {
        let config = AuraConfig::default();
        let agent_loop = AgentLoop::new(config.clone());
        let mut tool_registry = ToolRegistry::new();

        // Register default built-in tools
        tool_registry.register(DateTimeTool);
        tool_registry.register(DeviceInfoTool);

        let inference_manager = InferenceManager::new();

        let tokio_rt = if tokio::runtime::Handle::try_current().is_ok() {
            None
        } else {
            tokio::runtime::Builder::new_multi_thread()
                .worker_threads(2)
                .enable_all()
                .build()
                .ok()
        };

        Ok(Self {
            agent_loop,
            tool_registry,
            inference_manager,
            config,
            tokio_rt,
        })
    }

    pub fn process_message(&mut self, input: &str) -> Result<String, AuraError> {
        let input_str = input.to_string();
        if let Ok(handle) = tokio::runtime::Handle::try_current() {
            tokio::task::block_in_place(|| {
                handle.block_on(async {
                    self.agent_loop.process_input(input_str).await
                })
            })
        } else if let Some(ref rt) = self.tokio_rt {
            rt.block_on(async {
                self.agent_loop.process_input(input_str).await
            })
        } else {
            let rt = tokio::runtime::Builder::new_current_thread()
                .enable_all()
                .build()
                .map_err(|e| AuraError::AgentError(e.to_string()))?;
            rt.block_on(async {
                self.agent_loop.process_input(input_str).await
            })
        }
    }

    pub fn emergency_stop(&self) {
        self.agent_loop.emergency_stop();
    }

    pub fn get_status(&self) -> AgentStatus {
        if self.agent_loop.context.should_stop() {
            AgentStatus::Paused
        } else {
            AgentStatus::Idle
        }
    }
}

impl Default for AuraRuntime {
    fn default() -> Self {
        Self::new().expect("Failed to initialize default AuraRuntime")
    }
}

#[cfg(feature = "android")]
pub mod jni_bridge {
    use super::*;
    use jni::objects::{JClass, JString};
    use jni::sys::{jlong, jstring};
    use jni::JNIEnv;

    #[no_mangle]
    pub extern "system" fn Java_app_aura_core_AuraEngine_nativeInit(
        _env: JNIEnv,
        _class: JClass,
    ) -> jlong {
        match AuraRuntime::new() {
            Ok(runtime) => Box::into_raw(Box::new(runtime)) as jlong,
            Err(_) => 0,
        }
    }

    #[no_mangle]
    pub extern "system" fn Java_app_aura_core_AuraEngine_nativeProcessMessage(
        mut env: JNIEnv,
        _class: JClass,
        ptr: jlong,
        input: JString,
    ) -> jstring {
        if ptr == 0 {
            let output = env.new_string("Error: Engine not initialized").unwrap();
            return output.into_raw();
        }

        let runtime = unsafe { &mut *(ptr as *mut AuraRuntime) };
        let input_rust: String = match env.get_string(&input) {
            Ok(s) => s.into(),
            Err(_) => {
                let output = env.new_string("Error: Invalid string").unwrap();
                return output.into_raw();
            }
        };

        match runtime.process_message(&input_rust) {
            Ok(reply) => env.new_string(reply).unwrap().into_raw(),
            Err(e) => env.new_string(format!("Error: {e}")).unwrap().into_raw(),
        }
    }

    #[no_mangle]
    pub extern "system" fn Java_app_aura_core_AuraEngine_nativeEmergencyStop(
        _env: JNIEnv,
        _class: JClass,
        ptr: jlong,
    ) {
        if ptr != 0 {
            let runtime = unsafe { &*(ptr as *const AuraRuntime) };
            runtime.emergency_stop();
        }
    }

    #[no_mangle]
    pub extern "system" fn Java_app_aura_core_AuraEngine_nativeDestroy(
        _env: JNIEnv,
        _class: JClass,
        ptr: jlong,
    ) {
        if ptr != 0 {
            let _ = unsafe { Box::from_raw(ptr as *mut AuraRuntime) };
        }
    }
}
