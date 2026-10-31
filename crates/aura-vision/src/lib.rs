use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};

use aura_core::AuraError;

/// Bounding box for a UI element in screen coordinates.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Rect {
    pub left: i32,
    pub top: i32,
    pub right: i32,
    pub bottom: i32,
}

impl Rect {
    pub fn new(left: i32, top: i32, right: i32, bottom: i32) -> Self {
        Self { left, top, right, bottom }
    }

    pub fn width(&self) -> i32 {
        self.right - self.left
    }

    pub fn height(&self) -> i32 {
        self.bottom - self.top
    }

    pub fn center_x(&self) -> i32 {
        (self.left + self.right) / 2
    }

    pub fn center_y(&self) -> i32 {
        (self.top + self.bottom) / 2
    }
}

/// A single UI element parsed from Android accessibility or UI automator data.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UiElement {
    pub element_id: String,
    pub class_name: String,
    pub text: Option<String>,
    pub content_desc: Option<String>,
    pub bounds: Rect,
    pub is_clickable: bool,
    pub is_scrollable: bool,
    pub is_enabled: bool,
}

/// A snapshot of the current screen content.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ScreenContent {
    pub timestamp: DateTime<Utc>,
    pub elements: Vec<UiElement>,
    pub screenshot_available: bool,
}

impl ScreenContent {
    pub fn empty() -> Self {
        Self {
            timestamp: Utc::now(),
            elements: Vec::new(),
            screenshot_available: false,
        }
    }

    pub fn clickable_elements(&self) -> Vec<&UiElement> {
        self.elements.iter().filter(|e| e.is_clickable && e.is_enabled).collect()
    }
}

/// Processes raw screen data (e.g. accessibility XML) into structured `ScreenContent`.
pub struct VisionProcessor;

impl VisionProcessor {
    pub fn new() -> Self {
        Self
    }

    /// Parse raw screen data into a `ScreenContent` snapshot.
    ///
    /// Currently a placeholder — real XML/JSON parsing will be added in Phase 4.
    pub fn process_screen_content(&self, _raw: &str) -> Result<ScreenContent, AuraError> {
        Ok(ScreenContent::empty())
    }
}

impl Default for VisionProcessor {
    fn default() -> Self {
        Self::new()
    }
}
