use async_trait::async_trait;
use rusqlite::{params, Connection};
use std::sync::Mutex;
use uuid::Uuid;

use aura_core::{AuraError, MemoryEntry, MemoryEntryType, MemoryStore, Message, MessageRole};

/// One row of the persistent audit log (tool actions, model loads, permission
/// changes). Surfaced verbatim to the Activity screen.
#[derive(Debug, Clone)]
pub struct AuditRecord {
    pub timestamp: chrono::DateTime<chrono::Utc>,
    pub event_type: String,
    pub description: String,
}

pub struct MemoryDb {
    conn: Mutex<Connection>,
}

impl MemoryDb {
    pub fn new(db_path: &str) -> Result<Self, AuraError> {
        let conn = Connection::open(db_path).map_err(|e| AuraError::MemoryError(e.to_string()))?;
        let db = Self {
            conn: Mutex::new(conn),
        };
        db.initialize_schema()?;
        Ok(db)
    }

    pub fn new_in_memory() -> Result<Self, AuraError> {
        let conn =
            Connection::open_in_memory().map_err(|e| AuraError::MemoryError(e.to_string()))?;
        let db = Self {
            conn: Mutex::new(conn),
        };
        db.initialize_schema()?;
        Ok(db)
    }

    fn initialize_schema(&self) -> Result<(), AuraError> {
        let conn = self
            .conn
            .lock()
            .map_err(|e| AuraError::MemoryError(format!("Lock error: {e}")))?;

        conn.execute_batch(
            "
            CREATE TABLE IF NOT EXISTS memories (
                id          TEXT PRIMARY KEY,
                content     TEXT NOT NULL,
                entry_type  TEXT NOT NULL,
                created_at  TEXT NOT NULL,
                tags        TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS conversations (
                id         TEXT PRIMARY KEY,
                title      TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS messages (
                id              TEXT PRIMARY KEY,
                conversation_id TEXT NOT NULL,
                role            TEXT NOT NULL,
                content         TEXT NOT NULL,
                timestamp       TEXT NOT NULL,
                metadata        TEXT,
                FOREIGN KEY(conversation_id) REFERENCES conversations(id)
            );

            CREATE TABLE IF NOT EXISTS audit_log (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp   TEXT NOT NULL,
                event_type  TEXT NOT NULL,
                description TEXT NOT NULL
            );
            ",
        )
        .map_err(|e| AuraError::MemoryError(format!("Schema init failed: {e}")))?;

        Ok(())
    }

    pub fn save_message(&self, conversation_id: Uuid, msg: &Message) -> Result<(), AuraError> {
        let conn = self
            .conn
            .lock()
            .map_err(|e| AuraError::MemoryError(format!("Lock error: {e}")))?;

        // Ensure conversation exists so FOREIGN KEY is satisfied
        let now_str = chrono::Utc::now().to_rfc3339();
        conn.execute(
            "INSERT OR IGNORE INTO conversations (id, title, created_at, updated_at) VALUES (?1, 'Conversation', ?2, ?2)",
            params![conversation_id.to_string(), now_str],
        )
        .map_err(|e| AuraError::MemoryError(format!("Ensure conversation failed: {e}")))?;

        let role_str = match msg.role {
            MessageRole::User => "user",
            MessageRole::Assistant => "assistant",
            MessageRole::System => "system",
            MessageRole::Tool => "tool",
        };

        let metadata_str = msg.metadata.as_ref().map(|m| m.to_string());

        conn.execute(
            "INSERT INTO messages (id, conversation_id, role, content, timestamp, metadata)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6)
             ON CONFLICT(id) DO UPDATE SET content=?4, metadata=?6",
            params![
                msg.id.to_string(),
                conversation_id.to_string(),
                role_str,
                msg.content,
                msg.timestamp.to_rfc3339(),
                metadata_str,
            ],
        )
        .map_err(|e| AuraError::MemoryError(format!("Save message failed: {e}")))?;

        // Touch the parent conversation so ordering by updated_at works.
        conn.execute(
            "UPDATE conversations SET updated_at = ?2 WHERE id = ?1",
            params![conversation_id.to_string(), chrono::Utc::now().to_rfc3339()],
        )
        .map_err(|e| AuraError::MemoryError(format!("Touch conversation failed: {e}")))?;

        Ok(())
    }

    pub fn get_conversation_messages(
        &self,
        conversation_id: Uuid,
    ) -> Result<Vec<Message>, AuraError> {
        let conn = self
            .conn
            .lock()
            .map_err(|e| AuraError::MemoryError(format!("Lock error: {e}")))?;

        let mut stmt = conn
            .prepare(
                "SELECT id, role, content, timestamp, metadata FROM messages
             WHERE conversation_id = ?1
             ORDER BY timestamp ASC",
            )
            .map_err(|e| AuraError::MemoryError(format!("Prepare failed: {e}")))?;

        let messages = stmt
            .query_map(params![conversation_id.to_string()], |row| {
                let id_str: String = row.get(0)?;
                let role_str: String = row.get(1)?;
                let content: String = row.get(2)?;
                let ts_str: String = row.get(3)?;
                let metadata_str: Option<String> = row.get(4)?;

                Ok((id_str, role_str, content, ts_str, metadata_str))
            })
            .map_err(|e| AuraError::MemoryError(format!("Query failed: {e}")))?;

        let mut result = Vec::new();
        for row in messages {
            let (id_str, role_str, content, ts_str, metadata_str) =
                row.map_err(|e| AuraError::MemoryError(format!("Row error: {e}")))?;

            let id = Uuid::parse_str(&id_str)
                .map_err(|e| AuraError::MemoryError(format!("UUID parse: {e}")))?;

            let role = match role_str.as_str() {
                "user" => MessageRole::User,
                "assistant" => MessageRole::Assistant,
                "system" => MessageRole::System,
                "tool" => MessageRole::Tool,
                other => return Err(AuraError::MemoryError(format!("Unknown role: {other}"))),
            };

            let timestamp = chrono::DateTime::parse_from_rfc3339(&ts_str)
                .map_err(|e| AuraError::MemoryError(format!("Timestamp parse: {e}")))?
                .with_timezone(&chrono::Utc);

            let metadata = metadata_str
                .as_deref()
                .map(serde_json::from_str)
                .transpose()
                .map_err(|e: serde_json::Error| {
                    AuraError::MemoryError(format!("Metadata parse: {e}"))
                })?;

            result.push(Message {
                id,
                role,
                content,
                timestamp,
                metadata,
            });
        }

        Ok(result)
    }

    pub fn log_audit(&self, event_type: &str, description: &str) -> Result<(), AuraError> {
        let conn = self
            .conn
            .lock()
            .map_err(|e| AuraError::MemoryError(format!("Lock error: {e}")))?;

        let now = chrono::Utc::now().to_rfc3339();
        conn.execute(
            "INSERT INTO audit_log (timestamp, event_type, description) VALUES (?1, ?2, ?3)",
            params![now, event_type, description],
        )
        .map_err(|e| AuraError::MemoryError(format!("Audit log failed: {e}")))?;

        Ok(())
    }

    /// Number of messages recorded in a conversation (0 if unknown conversation).
    pub fn count_messages(&self, conversation_id: Uuid) -> Result<u64, AuraError> {
        let conn = self
            .conn
            .lock()
            .map_err(|e| AuraError::MemoryError(format!("Lock error: {e}")))?;

        conn.query_row(
            "SELECT COUNT(*) FROM messages WHERE conversation_id = ?1",
            params![conversation_id.to_string()],
            |row| row.get::<_, u64>(0),
        )
        .map_err(|e| AuraError::MemoryError(format!("Count failed: {e}")))
    }

    /// Total number of conversations ever recorded.
    pub fn count_conversations(&self) -> Result<u64, AuraError> {
        let conn = self
            .conn
            .lock()
            .map_err(|e| AuraError::MemoryError(format!("Lock error: {e}")))?;

        conn.query_row("SELECT COUNT(*) FROM conversations", [], |row| {
            row.get::<_, u64>(0)
        })
        .map_err(|e| AuraError::MemoryError(format!("Count failed: {e}")))
    }

    /// Identifier of the most recently updated conversation, if any.
    pub fn latest_conversation(&self) -> Result<Option<Uuid>, AuraError> {
        let conn = self
            .conn
            .lock()
            .map_err(|e| AuraError::MemoryError(format!("Lock error: {e}")))?;

        let row: Option<String> = conn
            .query_row(
                "SELECT id FROM conversations ORDER BY updated_at DESC LIMIT 1",
                [],
                |row| row.get(0),
            )
            .map_err(|e| AuraError::MemoryError(format!("Latest conversation query: {e}")))?;

        row.map(|s| {
            Uuid::parse_str(&s).map_err(|e| AuraError::MemoryError(format!("UUID parse: {e}")))
        })
        .transpose()
    }

    /// Delete all chat history. Memories and audit log are preserved.
    pub fn wipe_messages(&self) -> Result<(), AuraError> {
        let conn = self
            .conn
            .lock()
            .map_err(|e| AuraError::MemoryError(format!("Lock error: {e}")))?;

        conn.execute("DELETE FROM messages", [])
            .map_err(|e| AuraError::MemoryError(format!("Wipe messages failed: {e}")))?;
        conn.execute("DELETE FROM conversations", [])
            .map_err(|e| AuraError::MemoryError(format!("Wipe conversations failed: {e}")))?;
        Ok(())
    }

    /// Delete all long-term memory entries (preferences, task history, etc.).
    pub fn wipe_memories(&self) -> Result<(), AuraError> {
        let conn = self
            .conn
            .lock()
            .map_err(|e| AuraError::MemoryError(format!("Lock error: {e}")))?;

        conn.execute("DELETE FROM memories", [])
            .map_err(|e| AuraError::MemoryError(format!("Wipe memories failed: {e}")))?;
        Ok(())
    }

    /// Most recent memories, newest first (for the Memory screen).
    pub fn list_memories(&self, limit: u32) -> Result<Vec<MemoryEntry>, AuraError> {
        let conn = self
            .conn
            .lock()
            .map_err(|e| AuraError::MemoryError(format!("Lock error: {e}")))?;

        let mut stmt = conn
            .prepare(
                "SELECT id, content, entry_type, created_at, tags FROM memories
             ORDER BY created_at DESC LIMIT ?1",
            )
            .map_err(|e| AuraError::MemoryError(format!("Prepare failed: {e}")))?;

        let rows = stmt
            .query_map(params![limit], |row| {
                Ok((
                    row.get::<_, String>(0)?,
                    row.get::<_, String>(1)?,
                    row.get::<_, String>(2)?,
                    row.get::<_, String>(3)?,
                    row.get::<_, String>(4)?,
                ))
            })
            .map_err(|e| AuraError::MemoryError(format!("Query failed: {e}")))?;

        parse_memory_rows(
            rows.collect::<Result<Vec<_>, _>>()
                .map_err(|e| AuraError::MemoryError(format!("Row error: {e}")))?,
        )
    }

    /// Latest audit-log records, newest first (for the Activity screen).
    pub fn recent_audit(&self, limit: u32) -> Result<Vec<AuditRecord>, AuraError> {
        let conn = self
            .conn
            .lock()
            .map_err(|e| AuraError::MemoryError(format!("Lock error: {e}")))?;

        let mut stmt = conn
            .prepare(
                "SELECT timestamp, event_type, description FROM audit_log
             ORDER BY id DESC LIMIT ?1",
            )
            .map_err(|e| AuraError::MemoryError(format!("Prepare failed: {e}")))?;

        let rows = stmt
            .query_map(params![limit], |row| {
                Ok((
                    row.get::<_, String>(0)?,
                    row.get::<_, String>(1)?,
                    row.get::<_, String>(2)?,
                ))
            })
            .map_err(|e| AuraError::MemoryError(format!("Query failed: {e}")))?;

        let mut result = Vec::new();
        for row in rows {
            let (ts_str, event_type, description) =
                row.map_err(|e| AuraError::MemoryError(format!("Row error: {e}")))?;
            result.push(AuditRecord {
                timestamp: chrono::DateTime::parse_from_rfc3339(&ts_str)
                    .map_err(|e| AuraError::MemoryError(format!("Timestamp parse: {e}")))?
                    .with_timezone(&chrono::Utc),
                event_type,
                description,
            });
        }
        Ok(result)
    }

    /// Synchronous insert shared by the async [`MemoryStore`] trait and the
    /// UI-facing synchronous paths (JNI).
    pub fn save_entry_sync(&self, entry: &MemoryEntry) -> Result<(), AuraError> {
        use MemoryEntryType::*;

        let conn = self
            .conn
            .lock()
            .map_err(|e| AuraError::MemoryError(format!("Lock error: {e}")))?;

        let entry_type_str = match entry.entry_type {
            Conversation => "conversation",
            LongTerm => "long_term",
            Preference => "preference",
            TaskHistory => "task_history",
            TempContext => "temp_context",
        };

        let tags_json = serde_json::to_string(&entry.tags)
            .map_err(|e| AuraError::MemoryError(format!("Tags serialize: {e}")))?;

        conn.execute(
            "INSERT OR REPLACE INTO memories (id, content, entry_type, created_at, tags)
             VALUES (?1, ?2, ?3, ?4, ?5)",
            params![
                entry.id.to_string(),
                entry.content,
                entry_type_str,
                entry.created_at.to_rfc3339(),
                tags_json,
            ],
        )
        .map_err(|e| AuraError::MemoryError(format!("Save failed: {e}")))?;

        Ok(())
    }
}

/// Shared row→`MemoryEntry` conversion for `list_memories` / `search`.
fn parse_memory_rows(
    rows: Vec<(String, String, String, String, String)>,
) -> Result<Vec<MemoryEntry>, AuraError> {
    let mut result = Vec::new();
    for (id_str, content, entry_type_str, created_at_str, tags_str) in rows {
        let id = Uuid::parse_str(&id_str)
            .map_err(|e| AuraError::MemoryError(format!("UUID parse: {e}")))?;

        let entry_type = match entry_type_str.as_str() {
            "conversation" => MemoryEntryType::Conversation,
            "long_term" => MemoryEntryType::LongTerm,
            "preference" => MemoryEntryType::Preference,
            "task_history" => MemoryEntryType::TaskHistory,
            "temp_context" => MemoryEntryType::TempContext,
            other => {
                return Err(AuraError::MemoryError(format!(
                    "Unknown memory type: {other}"
                )))
            }
        };

        let created_at = chrono::DateTime::parse_from_rfc3339(&created_at_str)
            .map_err(|e| AuraError::MemoryError(format!("Timestamp parse: {e}")))?
            .with_timezone(&chrono::Utc);

        let tags = serde_json::from_str::<Vec<String>>(&tags_str)
            .map_err(|e| AuraError::MemoryError(format!("Tags parse: {e}")))?;

        result.push(MemoryEntry {
            id,
            content,
            entry_type,
            created_at,
            tags,
        });
    }
    Ok(result)
}

#[async_trait]
impl MemoryStore for MemoryDb {
    async fn save(&self, entry: MemoryEntry) -> Result<(), AuraError> {
        self.save_entry_sync(&entry)
    }

    async fn search(&self, query: &str) -> Result<Vec<MemoryEntry>, AuraError> {
        let conn = self
            .conn
            .lock()
            .map_err(|e| AuraError::MemoryError(format!("Lock error: {e}")))?;

        let pattern = format!("%{query}%");
        let mut stmt = conn.prepare(
            "SELECT id, content, entry_type, created_at, tags FROM memories WHERE content LIKE ?1",
        )
        .map_err(|e| AuraError::MemoryError(format!("Prepare failed: {e}")))?;

        let entries = stmt
            .query_map(params![pattern], |row| {
                Ok((
                    row.get::<_, String>(0)?,
                    row.get::<_, String>(1)?,
                    row.get::<_, String>(2)?,
                    row.get::<_, String>(3)?,
                    row.get::<_, String>(4)?,
                ))
            })
            .map_err(|e| AuraError::MemoryError(format!("Query failed: {e}")))?;

        parse_memory_rows(
            entries
                .collect::<Result<Vec<_>, _>>()
                .map_err(|e| AuraError::MemoryError(format!("Row error: {e}")))?,
        )
    }
}
