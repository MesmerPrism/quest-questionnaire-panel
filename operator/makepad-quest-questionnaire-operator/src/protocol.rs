use serde::{Deserialize, Serialize};

pub const OPERATOR_PROTOCOL_VERSION: &str = "quest.questionnaire.operator.v1";
pub const PANEL_PROTOCOL_VERSION: &str = "quest.questionnaire.v1";
pub const DEFAULT_RUNTIME_OPERATOR_PROTOCOL_VERSION: &str =
    "viscereality.peripersonal.operator.v1";
pub const DEFAULT_RUNTIME_KIND: &str = "unity_quest_apk";
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
    pub target: Option<RuntimeStatusTarget>,
    pub runtime_contract: Option<RuntimeStatusContract>,
    pub capabilities: Option<RuntimeCapabilities>,
    pub bridge: Option<BridgeInfo>,
    pub foreground: ForegroundState,
    pub last_command: Option<LastCommand>,
    pub last_result: Option<LastResult>,
    pub message: Option<String>,
    pub recording_active: Option<bool>,
    pub session_dir: Option<String>,
}

impl BridgeStatusResponse {
    pub fn runtime_summary(&self) -> Option<String> {
        let mut parts = Vec::new();
        if let Some(target) = &self.target {
            let kind = target
                .runtime_kind
                .as_deref()
                .filter(|value| !value.trim().is_empty());
            let package = target
                .runtime_package
                .as_deref()
                .filter(|value| !value.trim().is_empty());
            let source_scene_path = target
                .source_scene_path
                .as_deref()
                .filter(|value| !value.trim().is_empty());
            match (kind, package) {
                (Some(kind), Some(package)) => {
                    parts.push(format!("Runtime: {kind} / {package}"));
                }
                (Some(kind), None) => parts.push(format!("Runtime: {kind}")),
                (None, Some(package)) => parts.push(format!("Runtime package: {package}")),
                (None, None) => {}
            }
            if let Some(source_scene_path) = source_scene_path {
                parts.push(format!("scene: {source_scene_path}"));
            }
        }

        if let Some(recording_active) = self.recording_active {
            parts.push(format!(
                "recording: {}",
                if recording_active { "active" } else { "idle" }
            ));
        }

        if let Some(capabilities) = &self.capabilities {
            if let Some(questionnaire_panel_launch) = capabilities.questionnaire_panel_launch {
                parts.push(format!(
                    "questionnaire: {}",
                    if questionnaire_panel_launch {
                        "available"
                    } else {
                        "not available"
                    }
                ));
            }
        }

        if parts.is_empty() {
            None
        } else {
            Some(parts.join("; "))
        }
    }
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize, Default)]
pub struct BridgeCommandResponse {
    pub protocol_version: Option<String>,
    pub accepted: bool,
    pub message: Option<String>,
    pub foreground: ForegroundState,
    pub last_result: Option<LastResult>,
    pub recording_active: Option<bool>,
    pub session_dir: Option<String>,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize, Default)]
pub struct BridgeInfo {
    pub app: Option<String>,
    pub version: Option<String>,
    pub device_label: Option<String>,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize, Default)]
pub struct RuntimeStatusTarget {
    pub runtime_kind: Option<String>,
    pub runtime_package: Option<String>,
    pub source_scene_path: Option<String>,
    pub bridge_endpoint: Option<String>,
    pub quest_selector: Option<String>,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize, Default)]
pub struct RuntimeStatusContract {
    pub operator_protocol: Option<String>,
    pub questionnaire_protocol: Option<String>,
    pub questionnaire_result_callback_protocol: Option<String>,
    pub storage_root: Option<String>,
    pub session_storage_policy: Option<String>,
    pub operator_transport: Option<String>,
    pub timing_transport: Option<String>,
    pub runtime_state_transport: Option<String>,
    pub runtime_state_lsl_stream_name: Option<String>,
    pub runtime_state_lsl_stream_type: Option<String>,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize, Default)]
