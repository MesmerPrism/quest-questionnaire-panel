use serde::{Deserialize, Serialize};

pub const OPERATOR_PROTOCOL_VERSION: &str = "quest.questionnaire.operator.v1";
pub const PANEL_PROTOCOL_VERSION: &str = "quest.questionnaire.v1";
pub const STUDY_ID: &str = "maia-spatial";
pub const SCHEMA_ID: &str = "maia2-spatial-frame-questionnaire-v1";

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct BlockSpec {
    pub command_name: &'static str,
    pub label: &'static str,
    pub open_stage: &'static str,
    pub screen_sequence: &'static [&'static str],
}

pub const BLOCK1: BlockSpec = BlockSpec {
    command_name: "maia_spatial.block1",
    label: "Block 1",
    open_stage: "maia_spatial:language_selection",
    screen_sequence: &[
        "maia_spatial:language_selection",
        "maia_spatial:demographics",
        "maia_spatial:maia2",
    ],
};

pub const BLOCK2: BlockSpec = BlockSpec {
    command_name: "maia_spatial.block2",
    label: "Block 2",
    open_stage: "maia_spatial:spatial_frame_reference_1",
    screen_sequence: &["maia_spatial:spatial_frame_reference_1"],
};

pub const BLOCK3: BlockSpec = BlockSpec {
    command_name: "maia_spatial.block3",
    label: "Block 3",
    open_stage: "maia_spatial:spatial_frame_reference_2",
    screen_sequence: &["maia_spatial:spatial_frame_reference_2"],
};

pub fn block_by_command(command_name: &str) -> Option<&'static BlockSpec> {
    match command_name {
        "maia_spatial.block1" => Some(&BLOCK1),
        "maia_spatial.block2" => Some(&BLOCK2),
        "maia_spatial.block3" => Some(&BLOCK3),
        _ => None,
    }
}

pub fn block_by_number(block: u8) -> Option<&'static BlockSpec> {
    match block {
        1 => Some(&BLOCK1),
        2 => Some(&BLOCK2),
        3 => Some(&BLOCK3),
        _ => None,
    }
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct OperatorCommandRequest {
    pub protocol_version: String,
    pub command_id: String,
    pub action: OperatorAction,
    pub command_name: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub debug_auto_submit: Option<bool>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub debug_command_script: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub debug_command_interval_ms: Option<u32>,
    pub panel_request: PanelLaunchSpec,
}

impl OperatorCommandRequest {
    pub fn open_block(
        command_id: impl Into<String>,
        block: &BlockSpec,
        session_id: impl Into<String>,
        participant_ref: impl Into<String>,
        language_code: impl Into<String>,
    ) -> Self {
        let language_code = language_code.into();
        Self {
            protocol_version: OPERATOR_PROTOCOL_VERSION.to_string(),
            command_id: command_id.into(),
            action: OperatorAction::OpenQuestionnaire,
            command_name: block.command_name.to_string(),
            debug_auto_submit: None,
            debug_command_script: None,
            debug_command_interval_ms: None,
            panel_request: PanelLaunchSpec {
                protocol_version: PANEL_PROTOCOL_VERSION.to_string(),
                session_id: session_id.into(),
                study_id: STUDY_ID.to_string(),
                schema_id: SCHEMA_ID.to_string(),
                open_stage: block.open_stage.to_string(),
                screen_sequence: block
                    .screen_sequence
                    .iter()
                    .map(|stage| (*stage).to_string())
                    .collect(),
                participant_ref: participant_ref.into(),
                questionnaire_state: QuestionnaireState {
                    language_code: if language_code.trim().is_empty() {
                        "en".to_string()
                    } else {
                        language_code
                    },
                },
                caller_hint: CallerHint {
                    engine: "rusty-morphospace".to_string(),
                    transport: "makepad-windows-operator".to_string(),
                },
            },
        }
    }

