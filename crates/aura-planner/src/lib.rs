use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub enum PlanStatus {
    Pending,
    InProgress,
    Completed,
    Failed,
    Cancelled,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub enum StepStatus {
    Pending,
    Running,
    Completed,
    Failed,
    Skipped,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PlanStep {
    pub index: u32,
    pub description: String,
    pub tool_id: Option<String>,
    pub status: StepStatus,
    pub result: Option<serde_json::Value>,
}

impl PlanStep {
    pub fn new(index: u32, description: impl Into<String>) -> Self {
        Self {
            index,
            description: description.into(),
            tool_id: None,
            status: StepStatus::Pending,
            result: None,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Plan {
    pub id: Uuid,
    pub goal: String,
    pub steps: Vec<PlanStep>,
    pub created_at: DateTime<Utc>,
    pub status: PlanStatus,
}

impl Plan {
    pub fn new(goal: impl Into<String>, steps: Vec<PlanStep>) -> Self {
        Self {
            id: Uuid::new_v4(),
            goal: goal.into(),
            steps,
            created_at: Utc::now(),
            status: PlanStatus::Pending,
        }
    }

    pub fn step_count(&self) -> usize {
        self.steps.len()
    }

    pub fn is_complete(&self) -> bool {
        matches!(
            self.status,
            PlanStatus::Completed | PlanStatus::Failed | PlanStatus::Cancelled
        )
    }
}

/// Responsible for decomposing goals into executable plans.
///
/// Currently uses a placeholder heuristic; LLM-driven planning will be
/// integrated in Phase 2.
pub struct Planner;

impl Planner {
    pub fn new() -> Self {
        Self
    }

    /// Create a simple single-step plan for the given goal.
    pub fn create_plan(&self, goal: &str) -> Plan {
        tracing::debug!("Creating plan for goal: {goal}");

        let descriptions = Self::decompose_goal(goal);
        let steps: Vec<PlanStep> = descriptions
            .into_iter()
            .enumerate()
            .map(|(i, desc)| PlanStep::new(i as u32, desc))
            .collect();

        Plan::new(goal, steps)
    }

    /// Placeholder goal decomposition — returns a minimal three-step breakdown.
    /// Will be replaced by LLM-based decomposition in a later phase.
    pub fn decompose_goal(goal: &str) -> Vec<String> {
        tracing::trace!("Decomposing goal: {goal}");
        vec![
            "Analyze goal".to_string(),
            "Execute".to_string(),
            "Verify result".to_string(),
        ]
    }
}

impl Default for Planner {
    fn default() -> Self {
        Self::new()
    }
}
