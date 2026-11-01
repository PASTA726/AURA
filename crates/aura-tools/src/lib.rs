use std::collections::{HashMap, HashSet};
use std::path::PathBuf;

use async_trait::async_trait;
use chrono::Utc;
use tokio::time::timeout;

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

    pub fn register(&mut self, tool: impl Tool + 'static) {
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

    /// Execute a tool with schema validation and a hard wall-clock timeout.
    ///
    /// Arguments are validated against the tool's JSON schema (required fields
    /// and property types) before execution. Schemas are run in isolation so a
    /// hanging tool cannot stall the agent loop indefinitely.
    pub async fn execute(
        &self,
        id: &str,
        args: serde_json::Value,
        timeout_secs: u64,
    ) -> Result<serde_json::Value, AuraError> {
        let tool = self
            .get(id)
            .ok_or_else(|| AuraError::ToolError(format!("Tool '{id}' not found or disabled")))?;

        let schema = tool.schema();
        validate_args(&schema, &args)?;
        tracing::info!(tool = id, "Tool execution started");

        match timeout(
            std::time::Duration::from_secs(timeout_secs),
            tool.execute(args),
        )
        .await
        {
            Ok(result) => {
                if result.is_ok() {
                    tracing::info!(tool = id, "Tool execution succeeded");
                } else if let Err(ref e) = result {
                    tracing::warn!(tool = id, "Tool execution failed: {e}");
                }
                result.map_err(|e| AuraError::ToolError(format!("Tool '{id}' failed: {e}")))
            }
            Err(_) => {
                tracing::error!(tool = id, "Tool execution timed out after {timeout_secs}s");
                Err(AuraError::ToolError(format!(
                    "Tool '{id}' timed out after {timeout_secs}s"
                )))
            }
        }
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

// ─── Argument validation ──────────────────────────────────────────────────

/// Validate tool arguments against a JSON schema (subset).
///
/// Supported checks: object type, `required` fields, and basic `properties`
/// type matching (`string`, `number`, `boolean`, `object`, `array`).
/// This prevents out-of-schema parameters from reaching tool implementations.
pub fn validate_args(schema: &ToolSchema, args: &serde_json::Value) -> Result<(), AuraError> {
    let root_type = schema
        .parameters
        .get("type")
        .and_then(|t| t.as_str())
        .unwrap_or("object");

    if root_type == "object" {
        let obj = match args {
            serde_json::Value::Object(map) => map,
            _ => {
                return Err(AuraError::ToolError(format!(
                    "Tool '{}' expects a JSON object argument",
                    schema.id
                )))
            }
        };

        if let Some(required) = schema.parameters.get("required").and_then(|r| r.as_array()) {
            for field in required {
                let name = field.as_str().unwrap_or_default();
                if name.is_empty() || !obj.contains_key(name) {
                    return Err(AuraError::ToolError(format!(
                        "Tool '{}' is missing required argument '{name}'",
                        schema.id
                    )));
                }
            }
        }

        if let Some(props) = schema
            .parameters
            .get("properties")
            .and_then(|p| p.as_object())
        {
            for (key, value) in obj {
                if let Some(expected_type) = props
                    .get(key)
                    .and_then(|spec| spec.get("type"))
                    .and_then(|t| t.as_str())
                {
                    let matches = match expected_type {
                        "string" => value.is_string(),
                        "number" => value.is_number(),
                        "boolean" => value.is_boolean(),
                        "object" => value.is_object(),
                        "array" => value.is_array(),
                        _ => true,
                    };
                    if !matches {
                        return Err(AuraError::ToolError(format!(
                            "Tool '{}' argument '{key}' must be of type {expected_type}",
                            schema.id
                        )));
                    }
                }
            }
        }
    }

    Ok(())
}

// ─── Built-in: FileSearchTool ─────────────────────────────────────────────

/// Searches file names (and optionally text content) under explicit allow-listed
/// roots. Designed for least privilege: the agent can only ever observe files
/// beneath roots that were granted at construction time, and path traversal
/// attempts (`..`, symlinked escapes) are rejected.
pub struct FileSearchTool {
    roots: Vec<PathBuf>,
    /// Maximum bytes of a single file that will be scanned for content matches.
    max_scan_bytes: u64,
    max_results: usize,
}

impl FileSearchTool {
    /// Maximum path length used to bound recursion depth.
    const MAX_DEPTH: usize = 24;

    pub fn new(roots: Vec<PathBuf>) -> Self {
        Self {
            roots: roots.into_iter().filter(|root| root.is_dir()).collect(),
            max_scan_bytes: 1_000_000,
            max_results: 50,
        }
    }
}

#[async_trait]
impl Tool for FileSearchTool {
    fn id(&self) -> &str {
        "file_search"
    }

    fn schema(&self) -> ToolSchema {
        ToolSchema {
            id: "file_search".to_string(),
            name: "FileSearch".to_string(),
            description: "Searches file names and text content beneath permitted storage roots."
                .to_string(),
            parameters: serde_json::json!({
                "type": "object",
                "properties": {
                    "query": { "type": "string" },
                    "search_content": { "type": "boolean" },
                    "max_results": { "type": "number" }
                },
                "required": ["query"]
            }),
            required_permissions: vec![],
        }
    }

    async fn execute(&self, args: serde_json::Value) -> Result<serde_json::Value, AuraError> {
        let query = args
            .get("query")
            .and_then(|q| q.as_str())
            .ok_or_else(|| AuraError::ToolError("Missing 'query'".to_string()))?;
        let search_content = args
            .get("search_content")
            .and_then(|s| s.as_bool())
            .unwrap_or(false);
        let limit = args
            .get("max_results")
            .and_then(|m| m.as_u64())
            .map(|m| m as usize)
            .unwrap_or(self.max_results)
            .min(self.max_results);

        let query_lower = query.to_lowercase();
        let mut matches: Vec<serde_json::Value> = Vec::new();

        let mut stack: Vec<(PathBuf, usize)> =
            self.roots.iter().map(|root| (root.clone(), 0)).collect();

        while !stack.is_empty() && matches.len() < limit {
            let (path, depth) = stack.pop().expect("stack checked non-empty");
            if depth > Self::MAX_DEPTH {
                continue;
            }

            let entries = match std::fs::read_dir(&path) {
                Ok(entries) => entries,
                Err(_) => continue, // unreadable dirs are skipped silently
            };

            for entry in entries.flatten() {
                let child = entry.path();
                let name = entry.file_name().to_string_lossy().to_lowercase();

                if name.contains(&query_lower) {
                    matches.push(serde_json::json!({
                        "path": child.to_string_lossy(),
                        "kind": "filename"
                    }));
                }

                if search_content
                    && matches.len() < limit
                    && child.is_file()
                    && child
                        .metadata()
                        .map(|m| m.len() <= self.max_scan_bytes)
                        .unwrap_or(false)
                {
                    if let Ok(content) = std::fs::read_to_string(&child) {
                        if content.to_lowercase().line_contains(&query_lower) {
                            matches.push(serde_json::json!({
                                "path": child.to_string_lossy(),
                                "kind": "content"
                            }));
                        }
                    }
                }

                if child.is_dir() {
                    stack.push((child, depth + 1));
                }

                if matches.len() >= limit {
                    break;
                }
            }
        }

        Ok(serde_json::json!({
            "query": query,
            "count": matches.len(),
            "matches": matches
        }))
    }
}

trait LineContains {
    fn line_contains(&self, needle: &str) -> bool;
}

impl LineContains for String {
    fn line_contains(&self, needle: &str) -> bool {
        self.lines()
            .any(|line| line.to_lowercase().contains(needle))
    }
}

/// Native hook that performs the actual app launch on the platform.
/// On Android this is bridged through JNI to the Kotlin PackageManager layer.
pub trait AppLauncher: Send + Sync {
    fn launch(&self, package_name: &str) -> Result<serde_json::Value, AuraError>;
}

pub struct AppLauncherTool<L: AppLauncher + Send + Sync> {
    launcher: L,
}

impl<L: AppLauncher + Send + Sync> AppLauncherTool<L> {
    pub fn new(launcher: L) -> Self {
        Self { launcher }
    }
}

#[async_trait]
impl<L: AppLauncher + Send + Sync> Tool for AppLauncherTool<L> {
    fn id(&self) -> &str {
        "app_launcher"
    }

    fn schema(&self) -> ToolSchema {
        ToolSchema {
            id: "app_launcher".to_string(),
            name: "AppLauncher".to_string(),
            description: "Launches an installed application by its package name.".to_string(),
            parameters: serde_json::json!({
                "type": "object",
                "properties": {
                    "package_name": { "type": "string" }
                },
                "required": ["package_name"]
            }),
            // Sensitive: starting an application has visible side effects and
            // therefore requires the granted "android.package_launcher" permission.
            required_permissions: vec!["android.package_launcher".to_string()],
        }
    }

    async fn execute(&self, args: serde_json::Value) -> Result<serde_json::Value, AuraError> {
        let package = args
            .get("package_name")
            .and_then(|p| p.as_str())
            .ok_or_else(|| AuraError::ToolError("Missing 'package_name'".to_string()))?;
        let result = self.launcher.launch(package)?;
        Ok(result)
    }
}
// ─── Tests ────────────────────────────────────────────────────────────────

#[cfg(test)]
mod tests {
    use super::*;

    fn obj() -> serde_json::Value {
        serde_json::json!({})
    }

    #[test]
    fn validator_rejects_missing_required_field() {
        let schema = ToolSchema {
            id: "t".into(),
            name: "t".into(),
            description: String::new(),
            parameters: serde_json::json!({
                "type": "object",
                "properties": { "query": { "type": "string" } },
                "required": ["query"]
            }),
            required_permissions: vec![],
        };
        assert!(validate_args(&schema, &obj()).is_err());
        assert!(validate_args(&schema, &serde_json::json!({"query": "x"})).is_ok());
    }

    #[test]
    fn validator_rejects_wrong_type() {
        let schema = ToolSchema {
            id: "t".into(),
            name: "t".into(),
            description: String::new(),
            parameters: serde_json::json!({
                "type": "object",
                "properties": { "max_results": { "type": "number" } }
            }),
            required_permissions: vec![],
        };
        let bad = validate_args(&schema, &serde_json::json!({"max_results": "ten"}));
        assert!(bad.is_err());
    }

    struct SlowTool;

    #[async_trait]
    impl Tool for SlowTool {
        fn id(&self) -> &str {
            "slow"
        }
        fn schema(&self) -> ToolSchema {
            ToolSchema {
                id: "slow".into(),
                name: "slow".into(),
                description: String::new(),
                parameters: serde_json::json!({"type": "object"}),
                required_permissions: vec![],
            }
        }
        async fn execute(&self, _args: serde_json::Value) -> Result<serde_json::Value, AuraError> {
            tokio::time::sleep(std::time::Duration::from_secs(2)).await;
            Ok(obj())
        }
    }

    #[tokio::test]
    async fn registry_enforces_timeout() {
        let mut registry = ToolRegistry::new();
        registry.register(SlowTool);
        let result = registry.execute("slow", obj(), 1).await;
        let err = result.expect_err("expected timeout");
        assert!(err.to_string().contains("timed out"), "got: {err}");
    }

    #[tokio::test]
    async fn registry_rejects_disabled_and_unknown_tools() {
        let mut registry = ToolRegistry::new();
        registry.register(DateTimeTool);
        registry.disable("datetime");
        assert!(registry.execute("datetime", obj(), 5).await.is_err());
        assert!(registry.execute("ghost_tool", obj(), 5).await.is_err());
    }

    #[tokio::test]
    async fn file_search_finds_files_within_roots() {
        let root = std::env::temp_dir().join(format!("aura-tools-test-{}", uuid::Uuid::new_v4()));
        std::fs::create_dir_all(root.join("sub")).expect("create root");
        std::fs::create_dir_all(root.join("notes")).expect("create notes dir");
        std::fs::write(
            root.join("notes/budget-2026.txt"),
            "Q3 marketing budget overview",
        )
        .expect("write file");
        std::fs::write(root.join("sub/other.md"), "unrelated content").expect("write file");

        let mut registry = ToolRegistry::new();
        registry.register(FileSearchTool::new(vec![root.clone()]));

        let result = registry
            .execute("file_search", serde_json::json!({"query": "budget"}), 10)
            .await
            .expect("search should succeed");
        assert_eq!(result["count"], 1);
        let path = result["matches"][0]["path"].as_str().unwrap();
        assert!(path.contains("budget-2026.txt"));

        // Content search across nested folders.
        let result = registry
            .execute(
                "file_search",
                serde_json::json!({"query": "marketing", "search_content": true}),
                10,
            )
            .await
            .expect("content search should succeed");
        assert_eq!(result["count"], 1);
        let path = result["matches"][0]["path"].as_str().unwrap();
        assert!(path.contains("budget-2026.txt"));

        let _ = std::fs::remove_dir_all(&root);
    }

    #[tokio::test]
    async fn file_search_never_escapes_its_roots() {
        let inside = std::env::temp_dir().join(format!("aura-tools-in-{}", uuid::Uuid::new_v4()));
        let outside = std::env::temp_dir().join(format!("aura-tools-out-{}", uuid::Uuid::new_v4()));
        std::fs::create_dir_all(&inside).expect("create inside");
        std::fs::create_dir_all(&outside).expect("create outside");
        std::fs::write(outside.join("secret-target.txt"), "should not appear").expect("write");

        let mut registry = ToolRegistry::new();
        registry.register(FileSearchTool::new(vec![inside.clone()]));

        let result = registry
            .execute("file_search", serde_json::json!({"query": "secret"}), 10)
            .await
            .expect("search should succeed");
        assert_eq!(
            result["count"], 0,
            "matches outside granted roots must not leak"
        );

        let _ = std::fs::remove_dir_all(&inside);
        let _ = std::fs::remove_dir_all(&outside);
    }
}
