use aura_core::{Message, MessageRole};
use aura_memory::MemoryDb;
use aura_planner::Planner;
use aura_runtime::AuraRuntime;
use aura_tools::{DateTimeTool, DeviceInfoTool, Tool};

#[test]
fn test_runtime_initialization_and_status() {
    let mut runtime = AuraRuntime::new().expect("Failed to create AuraRuntime");
    assert_eq!(runtime.get_status(), aura_protocol::AgentStatus::Idle);

    let reply = runtime.process_message("Hello AURA").expect("Process message failed");
    assert!(!reply.is_empty());
}

#[tokio::test]
async fn test_tools_execution() {
    let dt_tool = DateTimeTool;
    let res = dt_tool.execute(serde_json::json!({})).await.expect("DateTimeTool failed");
    assert!(res.get("utc").is_some());

    let dev_tool = DeviceInfoTool;
    let dev_res = dev_tool.execute(serde_json::json!({})).await.expect("DeviceInfoTool failed");
    assert_eq!(dev_res.get("platform").and_then(|v| v.as_str()), Some("android"));
}

#[test]
fn test_memory_db_in_memory() {
    let db = MemoryDb::new_in_memory().expect("Failed to create in-memory db");
    let conv_id = uuid::Uuid::new_v4();
    let msg = Message::new(MessageRole::User, "Test message content");

    db.save_message(conv_id, &msg).expect("Failed to save message");
    let retrieved = db.get_conversation_messages(conv_id).expect("Failed to get messages");
    assert_eq!(retrieved.len(), 1);
    assert_eq!(retrieved[0].content, "Test message content");
}

#[test]
fn test_planner_decomposition() {
    let planner = Planner::new();
    let plan = planner.create_plan("Summarize local files and report");
    assert!(plan.step_count() >= 3);
}

#[test]
fn test_emergency_stop() {
    let runtime = AuraRuntime::new().expect("Failed to create AuraRuntime");
    runtime.emergency_stop();
    assert_eq!(runtime.get_status(), aura_protocol::AgentStatus::Paused);
}