pub struct RuntimeCapabilities {
    #[serde(default)]
    pub command_actions: Vec<String>,
    pub additive_recording: Option<bool>,
    pub app_private_session_bundle: Option<bool>,
    pub explicit_pull_required: Option<bool>,
    pub questionnaire_panel_launch: Option<bool>,
    pub questionnaire_result_callback_ingest: Option<bool>,
    pub lsl_clock_alignment: Option<bool>,
    pub lsl_runtime_state_readout: Option<bool>,
}

#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct RuntimeStatusExpectation {
    pub runtime_kind: Option<String>,
    pub runtime_package: Option<String>,
    pub source_scene_path: Option<String>,
    pub operator_protocol: Option<String>,
    pub required_actions: Vec<String>,
    pub require_app_private_session_bundle: bool,
    pub require_explicit_pull: bool,
    pub require_questionnaire_panel_launch: bool,
    pub require_questionnaire_result_callback_ingest: bool,
    pub require_lsl_clock_alignment: bool,
}

pub fn validate_runtime_status(
    status: &BridgeStatusResponse,
    expectation: &RuntimeStatusExpectation,
) -> Vec<String> {
    let mut issues = Vec::new();

    if let Some(expected) = non_empty_option(&expectation.runtime_kind) {
        match status
            .target
            .as_ref()
            .and_then(|target| target.runtime_kind.as_deref())
            .filter(|value| !value.trim().is_empty())
        {
            Some(actual) if actual == expected => {}
            Some(actual) => issues.push(format!(
                "runtime kind mismatch: expected `{expected}`, got `{actual}`"
            )),
            None => issues.push(format!("runtime kind missing; expected `{expected}`")),
        }
    }

    if let Some(expected) = non_empty_option(&expectation.runtime_package) {
        match status
            .target
            .as_ref()
            .and_then(|target| target.runtime_package.as_deref())
            .filter(|value| !value.trim().is_empty())
        {
            Some(actual) if actual == expected => {}
            Some(actual) => issues.push(format!(
                "runtime package mismatch: expected `{expected}`, got `{actual}`"
            )),
            None => issues.push(format!("runtime package missing; expected `{expected}`")),
        }
    }

    if let Some(expected) = non_empty_option(&expectation.source_scene_path) {
        match status
            .target
            .as_ref()
            .and_then(|target| target.source_scene_path.as_deref())
            .filter(|value| !value.trim().is_empty())
        {
            Some(actual) if actual == expected => {}
            Some(actual) => issues.push(format!(
                "source scene mismatch: expected `{expected}`, got `{actual}`"
            )),
            None => issues.push(format!("source scene missing; expected `{expected}`")),
        }
    }

    if let Some(expected) = non_empty_option(&expectation.operator_protocol) {
        let top_level = status.protocol_version.as_deref();
        let contract = status
            .runtime_contract
            .as_ref()
            .and_then(|contract| contract.operator_protocol.as_deref());
        if top_level != Some(expected) && contract != Some(expected) {
            issues.push(format!(
                "operator protocol mismatch: expected `{expected}`, got status `{}` and contract `{}`",
                top_level.unwrap_or("missing"),
                contract.unwrap_or("missing")
            ));
        }
    }

    let capabilities = status.capabilities.as_ref();
    for action in expectation
        .required_actions
        .iter()
        .filter(|value| !value.trim().is_empty())
    {
        if !capabilities
            .map(|capabilities| {
                capabilities
                    .command_actions
                    .iter()
                    .any(|item| item == action)
            })
            .unwrap_or(false)
        {
            issues.push(format!("required action `{action}` is not advertised"));
        }
    }

    require_capability(
        capabilities.and_then(|capabilities| capabilities.app_private_session_bundle),
        expectation.require_app_private_session_bundle,
        "app-private session bundle",
        &mut issues,
    );
    require_capability(
        capabilities.and_then(|capabilities| capabilities.explicit_pull_required),
        expectation.require_explicit_pull,
        "explicit pull/export",
        &mut issues,
    );
    require_capability(
        capabilities.and_then(|capabilities| capabilities.questionnaire_panel_launch),
        expectation.require_questionnaire_panel_launch,
        "questionnaire panel launch",
        &mut issues,
    );
    require_capability(
        capabilities.and_then(|capabilities| capabilities.questionnaire_result_callback_ingest),
        expectation.require_questionnaire_result_callback_ingest,
        "questionnaire result callback ingest",
        &mut issues,
    );
    require_capability(
        capabilities.and_then(|capabilities| capabilities.lsl_clock_alignment),
        expectation.require_lsl_clock_alignment,
        "LSL clock alignment",
        &mut issues,
    );

    issues
}