    pub fn dismiss(command_id: impl Into<String>, session_id: impl Into<String>) -> Self {
        Self {
            protocol_version: OPERATOR_PROTOCOL_VERSION.to_string(),
            command_id: command_id.into(),
            action: OperatorAction::DismissQuestionnaire,
            command_name: "questionnaire.dismiss".to_string(),
            debug_auto_submit: None,
            debug_command_script: None,
            debug_command_interval_ms: None,
            panel_request: PanelLaunchSpec {
                protocol_version: PANEL_PROTOCOL_VERSION.to_string(),
                session_id: session_id.into(),
                study_id: STUDY_ID.to_string(),
                schema_id: SCHEMA_ID.to_string(),
                open_stage: String::new(),
                screen_sequence: Vec::new(),
                participant_ref: String::new(),
                questionnaire_state: QuestionnaireState {
                    language_code: "en".to_string(),
                },
                caller_hint: CallerHint {
                    engine: "rusty-morphospace".to_string(),
                    transport: "makepad-windows-operator".to_string(),
                },
            },
        }
    }
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum OperatorAction {
    OpenQuestionnaire,
    DismissQuestionnaire,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct PanelLaunchSpec {
    pub protocol_version: String,
    pub session_id: String,
    pub study_id: String,
    pub schema_id: String,
    pub open_stage: String,
    pub screen_sequence: Vec<String>,
    pub participant_ref: String,
    pub questionnaire_state: QuestionnaireState,
    pub caller_hint: CallerHint,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct QuestionnaireState {
    pub language_code: String,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct CallerHint {
    pub engine: String,
    pub transport: String,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize, Default)]
pub struct BridgeStatusResponse {
    pub protocol_version: Option<String>,
    pub bridge: Option<BridgeInfo>,
    pub foreground: ForegroundState,
    pub last_command: Option<LastCommand>,
    pub last_result: Option<LastResult>,
    pub message: Option<String>,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize, Default)]
pub struct BridgeCommandResponse {
    pub protocol_version: Option<String>,
    pub accepted: bool,
    pub message: Option<String>,
    pub foreground: ForegroundState,
    pub last_result: Option<LastResult>,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize, Default)]
pub struct BridgeInfo {
    pub app: Option<String>,
    pub version: Option<String>,
    pub device_label: Option<String>,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize, Default)]
pub struct ForegroundState {
    pub xr_app_foreground: Option<bool>,
    pub panel_foreground: Option<bool>,
    pub foreground_package: Option<String>,
    pub foreground_activity: Option<String>,
    pub questionnaire_id: Option<String>,
    pub open_stage: Option<String>,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize, Default)]
pub struct LastCommand {
    pub command_id: Option<String>,
    pub command_name: Option<String>,
    pub accepted: Option<bool>,
    pub message: Option<String>,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize, Default)]
pub struct LastResult {
    pub request_id: Option<String>,
    pub session_id: Option<String>,
    pub status: Option<String>,
    pub open_stage: Option<String>,
}

pub fn endpoint_url(base_url: &str, path: &str) -> Result<String, String> {
    let base = base_url.trim().trim_end_matches('/');
    if base.is_empty() {
        return Err("Bridge endpoint is empty".to_string());
    }
    if !(base.starts_with("http://") || base.starts_with("https://")) {
        return Err("Bridge endpoint must start with http:// or https://".to_string());
    }
    Ok(format!(
        "{base}/{path}",
        path = path.trim_start_matches('/')
    ))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn block_two_uses_first_spatial_reference_stage() {
        let request =
            OperatorCommandRequest::open_block("cmd-1", &BLOCK2, "session-1", "P001", "de");

        assert_eq!(request.action, OperatorAction::OpenQuestionnaire);
        assert_eq!(request.command_name, "maia_spatial.block2");
        assert_eq!(
            request.panel_request.open_stage,
            "maia_spatial:spatial_frame_reference_1"
        );
        assert_eq!(
            request.panel_request.screen_sequence,
            vec!["maia_spatial:spatial_frame_reference_1"]
        );
        assert_eq!(
            request.panel_request.questionnaire_state.language_code,
            "de"
        );
    }

    #[test]
    fn endpoint_url_normalizes_slashes() {
        assert_eq!(
            endpoint_url("http://127.0.0.1:8787/", "/v1/status").unwrap(),
            "http://127.0.0.1:8787/v1/status"
        );
    }

    #[test]
    fn endpoint_url_rejects_missing_scheme() {
        assert!(endpoint_url("127.0.0.1:8787", "/v1/status").is_err());
    }
}
