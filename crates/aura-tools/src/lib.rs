use std::collections::{HashMap, HashSet};
use async_trait::async_trait;
use chrono::Utc;

use aura_core::AuraError;
use aura_protocol::ToolSchema;

/// Core trait that all tools must implement.
#[async_trait]
pub trait Tool: Send + Sync {
    fn id(&self) -> &str;
    fn schema(&self) -> ToolSchema;
    async fn execute(&self, args: serde_json::Value) -> Result<serde_json::Value, AuraError>;
}

/// Registry that owns and manages all registered tools.
pub struct ToolRegistry {
    tools: HashMap<String, Box<dyn Tool + Send + Sync>>,
    disabled: HashSet<String>,
}

impl ToolRegistry {
    pub fn new() -> Self {
        Self {
            tools: HashMap::new(),
            disabled: HashSet::new(),
        }
    }

    pub fn register(&mut self, tool: impl Tool + Send + Sync + 'static) {
        let id = tool.id().to_string();
        self.tools.insert(id, Box::new(tool));
    }

    pub fn enable(&mut self, id: &str) {
        self.disabled.remove(id);
    }

    pub fn disable(&mut self, id: &str) {
        self.disabled.insert(id.to_string());
    }

    pub fn is_enabled(&self, id: &str) -> bool {
        self.tools.contains_key(id) && !self.disabled.contains(id)
    }

    pub fn get(&self, id: &str) -> Option<&(dyn Tool + Send + Sync)> {
        if self.disabled.contains(id) {
            return None;
        }
        self.tools.get(id).map(|t| t.as_ref())
    }

    pub fn list_schemas(&self) -> Vec<ToolSchema> {
        self.tools
            .iter()
            .filter(|(id, _)| !self.disabled.contains(*id))
            .map(|(_, tool)| tool.schema())
            .collect()
    }
}

impl Default for ToolRegistry {
    fn default() -> Self {
        Self::new()
    }
}

// ─── Built-in: DateTimeTool ────────────────────────────────────────────────

pub struct DateTimeTool;

#[async_trait]
impl Tool for DateTimeTool {
    fn id(&self) -> &str {
        "datetime"
    }

    fn schema(&self) -> ToolSchema {
        ToolSchema {
            id: "datetime".to_string(),
            name: "DateTime".to_string(),
            description: "Returns the current UTC date and time.".to_string(),
            parameters: serde_json::json!({
                "type": "object",
                "properties": {},
                "required": []
            }),
            required_permissions: vec![],
        }
    }

    async fn execute(&self, _args: serde_json::Value) -> Result<serde_json::Value, AuraError> {
        let now = Utc::now();
        Ok(serde_json::json!({
            "utc": now.to_rfc3339(),
            "timestamp_unix": now.timestamp()
        }))
    }
}

// ─── Built-in: DeviceInfoTool ──────────────────────────────────────────────

pub struct DeviceInfoTool;

#[async_trait]
impl Tool for DeviceInfoTool {
    fn id(&self) -> &str {
        "device_info"
    }

    fn schema(&self) -> ToolSchema {
        ToolSchema {
            id: "device_info".to_string(),
            name: "DeviceInfo".to_string(),
            description: "Returns device hardware and software information.".to_string(),
            parameters: serde_json::json!({
                "type": "object",
                "properties": {},
                "required": []
            }),
            required_permissions: vec![],
        }
    }

    async fn execute(&self, _args: serde_json::Value) -> Result<serde_json::Value, AuraError> {
        Ok(serde_json::json!({
            "platform": "android",
            "arch": "aarch64",
            "api_level": 34,
            "model": "AURA Device (placeholder)",
            "total_ram_mb": 8192,
            "available_ram_mb": 4096,
            "cpu_cores": 8
        }))
    }
}