fn non_empty_option(value: &Option<String>) -> Option<&str> {
    value
        .as_deref()
        .map(str::trim)
        .filter(|value| !value.is_empty())
}

fn require_capability(actual: Option<bool>, required: bool, label: &str, issues: &mut Vec<String>) {
    if required && actual != Some(true) {
        issues.push(format!("{label} capability is not advertised"));
    }
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

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct RuntimeOperatorCommandRequest {
    pub protocol_version: String,
    pub command_id: String,
    pub action: RuntimeOperatorAction,
    pub command_name: String,
    pub target: RuntimeTargetSpec,
    pub session: RuntimeSessionSpec,
    pub runtime_provenance: RuntimeProvenanceSpec,
    pub marker: RuntimeMarkerSpec,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub panel_request: Option<RuntimePanelLaunchSpec>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub export_request: Option<RuntimeExportRequestSpec>,
}

impl RuntimeOperatorCommandRequest {
    pub fn start_session(
        command_id: impl Into<String>,
        protocol_version: impl Into<String>,
        target: RuntimeTargetSpec,
        session: RuntimeSessionSpec,
        runtime_provenance: RuntimeProvenanceSpec,
    ) -> Self {
        Self {
            protocol_version: protocol_version.into(),
            command_id: command_id.into(),
            action: RuntimeOperatorAction::StartSession,
            command_name: "target_runtime.session.start".to_string(),
            target,
            session,
            runtime_provenance,
            marker: RuntimeMarkerSpec::default(),
            panel_request: None,
            export_request: None,
        }
    }

    pub fn stop_session(
        command_id: impl Into<String>,
        protocol_version: impl Into<String>,
        target: RuntimeTargetSpec,
        session_id: impl Into<String>,
    ) -> Self {
        Self {
            protocol_version: protocol_version.into(),
            command_id: command_id.into(),
            action: RuntimeOperatorAction::StopSession,
            command_name: "target_runtime.session.stop".to_string(),
            target,
            session: RuntimeSessionSpec {
                session_id: session_id.into(),
                ..RuntimeSessionSpec::default()
            },
            runtime_provenance: RuntimeProvenanceSpec::default(),
            marker: RuntimeMarkerSpec::default(),
            panel_request: None,
            export_request: None,
        }
    }

    pub fn mark_timing_event(
        command_id: impl Into<String>,
        protocol_version: impl Into<String>,
        target: RuntimeTargetSpec,
        session_id: impl Into<String>,
        marker_name: impl Into<String>,
        marker_detail: impl Into<String>,
    ) -> Self {
        let marker_name = marker_name.into();
        Self {
            protocol_version: protocol_version.into(),
            command_id: command_id.into(),
            action: RuntimeOperatorAction::MarkTimingEvent,
            command_name: if marker_name.trim().is_empty() {
                "target_runtime.timing.marker".to_string()
            } else {
                format!("target_runtime.timing.{marker_name}")
            },
            target,
            session: RuntimeSessionSpec {
                session_id: session_id.into(),
                ..RuntimeSessionSpec::default()
            },
            runtime_provenance: RuntimeProvenanceSpec::default(),
            marker: RuntimeMarkerSpec {
                marker_name,
                marker_detail: marker_detail.into(),
            },
            panel_request: None,
            export_request: None,
        }
    }

    pub fn open_questionnaire(
        command_id: impl Into<String>,
        protocol_version: impl Into<String>,
        target: RuntimeTargetSpec,
        panel_request: RuntimePanelLaunchSpec,
    ) -> Self {
        let condition_id = panel_request
            .questionnaire_state
            .as_ref()
            .map(|state| state.condition_id.clone())
            .unwrap_or_default();
        let language_code = panel_request
            .questionnaire_state
            .as_ref()
            .map(|state| state.language_code.clone())
            .filter(|value| !value.trim().is_empty())
            .unwrap_or_else(|| "en".to_string());
        Self {
            protocol_version: protocol_version.into(),
            command_id: command_id.into(),
            action: RuntimeOperatorAction::OpenQuestionnaire,
            command_name: "target_runtime.questionnaire.open".to_string(),
            target,
            session: RuntimeSessionSpec {
                study_id: panel_request.study_id.clone(),
                session_id: panel_request.session_id.clone(),
                participant_ref: panel_request.participant_ref.clone(),
                condition_id,
                language_code,
                ..RuntimeSessionSpec::default()
            },
            runtime_provenance: RuntimeProvenanceSpec::default(),
            marker: RuntimeMarkerSpec::default(),
            panel_request: Some(panel_request),
            export_request: None,
        }
    }

    pub fn pull_session(
        command_id: impl Into<String>,
        protocol_version: impl Into<String>,
        target: RuntimeTargetSpec,
        session_id: impl Into<String>,
        export_request: RuntimeExportRequestSpec,
    ) -> Self {
        Self {
            protocol_version: protocol_version.into(),
            command_id: command_id.into(),
            action: RuntimeOperatorAction::PullSession,
            command_name: "target_runtime.session.pull".to_string(),
            target,
            session: RuntimeSessionSpec {
                session_id: session_id.into(),
                ..RuntimeSessionSpec::default()
            },
            runtime_provenance: RuntimeProvenanceSpec::default(),
            marker: RuntimeMarkerSpec::default(),
            panel_request: None,
            export_request: Some(export_request),
        }
    }
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum RuntimeOperatorAction {
    StartSession,
    StopSession,
    MarkTimingEvent,
    OpenQuestionnaire,
    PullSession,
}

#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct RuntimeTargetSpec {
    pub runtime_kind: String,
    pub runtime_package: String,
    pub bridge_endpoint: String,
    pub quest_selector: String,
}

impl RuntimeTargetSpec {
    pub fn new(
        runtime_kind: impl Into<String>,
        runtime_package: impl Into<String>,
        bridge_endpoint: impl Into<String>,
        quest_selector: impl Into<String>,
    ) -> Self {
        Self {
            runtime_kind: runtime_kind.into(),
            runtime_package: runtime_package.into(),
            bridge_endpoint: bridge_endpoint.into(),
            quest_selector: quest_selector.into(),
        }
    }
}

#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct RuntimeSessionSpec {
    pub study_id: String,
    pub session_id: String,
    pub participant_ref: String,
    pub dataset_id: String,
    pub condition_id: String,
    pub language_code: String,
}

#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(default)]
pub struct RuntimeProvenanceSpec {
    pub unity_project: String,
    pub unity_editor: String,
    pub runtime_build_tag: String,
    pub source_scene_path: String,
    pub apk_sha256: String,
    pub app_version_name: String,
    pub app_version_code: i32,
    pub source_commit: String,
}

#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct RuntimeMarkerSpec {
    pub marker_name: String,
    pub marker_detail: String,
}

#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct RuntimeExportRequestSpec {
    pub pull_device_session: bool,
    pub quest_storage_policy: String,
    pub windows_storage_policy: String,
    pub quest_package: String,
    pub quest_remote_relative: String,
    pub windows_device_pull_subfolder: String,
    pub expected_files: Vec<String>,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct RuntimePanelLaunchSpec {
    pub protocol_version: String,
    pub session_id: String,
    pub study_id: String,
    pub schema_id: String,
    pub questionnaire_id: String,
    pub open_stage: String,
    pub screen_sequence: Vec<String>,
    pub condition_number: i32,
    pub participant_ref: String,
    pub caller_package_name: String,
    pub caller_app_version: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub questionnaire_state: Option<RuntimeQuestionnaireStateSpec>,
}

impl Default for RuntimePanelLaunchSpec {
    fn default() -> Self {
        Self {
            protocol_version: PANEL_PROTOCOL_VERSION.to_string(),
            session_id: String::new(),
            study_id: String::new(),
            schema_id: String::new(),
            questionnaire_id: String::new(),
            open_stage: String::new(),
            screen_sequence: Vec::new(),
            condition_number: -1,
            participant_ref: String::new(),
            caller_package_name: String::new(),
            caller_app_version: String::new(),
            questionnaire_state: None,
        }
    }
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct RuntimeQuestionnaireStateSpec {
    pub language_code: String,
    pub condition_id: String,
    pub condition_label: String,
    pub operator_stage: String,
    pub condition_index: i32,
}

impl Default for RuntimeQuestionnaireStateSpec {
    fn default() -> Self {
        Self {
            language_code: String::new(),
            condition_id: String::new(),
            condition_label: String::new(),
            operator_stage: String::new(),
            condition_index: -1,
        }
    }
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

pub fn runtime_session_remote_relative_from_session_dir(session_dir: &str) -> Option<String> {
    let normalized = session_dir.trim().replace('\\', "/");
    let trimmed = normalized.trim_matches('/');
    if trimmed.is_empty() {
        return None;
    }

    let segments = trimmed.split('/').collect::<Vec<_>>();
    if segments
        .iter()
        .any(|segment| segment.is_empty() || *segment == "." || *segment == "..")
    {
        return None;
    }

    for index in 0..segments.len().saturating_sub(1) {
        if segments[index] == "files" && segments[index + 1] == "runtime_csv" {
            return Some(segments[index..].join("/"));
        }
    }

    None
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

    #[test]
    fn runtime_remote_relative_accepts_external_android_session_dir() {
        assert_eq!(
            runtime_session_remote_relative_from_session_dir(
                "/storage/emulated/0/Android/data/com.example.peripersonal/files/runtime_csv/participant-P001/session-001",
            )
            .as_deref(),
            Some("files/runtime_csv/participant-P001/session-001")
        );
    }

    #[test]
    fn runtime_remote_relative_accepts_internal_android_session_dir() {
        assert_eq!(
            runtime_session_remote_relative_from_session_dir(
                "/data/user/0/com.example.peripersonal/files/runtime_csv/participant-P001/session-001",
            )
            .as_deref(),
            Some("files/runtime_csv/participant-P001/session-001")
        );
    }

    #[test]
    fn runtime_remote_relative_keeps_already_relative_session_dir() {
        assert_eq!(
            runtime_session_remote_relative_from_session_dir(
                "files\\runtime_csv\\participant-P001\\session-001",
            )
            .as_deref(),
            Some("files/runtime_csv/participant-P001/session-001")
        );
    }

    #[test]
    fn runtime_remote_relative_rejects_unsafe_or_unrelated_dirs() {
        assert!(runtime_session_remote_relative_from_session_dir(
            "files/runtime_csv/participant-P001/../other"
        )
        .is_none());
        assert!(runtime_session_remote_relative_from_session_dir(
            "/storage/emulated/0/Android/data/com.example.peripersonal/files/not_runtime_csv/session-001",
        )
        .is_none());
        assert!(runtime_session_remote_relative_from_session_dir(
            "S:/Unity/Editor/runtime_csv/session-001"
        )
        .is_none());
    }

    #[test]
    fn parses_target_runtime_status_contract() {
        let raw = r#"{
            "protocol_version": "viscereality.peripersonal.operator.v1",
            "target": {
                "runtime_kind": "unity_quest_apk",
                "runtime_package": "com.example.peripersonal",
                "source_scene_path": "Assets/Scenes/Space.unity",
                "bridge_endpoint": "",
                "quest_selector": ""
            },
            "runtime_contract": {
                "operator_protocol": "viscereality.peripersonal.operator.v1",
                "questionnaire_protocol": "quest.questionnaire.v1",
                "questionnaire_result_callback_protocol": "viscereality.peripersonal.questionnaire_result_callback.v1",
                "storage_root": "runtime_csv",
                "session_storage_policy": "app_private_only",
                "operator_transport": "http_loopback_adb_forward",
                "timing_transport": "lsl_sussex_clock_probe",
                "runtime_state_transport": "lsl_runtime_state_readout",
                "runtime_state_lsl_stream_name": "peripersonal_runtime_state",
                "runtime_state_lsl_stream_type": "peripersonal.runtime.state"
            },
            "capabilities": {
                "command_actions": ["start_session", "stop_session", "open_questionnaire"],
                "additive_recording": true,
                "app_private_session_bundle": true,
                "explicit_pull_required": true,
                "questionnaire_panel_launch": true,
                "questionnaire_result_callback_ingest": true,
                "lsl_clock_alignment": true,
                "lsl_runtime_state_readout": true
            },
            "bridge": {
                "app": "peripersonal-unity-quest-apk",
                "version": "1.0",
                "device_label": "quest-lab"
            },
            "foreground": {
                "xr_app_foreground": true,
                "panel_foreground": false,
                "foreground_package": "com.example.peripersonal",
                "foreground_activity": "",
                "questionnaire_id": "",
                "open_stage": ""
            },
            "last_command": null,
            "last_result": null,
            "message": "Peripersonal Unity bridge ready.",
            "recording_active": true,
            "session_dir": "/data/user/0/com.example.peripersonal/files/runtime_csv/participant-P001/session-001"
        }"#;

        let status: BridgeStatusResponse = serde_json::from_str(raw).unwrap();
        let target = status.target.as_ref().unwrap();
        let contract = status.runtime_contract.as_ref().unwrap();
        let capabilities = status.capabilities.as_ref().unwrap();

        assert_eq!(target.runtime_kind.as_deref(), Some("unity_quest_apk"));
        assert_eq!(
            target.runtime_package.as_deref(),
            Some("com.example.peripersonal")
        );
        assert_eq!(
            target.source_scene_path.as_deref(),
            Some("Assets/Scenes/Space.unity")
        );
        assert_eq!(
            contract.operator_protocol.as_deref(),
            Some("viscereality.peripersonal.operator.v1")
        );
        assert_eq!(
            contract.runtime_state_lsl_stream_name.as_deref(),
            Some("peripersonal_runtime_state")
        );
        assert!(capabilities
            .command_actions
            .contains(&"open_questionnaire".to_string()));
        assert_eq!(capabilities.explicit_pull_required, Some(true));
        assert_eq!(capabilities.lsl_runtime_state_readout, Some(true));
        assert_eq!(
            status.runtime_summary().as_deref(),
            Some(
                "Runtime: unity_quest_apk / com.example.peripersonal; scene: Assets/Scenes/Space.unity; recording: active; questionnaire: available"
            )
        );
    }

    #[test]
    fn runtime_status_preflight_accepts_matching_contract() {
        let status: BridgeStatusResponse = serde_json::from_str(r#"{
            "protocol_version": "viscereality.peripersonal.operator.v1",
            "target": {
                "runtime_kind": "unity_quest_apk",
                "runtime_package": "com.example.peripersonal",
                "source_scene_path": "Assets/Scenes/Space.unity"
            },
            "runtime_contract": {
                "operator_protocol": "viscereality.peripersonal.operator.v1"
            },
            "capabilities": {
                "command_actions": ["start_session", "stop_session", "open_questionnaire", "pull_session"],
                "app_private_session_bundle": true,
                "explicit_pull_required": true,
                "questionnaire_panel_launch": true,
                "questionnaire_result_callback_ingest": true,
                "lsl_clock_alignment": true
            },
            "foreground": {}
        }"#).unwrap();
        let expectation = RuntimeStatusExpectation {
            runtime_kind: Some("unity_quest_apk".to_string()),
            runtime_package: Some("com.example.peripersonal".to_string()),
            source_scene_path: Some("Assets/Scenes/Space.unity".to_string()),
            operator_protocol: Some("viscereality.peripersonal.operator.v1".to_string()),
            required_actions: vec![
                "start_session".to_string(),
                "open_questionnaire".to_string(),
            ],
            require_app_private_session_bundle: true,
            require_explicit_pull: true,
            require_questionnaire_panel_launch: true,
            require_questionnaire_result_callback_ingest: true,
            require_lsl_clock_alignment: true,
        };

        assert!(validate_runtime_status(&status, &expectation).is_empty());
    }

    #[test]
    fn runtime_status_preflight_reports_mismatches() {
        let status: BridgeStatusResponse = serde_json::from_str(
            r#"{
            "protocol_version": "wrong.protocol",
            "target": {
                "runtime_kind": "other_runtime",
                "runtime_package": "com.example.other",
                "source_scene_path": "Assets/Scenes/Other.unity"
            },
            "capabilities": {
                "command_actions": ["start_session"],
                "explicit_pull_required": false
            },
            "foreground": {}
        }"#,
        )
        .unwrap();
        let expectation = RuntimeStatusExpectation {
            runtime_kind: Some("unity_quest_apk".to_string()),
            runtime_package: Some("com.example.peripersonal".to_string()),
            source_scene_path: Some("Assets/Scenes/Space.unity".to_string()),
            operator_protocol: Some("viscereality.peripersonal.operator.v1".to_string()),
            required_actions: vec!["open_questionnaire".to_string()],
            require_explicit_pull: true,
            require_questionnaire_panel_launch: true,
            ..RuntimeStatusExpectation::default()
        };
        let issues = validate_runtime_status(&status, &expectation);

        assert!(issues
            .iter()
            .any(|issue| issue.contains("runtime kind mismatch")));
        assert!(issues
            .iter()
            .any(|issue| issue.contains("runtime package mismatch")));
        assert!(issues
            .iter()
            .any(|issue| issue.contains("source scene mismatch")));
        assert!(issues
            .iter()
            .any(|issue| issue.contains("operator protocol mismatch")));
        assert!(issues.iter().any(|issue| issue.contains("required action")));
        assert!(issues
            .iter()
            .any(|issue| issue.contains("explicit pull/export")));
        assert!(issues
            .iter()
            .any(|issue| issue.contains("questionnaire panel launch")));
    }

    #[test]
    fn runtime_start_session_uses_generic_target_fields() {
        let request = RuntimeOperatorCommandRequest::start_session(
            "cmd-start",
            DEFAULT_RUNTIME_OPERATOR_PROTOCOL_VERSION,
            RuntimeTargetSpec::new(DEFAULT_RUNTIME_KIND, "example.package", "", ""),
            RuntimeSessionSpec {
                study_id: "downstream-study".to_string(),
                session_id: "session-1".to_string(),
                participant_ref: "P001".to_string(),
                condition_id: "condition-a".to_string(),
                language_code: "en".to_string(),
                ..RuntimeSessionSpec::default()
            },
            RuntimeProvenanceSpec {
                unity_editor: "6000.x".to_string(),
                runtime_build_tag: "apk-condition-a".to_string(),
                source_scene_path: "Assets/Scenes/Space.unity".to_string(),
                source_commit: "abc123".to_string(),
                ..RuntimeProvenanceSpec::default()
            },
        );

        assert_eq!(request.action, RuntimeOperatorAction::StartSession);
        assert_eq!(request.target.runtime_kind, DEFAULT_RUNTIME_KIND);
        assert_eq!(request.session.session_id, "session-1");
        assert_eq!(
            request.runtime_provenance.runtime_build_tag,
            "apk-condition-a"
        );
        assert_eq!(
            request.runtime_provenance.source_scene_path,
            "Assets/Scenes/Space.unity"
        );
        assert_eq!(request.runtime_provenance.source_commit, "abc123");
    }

    #[test]
    fn runtime_marker_command_serializes_snake_case_action() {
        let request = RuntimeOperatorCommandRequest::mark_timing_event(
            "cmd-marker",
            DEFAULT_RUNTIME_OPERATOR_PROTOCOL_VERSION,
            RuntimeTargetSpec::new(DEFAULT_RUNTIME_KIND, "", "", ""),
            "session-1",
            "stage-start",
            "operator pressed marker",
        );
        let json = serde_json::to_string(&request).unwrap();

        assert!(json.contains("\"action\":\"mark_timing_event\""));
        assert!(json.contains("\"marker_name\":\"stage-start\""));
    }

    #[test]
    fn runtime_open_questionnaire_serializes_panel_request() {
        let request = RuntimeOperatorCommandRequest::open_questionnaire(
            "cmd-panel",
            DEFAULT_RUNTIME_OPERATOR_PROTOCOL_VERSION,
            RuntimeTargetSpec::new(DEFAULT_RUNTIME_KIND, "example.package", "", ""),
            RuntimePanelLaunchSpec {
                protocol_version: PANEL_PROTOCOL_VERSION.to_string(),
                session_id: "session-1".to_string(),
                study_id: "downstream-study".to_string(),
                schema_id: "questionnaire-v1".to_string(),
                questionnaire_id: "questionnaire-v1".to_string(),
                open_stage: "study:stage".to_string(),
                screen_sequence: vec!["study:stage".to_string()],
                condition_number: 1,
                participant_ref: "P001".to_string(),
                caller_package_name: "example.package".to_string(),
                caller_app_version: "1.0".to_string(),
                questionnaire_state: Some(RuntimeQuestionnaireStateSpec {
                    language_code: "en".to_string(),
                    condition_id: "condition-a".to_string(),
                    condition_index: 1,
                    ..RuntimeQuestionnaireStateSpec::default()
                }),
            },
        );
        let json = serde_json::to_string(&request).unwrap();

        assert_eq!(request.action, RuntimeOperatorAction::OpenQuestionnaire);
        assert!(json.contains("\"action\":\"open_questionnaire\""));
        assert!(json.contains("\"panel_request\""));
        assert!(json.contains("\"questionnaire_id\":\"questionnaire-v1\""));
    }

    #[test]
    fn runtime_pull_session_serializes_export_request() {
        let request = RuntimeOperatorCommandRequest::pull_session(
            "cmd-pull",
            DEFAULT_RUNTIME_OPERATOR_PROTOCOL_VERSION,
            RuntimeTargetSpec::new(DEFAULT_RUNTIME_KIND, "example.package", "", ""),
            "session-1",
            RuntimeExportRequestSpec {
                pull_device_session: true,
                quest_storage_policy: "app_private_only".to_string(),
                windows_storage_policy: "explicit_pull_only".to_string(),
                quest_package: "example.package".to_string(),
                quest_remote_relative: "files/runtime_csv/participant-P001/session-1".to_string(),
                windows_device_pull_subfolder: "device-session-pull".to_string(),
                expected_files: vec!["questionnaire_results.jsonl".to_string()],
            },
        );
        let json = serde_json::to_string(&request).unwrap();

        assert_eq!(request.action, RuntimeOperatorAction::PullSession);
        assert!(json.contains("\"action\":\"pull_session\""));
        assert!(json.contains("\"export_request\""));
        assert!(json.contains(
            "\"quest_remote_relative\":\"files/runtime_csv/participant-P001/session-1\""
        ));
    }
}
