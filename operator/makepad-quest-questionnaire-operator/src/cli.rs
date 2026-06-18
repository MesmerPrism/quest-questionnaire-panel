use std::fs;
use std::io::{Read, Write};
use std::net::TcpStream;
use std::path::{Path, PathBuf};
use std::thread;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};

use crate::device;
use crate::protocol::{
    block_by_number, endpoint_url, validate_runtime_status, BridgeStatusResponse,
    OperatorCommandRequest, RuntimeExportRequestSpec, RuntimeOperatorCommandRequest,
    RuntimePanelLaunchSpec, RuntimeProvenanceSpec, RuntimeQuestionnaireStateSpec,
    RuntimeSessionSpec, RuntimeStatusExpectation, RuntimeTargetSpec, DEFAULT_RUNTIME_KIND,
    DEFAULT_RUNTIME_OPERATOR_PROTOCOL_VERSION, PANEL_PROTOCOL_VERSION,
};

const DEFAULT_ENDPOINT: &str = "http://127.0.0.1:8787";
const DEFAULT_RUNTIME_EXPORT_REMOTE_RELATIVE: &str = "files/runtime_csv";
const DEFAULT_RUNTIME_EXPORT_SUBFOLDER: &str = "device-session-pull";
const DEFAULT_RUNTIME_EXPORT_EXPECTED_FILES: &[&str] = &[
    "session_events.csv",
    "signals_long.csv",
    "breathing_trace.csv",
    "clock_alignment_samples.csv",
    "timing_markers.csv",
    "questionnaire_results.jsonl",
    "session_settings.json",
    "session_snapshot.json",
];

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct RuntimeCommonArgs {
    pub endpoint: String,
    pub protocol_version: String,
    pub target_runtime_kind: String,
    pub target_runtime_package: String,
    pub bridge_endpoint: String,
    pub quest_selector: String,
    pub command_id: Option<String>,
    pub command_name: Option<String>,
    pub audit_dir: Option<PathBuf>,
}

impl Default for RuntimeCommonArgs {
    fn default() -> Self {
        Self {
            endpoint: DEFAULT_ENDPOINT.to_string(),
            protocol_version: DEFAULT_RUNTIME_OPERATOR_PROTOCOL_VERSION.to_string(),
            target_runtime_kind: DEFAULT_RUNTIME_KIND.to_string(),
            target_runtime_package: String::new(),
            bridge_endpoint: String::new(),
            quest_selector: String::new(),
            command_id: None,
            command_name: None,
            audit_dir: None,
        }
    }
}

impl RuntimeCommonArgs {
    fn command_id_or_generated(&self, prefix: &str) -> String {
        self.command_id
            .as_deref()
            .filter(|value| !value.trim().is_empty())
            .map(str::to_string)
            .unwrap_or_else(|| generated_command_id(prefix))
    }

    fn target(&self) -> RuntimeTargetSpec {
        RuntimeTargetSpec::new(
            self.target_runtime_kind.clone(),
            self.target_runtime_package.clone(),
            if self.bridge_endpoint.trim().is_empty() {
                self.endpoint.clone()
            } else {
                self.bridge_endpoint.clone()
            },
            self.quest_selector.clone(),
        )
    }

    fn apply_command_name(&self, body: &mut RuntimeOperatorCommandRequest) {
        if let Some(command_name) = self
            .command_name
            .as_deref()
            .filter(|value| !value.trim().is_empty())
        {
            body.command_name = command_name.to_string();
        }
    }
}

#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct RuntimeSessionArgs {
    pub study_id: String,
    pub session_id: String,
    pub participant_ref: String,
    pub dataset_id: String,
    pub condition_id: String,
    pub language_code: String,
}

impl RuntimeSessionArgs {
    fn to_spec(&self) -> RuntimeSessionSpec {
        RuntimeSessionSpec {
            study_id: self.study_id.clone(),
            session_id: self.session_id.clone(),
            participant_ref: self.participant_ref.clone(),
            dataset_id: self.dataset_id.clone(),
            condition_id: self.condition_id.clone(),
            language_code: if self.language_code.trim().is_empty() {
                "en".to_string()
            } else {
                self.language_code.clone()
            },
        }
    }
}

#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct RuntimeProvenanceArgs {
    pub unity_project: String,
    pub unity_editor: String,
    pub apk_sha256: String,
    pub app_version_name: String,
    pub app_version_code: i32,
    pub source_commit: String,
}

impl RuntimeProvenanceArgs {
    fn to_spec(&self) -> RuntimeProvenanceSpec {
        RuntimeProvenanceSpec {
            unity_project: self.unity_project.clone(),
            unity_editor: self.unity_editor.clone(),
            apk_sha256: self.apk_sha256.clone(),
            app_version_name: self.app_version_name.clone(),
            app_version_code: self.app_version_code,
            source_commit: self.source_commit.clone(),
        }
    }
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct RuntimeExportArgs {
    pub quest_package: String,
    pub quest_remote_relative: String,
    pub windows_device_pull_subfolder: String,
    pub expected_files: Vec<String>,
}

impl Default for RuntimeExportArgs {
    fn default() -> Self {
        Self {
            quest_package: String::new(),
            quest_remote_relative: DEFAULT_RUNTIME_EXPORT_REMOTE_RELATIVE.to_string(),
            windows_device_pull_subfolder: DEFAULT_RUNTIME_EXPORT_SUBFOLDER.to_string(),
            expected_files: DEFAULT_RUNTIME_EXPORT_EXPECTED_FILES
                .iter()
                .map(|value| (*value).to_string())
                .collect(),
        }
    }
}

impl RuntimeExportArgs {
    fn to_spec(&self, fallback_package: &str) -> RuntimeExportRequestSpec {
        RuntimeExportRequestSpec {
            pull_device_session: true,
            quest_storage_policy: "app_private_only".to_string(),
            windows_storage_policy: "explicit_pull_only".to_string(),
            quest_package: if self.quest_package.trim().is_empty() {
                fallback_package.to_string()
            } else {
                self.quest_package.clone()
            },
            quest_remote_relative: self.quest_remote_relative.clone(),
            windows_device_pull_subfolder: self.windows_device_pull_subfolder.clone(),
            expected_files: self.expected_files.clone(),
        }
    }
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub enum CliCommand {
    Status {
        endpoint: String,
    },
    PreflightRuntime {
        common: RuntimeCommonArgs,
        required_actions: Vec<String>,
        require_app_private_session_bundle: bool,
        require_explicit_pull: bool,
        require_questionnaire_panel_launch: bool,
        require_questionnaire_result_callback_ingest: bool,
        require_lsl_clock_alignment: bool,
        json: bool,
    },
    ToolingStatus {
        json: bool,
    },
    Devices {
        json: bool,
    },
    DeviceStatus {
        serial: String,
        json: bool,
    },
    BridgeForward {
        serial: String,
        host_port: u16,
        device_port: u16,
        json: bool,
    },
    InstallPanel {
        serial: String,
        apk: PathBuf,
        json: bool,
    },
    InstallTargetApk {
        serial: String,
        apk: PathBuf,
        json: bool,
    },
    LaunchTargetRuntime {
        serial: String,
        package_name: String,
        activity: Option<String>,
        json: bool,
    },
    PullTargetSession {
        serial: String,
        package_name: String,
        remote_relative: String,
        out: PathBuf,
        json: bool,
    },
    OpenBlock {
        endpoint: String,
        block: u8,
        session_id: String,
        participant_ref: String,
        language_code: String,
        command_id: Option<String>,
        debug_auto_submit: bool,
        debug_command_script: Option<String>,
        debug_command_interval_ms: Option<u32>,
    },
    Dismiss {
        endpoint: String,
        session_id: String,
        command_id: Option<String>,
    },
    StartSession {
        common: RuntimeCommonArgs,
        session: RuntimeSessionArgs,
        provenance: RuntimeProvenanceArgs,
    },
    StopSession {
        common: RuntimeCommonArgs,
        session_id: String,
    },
    PullSession {
        common: RuntimeCommonArgs,
        session_id: String,
        export: RuntimeExportArgs,
    },
    MarkTimingEvent {
        common: RuntimeCommonArgs,
        session_id: String,
        marker_name: String,
        marker_detail: String,
    },
    OpenQuestionnaire {
        common: RuntimeCommonArgs,
        panel_request: RuntimePanelLaunchSpec,
    },
    PostCommand {
        endpoint: String,
        file: PathBuf,
        audit_dir: Option<PathBuf>,
    },
    ProofRun {
        endpoint: String,
        block: u8,
        session_id: String,
        participant_ref: String,
        language_code: String,
        out: PathBuf,
        timeout_ms: u64,
        poll_interval_ms: u64,
        debug_auto_submit: bool,
        debug_command_script: Option<String>,
        debug_command_interval_ms: Option<u32>,
    },
    Help,
}

pub fn main() -> i32 {
    match run(std::env::args().skip(1).collect()) {
        Ok(output) => {
            println!("{output}");
            0
        }
        Err(message) => {
            eprintln!("{message}");
            2
        }
    }
}

pub fn run(args: Vec<String>) -> Result<String, String> {
    match parse_args(args)? {
        CliCommand::Status { endpoint } => get_status(&endpoint),
        CliCommand::PreflightRuntime {
            common,
            required_actions,
            require_app_private_session_bundle,
            require_explicit_pull,
            require_questionnaire_panel_launch,
            require_questionnaire_result_callback_ingest,
            require_lsl_clock_alignment,
            json,
        } => preflight_runtime(
            common,
            required_actions,
            require_app_private_session_bundle,
            require_explicit_pull,
            require_questionnaire_panel_launch,
            require_questionnaire_result_callback_ingest,
            require_lsl_clock_alignment,
            json,
        ),
        CliCommand::ToolingStatus { json } => tooling_status(json),
        CliCommand::Devices { json } => devices(json),
        CliCommand::DeviceStatus { serial, json } => device_status(&serial, json),
        CliCommand::BridgeForward {
            serial,
            host_port,
            device_port,
            json,
        } => bridge_forward(&serial, host_port, device_port, json),
        CliCommand::InstallPanel { serial, apk, json } => install_panel(&serial, &apk, json),
        CliCommand::InstallTargetApk { serial, apk, json } => {
            install_target_apk(&serial, &apk, json)
        }
        CliCommand::LaunchTargetRuntime {
            serial,
            package_name,
            activity,
            json,
        } => launch_target_runtime(&serial, &package_name, activity.as_deref(), json),
        CliCommand::PullTargetSession {
            serial,
            package_name,
            remote_relative,
            out,
            json,
        } => pull_target_session(&serial, &package_name, &remote_relative, &out, json),
        CliCommand::OpenBlock {
            endpoint,
            block,
            session_id,
            participant_ref,
            language_code,
            command_id,
            debug_auto_submit,
            debug_command_script,
            debug_command_interval_ms,
        } => open_block(
            &endpoint,
            block,
            &session_id,
            &participant_ref,
            &language_code,
            command_id.as_deref(),
            debug_auto_submit,
            debug_command_script.as_deref(),
            debug_command_interval_ms,
        ),
        CliCommand::Dismiss {
            endpoint,
            session_id,
            command_id,
        } => dismiss(&endpoint, &session_id, command_id.as_deref()),
        CliCommand::StartSession {
            common,
            session,
            provenance,
        } => start_session(common, session, provenance),
        CliCommand::StopSession { common, session_id } => stop_session(common, &session_id),
        CliCommand::PullSession {
            common,
            session_id,
            export,
        } => pull_session(common, &session_id, export),
        CliCommand::MarkTimingEvent {
            common,
            session_id,
            marker_name,
            marker_detail,
        } => mark_timing_event(common, &session_id, &marker_name, &marker_detail),
        CliCommand::OpenQuestionnaire {
            common,
            panel_request,
        } => open_questionnaire(common, panel_request),
        CliCommand::PostCommand {
            endpoint,
            file,
            audit_dir,
        } => post_command_file(&endpoint, &file, audit_dir.as_deref()),
        CliCommand::ProofRun {
            endpoint,
            block,
            session_id,
            participant_ref,
            language_code,
            out,
            timeout_ms,
            poll_interval_ms,
            debug_auto_submit,
            debug_command_script,
            debug_command_interval_ms,
        } => proof_run(ProofRunOptions {
            endpoint,
            block,
            session_id,
            participant_ref,
            language_code,
            out,
            timeout_ms,
            poll_interval_ms,
            debug_auto_submit,
            debug_command_script,
            debug_command_interval_ms,
        }),
        CliCommand::Help => Ok(help_text()),
    }
}

pub fn tooling_status(json: bool) -> Result<String, String> {
    let status = device::tooling_status();
    if json {
        serde_json::to_string_pretty(&status).map_err(|err| err.to_string())
    } else {
        Ok(device::format_tooling_text(&status))
    }
}

pub fn devices(json: bool) -> Result<String, String> {
    let devices = device::list_devices()?;
    if json {
        serde_json::to_string_pretty(&devices).map_err(|err| err.to_string())
    } else {
        Ok(device::format_devices_text(&devices))
    }
}

pub fn device_status(serial: &str, json: bool) -> Result<String, String> {
    let snapshot = device::get_device_snapshot(serial)?;
    if json {
        serde_json::to_string_pretty(&snapshot).map_err(|err| err.to_string())
    } else {
        Ok(device::format_snapshot_text(&snapshot))
    }
}

pub fn bridge_forward(
    serial: &str,
    host_port: u16,
    device_port: u16,
    json: bool,
) -> Result<String, String> {
    let run = device::forward_bridge(serial, host_port, device_port)?;
    if json {
        serde_json::to_string_pretty(&run).map_err(|err| err.to_string())
    } else {
        Ok(format!(
            "Forwarded http://127.0.0.1:{host_port} to Quest tcp:{device_port}."
        ))
    }
}

pub fn install_panel(serial: &str, apk: &Path, json: bool) -> Result<String, String> {
    let run = device::install_apk(serial, apk)?;
    if json {
        serde_json::to_string_pretty(&run).map_err(|err| err.to_string())
    } else {
        Ok(format!(
            "Installed panel APK on {serial}: {}",
            apk.display()
        ))
    }
}

pub fn install_target_apk(serial: &str, apk: &Path, json: bool) -> Result<String, String> {
    let run = device::install_apk(serial, apk)?;
    if json {
        serde_json::to_string_pretty(&run).map_err(|err| err.to_string())
    } else {
        Ok(format!(
            "Installed target APK on {serial}: {}",
            apk.display()
        ))
    }
}

pub fn launch_target_runtime(
    serial: &str,
    package_name: &str,
    activity: Option<&str>,
    json: bool,
) -> Result<String, String> {
    let run = device::launch_package(serial, package_name, activity)?;
    if json {
        serde_json::to_string_pretty(&run).map_err(|err| err.to_string())
    } else {
        Ok(format!(
            "Launch requested for target runtime {package_name}."
        ))
    }
}

pub fn pull_target_session(
    serial: &str,
    package_name: &str,
    remote_relative: &str,
    out: &Path,
    json: bool,
) -> Result<String, String> {
    let run = device::pull_target_session(serial, package_name, remote_relative, out)?;
    if json {
        serde_json::to_string_pretty(&run).map_err(|err| err.to_string())
    } else {
        Ok(format!(
            "Pulled target session data from {package_name} into {}.",
            out.display()
        ))
    }
}

pub fn get_status(endpoint: &str) -> Result<String, String> {
    let url = endpoint_url(endpoint, "/v1/status")?;
    http_request("GET", &url, None)
}

#[derive(Clone, Debug, Serialize)]
struct RuntimePreflightReport {
    protocol_version: String,
    endpoint: String,
    accepted: bool,
    issues: Vec<String>,
    runtime_summary: Option<String>,
    status: BridgeStatusResponse,
}

pub fn preflight_runtime(
    common: RuntimeCommonArgs,
    required_actions: Vec<String>,
    require_app_private_session_bundle: bool,
    require_explicit_pull: bool,
    require_questionnaire_panel_launch: bool,
    require_questionnaire_result_callback_ingest: bool,
    require_lsl_clock_alignment: bool,
    json: bool,
) -> Result<String, String> {
    let raw = get_status(&common.endpoint)?;
    let status = parse_status_json(&raw)?;
    let expectation = RuntimeStatusExpectation {
        runtime_kind: Some(common.target_runtime_kind.clone()),
        runtime_package: non_empty_string(common.target_runtime_package.clone()),
        operator_protocol: Some(common.protocol_version.clone()),
        required_actions,
        require_app_private_session_bundle,
        require_explicit_pull,
        require_questionnaire_panel_launch,
        require_questionnaire_result_callback_ingest,
        require_lsl_clock_alignment,
    };
    let issues = validate_runtime_status(&status, &expectation);
    let accepted = issues.is_empty();
    let runtime_summary = status.runtime_summary();
    let report = RuntimePreflightReport {
        protocol_version: "quest.questionnaire.operator.runtime-preflight.v1".to_string(),
        endpoint: common.endpoint,
        accepted,
        issues,
        runtime_summary,
        status,
    };

    if accepted {
        if json {
            serde_json::to_string_pretty(&report).map_err(|err| err.to_string())
        } else {
            Ok(format!(
                "Target runtime preflight passed. {}",
                report
                    .runtime_summary
                    .as_deref()
                    .unwrap_or("Runtime status matched requested expectations.")
            ))
        }
    } else if json {
        Err(serde_json::to_string_pretty(&report).map_err(|err| err.to_string())?)
    } else {
        Err(format!(
            "Target runtime preflight failed:\n- {}",
            report.issues.join("\n- ")
        ))
    }
}

pub fn open_block(
    endpoint: &str,
    block: u8,
    session_id: &str,
    participant_ref: &str,
    language_code: &str,
    command_id: Option<&str>,
    debug_auto_submit: bool,
    debug_command_script: Option<&str>,
    debug_command_interval_ms: Option<u32>,
) -> Result<String, String> {
    let block = block_by_number(block).ok_or_else(|| "Block must be 1, 2, or 3".to_string())?;
    let command_id = command_id
        .filter(|value| !value.trim().is_empty())
        .map(str::to_string)
        .unwrap_or_else(|| generated_command_id("operator-cli-open"));
    let mut body = OperatorCommandRequest::open_block(
        command_id,
        block,
        session_id.to_string(),
        participant_ref.to_string(),
        language_code.to_string(),
    );
    if debug_auto_submit {
        body.debug_auto_submit = Some(true);
    }
    if let Some(script) = debug_command_script.filter(|value| !value.trim().is_empty()) {
        body.debug_command_script = Some(script.to_string());
    }
    if let Some(interval) = debug_command_interval_ms {
        body.debug_command_interval_ms = Some(interval);
    }
    post_command(endpoint, body)
}

pub fn dismiss(
    endpoint: &str,
    session_id: &str,
    command_id: Option<&str>,
) -> Result<String, String> {
    let command_id = command_id
        .filter(|value| !value.trim().is_empty())
        .map(str::to_string)
        .unwrap_or_else(|| generated_command_id("operator-cli-dismiss"));
    post_command(
        endpoint,
        OperatorCommandRequest::dismiss(command_id, session_id.to_string()),
    )
}

pub fn start_session(
    common: RuntimeCommonArgs,
    session: RuntimeSessionArgs,
    provenance: RuntimeProvenanceArgs,
) -> Result<String, String> {
    if session.session_id.trim().is_empty() {
        return Err("start-session requires --session-id".to_string());
    }
    if session.participant_ref.trim().is_empty() {
        return Err("start-session requires --participant-ref".to_string());
    }

    let mut body = RuntimeOperatorCommandRequest::start_session(
        common.command_id_or_generated("operator-cli-start-session"),
        common.protocol_version.clone(),
        common.target(),
        session.to_spec(),
        provenance.to_spec(),
    );
    common.apply_command_name(&mut body);
    post_runtime_command(&common.endpoint, body, common.audit_dir.as_deref())
}

pub fn stop_session(common: RuntimeCommonArgs, session_id: &str) -> Result<String, String> {
    if session_id.trim().is_empty() {
        return Err("stop-session requires --session-id".to_string());
    }

    let mut body = RuntimeOperatorCommandRequest::stop_session(
        common.command_id_or_generated("operator-cli-stop-session"),
        common.protocol_version.clone(),
        common.target(),
        session_id.to_string(),
    );
    common.apply_command_name(&mut body);
    post_runtime_command(&common.endpoint, body, common.audit_dir.as_deref())
}

pub fn pull_session(
    common: RuntimeCommonArgs,
    session_id: &str,
    export: RuntimeExportArgs,
) -> Result<String, String> {
    if session_id.trim().is_empty() {
        return Err("pull-session requires --session-id".to_string());
    }
    if export.quest_remote_relative.trim().is_empty() {
        return Err("pull-session requires --remote-relative".to_string());
    }
    if !export
        .quest_remote_relative
        .starts_with(DEFAULT_RUNTIME_EXPORT_REMOTE_RELATIVE)
    {
        return Err("pull-session --remote-relative must stay under files/runtime_csv".to_string());
    }
    if export
        .quest_remote_relative
        .split('/')
        .any(|part| part == "..")
    {
        return Err("pull-session --remote-relative must not contain parent traversal".to_string());
    }

    let mut body = RuntimeOperatorCommandRequest::pull_session(
        common.command_id_or_generated("operator-cli-pull-session"),
        common.protocol_version.clone(),
        common.target(),
        session_id.to_string(),
        export.to_spec(&common.target_runtime_package),
    );
    common.apply_command_name(&mut body);
    post_runtime_command(&common.endpoint, body, common.audit_dir.as_deref())
}

pub fn mark_timing_event(
    common: RuntimeCommonArgs,
    session_id: &str,
    marker_name: &str,
    marker_detail: &str,
) -> Result<String, String> {
    if session_id.trim().is_empty() {
        return Err("mark-timing-event requires --session-id".to_string());
    }
    if marker_name.trim().is_empty() {
        return Err("mark-timing-event requires --marker-name".to_string());
    }

    let mut body = RuntimeOperatorCommandRequest::mark_timing_event(
        common.command_id_or_generated("operator-cli-marker"),
        common.protocol_version.clone(),
        common.target(),
        session_id.to_string(),
        marker_name.to_string(),
        marker_detail.to_string(),
    );
    common.apply_command_name(&mut body);
    post_runtime_command(&common.endpoint, body, common.audit_dir.as_deref())
}

pub fn open_questionnaire(
    common: RuntimeCommonArgs,
    mut panel_request: RuntimePanelLaunchSpec,
) -> Result<String, String> {
    if panel_request.session_id.trim().is_empty() {
        return Err("open-questionnaire requires --session-id".to_string());
    }
    if panel_request.study_id.trim().is_empty() {
        return Err("open-questionnaire requires --study-id".to_string());
    }
    if panel_request.schema_id.trim().is_empty() && panel_request.questionnaire_id.trim().is_empty()
    {
        return Err("open-questionnaire requires --questionnaire-id or --schema-id".to_string());
    }
    if panel_request.schema_id.trim().is_empty() {
        panel_request.schema_id = panel_request.questionnaire_id.clone();
    }
    if panel_request.questionnaire_id.trim().is_empty() {
        panel_request.questionnaire_id = panel_request.schema_id.clone();
    }
    if panel_request.open_stage.trim().is_empty() {
        return Err("open-questionnaire requires --open-stage".to_string());
    }
    if panel_request.screen_sequence.is_empty() {
        panel_request
            .screen_sequence
            .push(panel_request.open_stage.clone());
    }
    if panel_request.participant_ref.trim().is_empty() {
        return Err("open-questionnaire requires --participant-ref".to_string());
    }
    if panel_request.caller_package_name.trim().is_empty() {
        panel_request.caller_package_name = common.target_runtime_package.clone();
    }

    let state = panel_request
        .questionnaire_state
        .get_or_insert_with(RuntimeQuestionnaireStateSpec::default);
    if state.language_code.trim().is_empty() {
        state.language_code = "en".to_string();
    }
    if state.condition_index < 0 && panel_request.condition_number >= 0 {
        state.condition_index = panel_request.condition_number;
    }

    let mut body = RuntimeOperatorCommandRequest::open_questionnaire(
        common.command_id_or_generated("operator-cli-open-questionnaire"),
        common.protocol_version.clone(),
        common.target(),
        panel_request,
    );
    common.apply_command_name(&mut body);
    post_runtime_command(&common.endpoint, body, common.audit_dir.as_deref())
}

pub fn post_command_file(
    endpoint: &str,
    file: &Path,
    audit_dir: Option<&Path>,
) -> Result<String, String> {
    let body = fs::read_to_string(file)
        .map_err(|err| format!("Could not read command JSON file {}: {err}", file.display()))?;
    post_raw_command_with_audit(endpoint, &body, audit_dir)
}

#[derive(Clone, Debug, PartialEq, Eq)]
struct ProofRunOptions {
    endpoint: String,
    block: u8,
    session_id: String,
    participant_ref: String,
    language_code: String,
    out: PathBuf,
    timeout_ms: u64,
    poll_interval_ms: u64,
    debug_auto_submit: bool,
    debug_command_script: Option<String>,
    debug_command_interval_ms: Option<u32>,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
struct ProofRunReport {
    protocol_version: String,
    command_id: String,
    endpoint: String,
    block: u8,
    session_id: String,
    participant_ref: String,
    language_code: String,
    started_unix_ms: u128,
    completed_unix_ms: u128,
    accepted: bool,
    observed_panel_foreground: bool,
    observed_result: bool,
    final_message: String,
    pre_status: Option<BridgeStatusResponse>,
    command_response: Option<serde_json::Value>,
    final_status: Option<BridgeStatusResponse>,
    errors: Vec<String>,
}

fn proof_run(options: ProofRunOptions) -> Result<String, String> {
    let block = block_by_number(options.block)
        .ok_or_else(|| "proof-run requires --block 1, 2, or 3".to_string())?;
    let command_id = generated_command_id("operator-proof");
    let started_unix_ms = unix_ms();
    let mut errors = Vec::new();
    let pre_status = get_status(&options.endpoint)
        .and_then(|raw| parse_status_json(&raw))
        .map_err(|err| {
            errors.push(format!("pre-status failed: {err}"));
            err
        })
        .ok();

    let command_raw = open_block(
        &options.endpoint,
        options.block,
        &options.session_id,
        &options.participant_ref,
        &options.language_code,
        Some(&command_id),
        options.debug_auto_submit,
        options.debug_command_script.as_deref(),
        options.debug_command_interval_ms,
    );
    let command_response = match command_raw {
        Ok(raw) => serde_json::from_str::<serde_json::Value>(&raw)
            .map_err(|err| {
                errors.push(format!("command response was not JSON: {err}"));
                err.to_string()
            })
            .ok(),
        Err(err) => {
            errors.push(format!("command failed: {err}"));
            None
        }
    };
    let accepted = command_response
        .as_ref()
        .and_then(|value| value.get("accepted"))
        .and_then(serde_json::Value::as_bool)
        .unwrap_or(false);

    let deadline = Duration::from_millis(options.timeout_ms);
    let interval = Duration::from_millis(options.poll_interval_ms.max(50));
    let poll_start = SystemTime::now();
    let mut final_status = None;
    let mut observed_panel_foreground = false;
    let mut observed_result = false;
    loop {
        match get_status(&options.endpoint).and_then(|raw| parse_status_json(&raw)) {
            Ok(status) => {
                observed_panel_foreground |= status.foreground.panel_foreground == Some(true)
                    && status.foreground.open_stage.as_deref() == Some(block.open_stage);
                observed_result |= status.last_result.is_some();
                final_status = Some(status);
                if observed_result || observed_panel_foreground {
                    break;
                }
            }
            Err(err) => errors.push(format!("poll failed: {err}")),
        }

        let elapsed = poll_start
            .elapsed()
            .unwrap_or_else(|_| Duration::from_millis(options.timeout_ms));
        if elapsed >= deadline {
            errors.push(format!(
                "proof-run timed out after {} ms",
                options.timeout_ms
            ));
            break;
        }
        thread::sleep(interval);
    }

    let completed_unix_ms = unix_ms();
    let final_message = match (accepted, observed_panel_foreground, observed_result) {
        (_, _, true) => "Bridge reported a questionnaire result.".to_string(),
        (true, true, false) => {
            "Command accepted and requested panel stage reached foreground.".to_string()
        }
        (true, false, false) => {
            "Command accepted, but foreground/result confirmation was not observed.".to_string()
        }
        _ => "Command was not accepted.".to_string(),
    };
    let report = ProofRunReport {
        protocol_version: "quest.questionnaire.operator.proof-run.v1".to_string(),
        command_id,
        endpoint: options.endpoint,
        block: options.block,
        session_id: options.session_id,
        participant_ref: options.participant_ref,
        language_code: options.language_code,
        started_unix_ms,
        completed_unix_ms,
        accepted,
        observed_panel_foreground,
        observed_result,
        final_message,
        pre_status,
        command_response,
        final_status,
        errors,
    };

    fs::create_dir_all(&options.out)
        .map_err(|err| format!("Could not create proof output folder: {err}"))?;
    let json_path = options.out.join("questionnaire-proof-run.json");
    let md_path = options.out.join("questionnaire-proof-run.md");
    let json = serde_json::to_string_pretty(&report)
        .map_err(|err| format!("Could not encode proof report: {err}"))?;
    fs::write(&json_path, json).map_err(|err| format!("Could not write proof JSON: {err}"))?;
    fs::write(&md_path, proof_markdown(&report))
        .map_err(|err| format!("Could not write proof Markdown: {err}"))?;
    Ok(format!(
        "{}\nJSON: {}\nMarkdown: {}",
        report.final_message,
        json_path.display(),
        md_path.display()
    ))
}

fn post_command(endpoint: &str, body: OperatorCommandRequest) -> Result<String, String> {
    let body = serde_json::to_string_pretty(&body)
        .map_err(|err| format!("Could not encode command JSON: {err}"))?;
    post_raw_command(endpoint, &body)
}

fn post_runtime_command(
    endpoint: &str,
    body: RuntimeOperatorCommandRequest,
    audit_dir: Option<&Path>,
) -> Result<String, String> {
    let body = serde_json::to_string_pretty(&body)
        .map_err(|err| format!("Could not encode runtime command JSON: {err}"))?;
    post_raw_command_with_audit(endpoint, &body, audit_dir)
}

fn post_raw_command(endpoint: &str, body: &str) -> Result<String, String> {
    let url = endpoint_url(endpoint, "/v1/command")?;
    http_request("POST", &url, Some(body))
}

fn post_raw_command_with_audit(
    endpoint: &str,
    body: &str,
    audit_dir: Option<&Path>,
) -> Result<String, String> {
    let started_unix_ms = unix_ms();
    let result = post_raw_command(endpoint, body);
    let completed_unix_ms = unix_ms();

    if let Some(audit_dir) = audit_dir {
        if let Err(audit_err) = write_command_audit(
            audit_dir,
            endpoint,
            body,
            &result,
            started_unix_ms,
            completed_unix_ms,
        ) {
            return match result {
                Ok(_) => Err(audit_err),
                Err(command_err) => Err(format!("{command_err}\nAudit write failed: {audit_err}")),
            };
        }
    }

    result
}

#[derive(Clone, Debug, Serialize)]
struct CommandAuditRecord {
    protocol_version: String,
    started_unix_ms: u128,
    completed_unix_ms: u128,
    endpoint: String,
    command_id: Option<String>,
    command_name: Option<String>,
    action: Option<String>,
    accepted: Option<bool>,
    request: serde_json::Value,
    response: Option<serde_json::Value>,
    error: Option<String>,
}

fn write_command_audit(
    audit_dir: &Path,
    endpoint: &str,
    request_body: &str,
    result: &Result<String, String>,
    started_unix_ms: u128,
    completed_unix_ms: u128,
) -> Result<(), String> {
    let request = serde_json::from_str::<serde_json::Value>(request_body)
        .unwrap_or_else(|_| serde_json::Value::String(request_body.to_string()));
    let response = result
        .as_ref()
        .ok()
        .and_then(|body| serde_json::from_str::<serde_json::Value>(body).ok());
    let record = CommandAuditRecord {
        protocol_version: "quest.questionnaire.operator.command-audit.v1".to_string(),
        started_unix_ms,
        completed_unix_ms,
        endpoint: endpoint.to_string(),
        command_id: json_string_field(&request, "command_id"),
        command_name: json_string_field(&request, "command_name"),
        action: json_string_field(&request, "action"),
        accepted: response
            .as_ref()
            .and_then(|value| value.get("accepted"))
            .and_then(serde_json::Value::as_bool),
        request,
        response,
        error: result.as_ref().err().cloned(),
    };

    fs::create_dir_all(audit_dir).map_err(|err| {
        format!(
            "Could not create command audit folder {}: {err}",
            audit_dir.display()
        )
    })?;
    let audit_path = audit_dir.join("command_audit.jsonl");
    let mut file = fs::OpenOptions::new()
        .create(true)
        .append(true)
        .open(&audit_path)
        .map_err(|err| {
            format!(
                "Could not open command audit {}: {err}",
                audit_path.display()
            )
        })?;
    let line = serde_json::to_string(&record)
        .map_err(|err| format!("Could not encode command audit record: {err}"))?;
    writeln!(file, "{line}").map_err(|err| {
        format!(
            "Could not write command audit {}: {err}",
            audit_path.display()
        )
    })
}

fn json_string_field(value: &serde_json::Value, field: &str) -> Option<String> {
    value
        .get(field)
        .and_then(serde_json::Value::as_str)
        .filter(|value| !value.trim().is_empty())
        .map(str::to_string)
}

fn non_empty_string(value: String) -> Option<String> {
    if value.trim().is_empty() {
        None
    } else {
        Some(value)
    }
}

fn parse_status_json(raw: &str) -> Result<BridgeStatusResponse, String> {
    serde_json::from_str::<BridgeStatusResponse>(raw)
        .map_err(|err| format!("Could not parse bridge status JSON: {err}"))
}

fn unix_ms() -> u128 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|duration| duration.as_millis())
        .unwrap_or_default()
}

fn proof_markdown(report: &ProofRunReport) -> String {
    [
        "# Quest Questionnaire Proof Run".to_string(),
        String::new(),
        format!("- command: `{}`", report.command_id),
        format!("- block: `{}`", report.block),
        format!("- session: `{}`", report.session_id),
        format!("- participant ref: `{}`", report.participant_ref),
        format!("- accepted: `{}`", report.accepted),
        format!(
            "- observed panel foreground: `{}`",
            report.observed_panel_foreground
        ),
        format!("- observed result: `{}`", report.observed_result),
        format!("- summary: {}", report.final_message),
        String::new(),
        "Errors:".to_string(),
        if report.errors.is_empty() {
            "- none".to_string()
        } else {
            report
                .errors
                .iter()
                .map(|error| format!("- {error}"))
                .collect::<Vec<_>>()
                .join("\n")
        },
    ]
    .join("\n")
}

pub fn parse_args(args: Vec<String>) -> Result<CliCommand, String> {
    let mut iter = args.into_iter();
    let Some(command) = iter.next() else {
        return Ok(CliCommand::Help);
    };
    match command.as_str() {
        "-h" | "--help" | "help" => Ok(CliCommand::Help),
        "status" => {
            let mut endpoint = DEFAULT_ENDPOINT.to_string();
            while let Some(arg) = iter.next() {
                match arg.as_str() {
                    "--endpoint" => endpoint = next_value(&mut iter, "--endpoint")?,
                    "-h" | "--help" => return Ok(CliCommand::Help),
                    _ => return Err(format!("Unknown status argument: {arg}")),
                }
            }
            Ok(CliCommand::Status { endpoint })
        }
        "preflight-runtime" => {
            let mut common = RuntimeCommonArgs::default();
            let mut required_actions = Vec::new();
            let mut require_app_private_session_bundle = false;
            let mut require_explicit_pull = false;
            let mut require_questionnaire_panel_launch = false;
            let mut require_questionnaire_result_callback_ingest = false;
            let mut require_lsl_clock_alignment = false;
            let mut json = false;
            while let Some(arg) = iter.next() {
                if parse_runtime_common_arg(&arg, &mut iter, &mut common)? {
                    continue;
                }
                match arg.as_str() {
                    "--require-action" => {
                        let value = next_value(&mut iter, "--require-action")?;
                        push_non_empty(&mut required_actions, value);
                    }
                    "--require-actions" => {
                        let value = next_value(&mut iter, "--require-actions")?;
                        extend_comma_list(&value, &mut required_actions);
                    }
                    "--require-app-private-session-bundle" => {
                        require_app_private_session_bundle = true
                    }
                    "--require-explicit-pull" => require_explicit_pull = true,
                    "--require-questionnaire-panel" => require_questionnaire_panel_launch = true,
                    "--require-questionnaire-result-callback" => {
                        require_questionnaire_result_callback_ingest = true
                    }
                    "--require-lsl-clock-alignment" => require_lsl_clock_alignment = true,
                    "--json" => json = true,
                    "-h" | "--help" => return Ok(CliCommand::Help),
                    _ => return Err(format!("Unknown preflight-runtime argument: {arg}")),
                }
            }
            Ok(CliCommand::PreflightRuntime {
                common,
                required_actions,
                require_app_private_session_bundle,
                require_explicit_pull,
                require_questionnaire_panel_launch,
                require_questionnaire_result_callback_ingest,
                require_lsl_clock_alignment,
                json,
            })
        }
        "tooling-status" => {
            let mut json = false;
            while let Some(arg) = iter.next() {
                match arg.as_str() {
                    "--json" => json = true,
                    "-h" | "--help" => return Ok(CliCommand::Help),
                    _ => return Err(format!("Unknown tooling-status argument: {arg}")),
                }
            }
            Ok(CliCommand::ToolingStatus { json })
        }
        "devices" => {
            let mut json = false;
            while let Some(arg) = iter.next() {
                match arg.as_str() {
                    "--json" => json = true,
                    "-h" | "--help" => return Ok(CliCommand::Help),
                    _ => return Err(format!("Unknown devices argument: {arg}")),
                }
            }
            Ok(CliCommand::Devices { json })
        }
        "device-status" => {
            let mut serial: Option<String> = None;
            let mut json = false;
            while let Some(arg) = iter.next() {
                match arg.as_str() {
                    "--serial" => serial = Some(next_value(&mut iter, "--serial")?),
                    "--json" => json = true,
                    "-h" | "--help" => return Ok(CliCommand::Help),
                    _ => return Err(format!("Unknown device-status argument: {arg}")),
                }
            }
            Ok(CliCommand::DeviceStatus {
                serial: serial
                    .filter(|value| !value.trim().is_empty())
                    .ok_or_else(|| "device-status requires --serial".to_string())?,
                json,
            })
        }
        "bridge-forward" => {
            let mut serial: Option<String> = None;
            let mut host_port = 8787;
            let mut device_port = 8787;
            let mut json = false;
            while let Some(arg) = iter.next() {
                match arg.as_str() {
                    "--serial" => serial = Some(next_value(&mut iter, "--serial")?),
                    "--host-port" => {
                        host_port =
                            parse_u16(&next_value(&mut iter, "--host-port")?, "--host-port")?
                    }
                    "--device-port" => {
                        device_port =
                            parse_u16(&next_value(&mut iter, "--device-port")?, "--device-port")?
                    }
                    "--json" => json = true,
                    "-h" | "--help" => return Ok(CliCommand::Help),
                    _ => return Err(format!("Unknown bridge-forward argument: {arg}")),
                }
            }
            Ok(CliCommand::BridgeForward {
                serial: serial
                    .filter(|value| !value.trim().is_empty())
                    .ok_or_else(|| "bridge-forward requires --serial".to_string())?,
                host_port,
                device_port,
                json,
            })
        }
        "install-panel" => {
            let mut serial: Option<String> = None;
            let mut apk = PathBuf::from(device::DEFAULT_PANEL_APK_PATH);
            let mut json = false;
            while let Some(arg) = iter.next() {
                match arg.as_str() {
                    "--serial" => serial = Some(next_value(&mut iter, "--serial")?),
                    "--apk" => apk = PathBuf::from(next_value(&mut iter, "--apk")?),
                    "--json" => json = true,
                    "-h" | "--help" => return Ok(CliCommand::Help),
                    _ => return Err(format!("Unknown install-panel argument: {arg}")),
                }
            }
            Ok(CliCommand::InstallPanel {
                serial: serial
                    .filter(|value| !value.trim().is_empty())
                    .ok_or_else(|| "install-panel requires --serial".to_string())?,
                apk,
                json,
            })
        }
        "install-target-apk" => {
            let mut serial: Option<String> = None;
            let mut apk: Option<PathBuf> = None;
            let mut json = false;
            while let Some(arg) = iter.next() {
                match arg.as_str() {
                    "--serial" => serial = Some(next_value(&mut iter, "--serial")?),
                    "--apk" => apk = Some(PathBuf::from(next_value(&mut iter, "--apk")?)),
                    "--json" => json = true,
                    "-h" | "--help" => return Ok(CliCommand::Help),
                    _ => return Err(format!("Unknown install-target-apk argument: {arg}")),
                }
            }
            Ok(CliCommand::InstallTargetApk {
                serial: serial
                    .filter(|value| !value.trim().is_empty())
                    .ok_or_else(|| "install-target-apk requires --serial".to_string())?,
                apk: apk.ok_or_else(|| "install-target-apk requires --apk".to_string())?,
                json,
            })
        }
        "launch-target-runtime" => {
            let mut serial: Option<String> = None;
            let mut package_name: Option<String> = None;
            let mut activity: Option<String> = None;
            let mut json = false;
            while let Some(arg) = iter.next() {
                match arg.as_str() {
                    "--serial" => serial = Some(next_value(&mut iter, "--serial")?),
                    "--package" | "--package-name" => {
                        package_name = Some(next_value(&mut iter, "--package")?)
                    }
                    "--activity" => activity = Some(next_value(&mut iter, "--activity")?),
                    "--json" => json = true,
                    "-h" | "--help" => return Ok(CliCommand::Help),
                    _ => return Err(format!("Unknown launch-target-runtime argument: {arg}")),
                }
            }
            Ok(CliCommand::LaunchTargetRuntime {
                serial: serial
                    .filter(|value| !value.trim().is_empty())
                    .ok_or_else(|| "launch-target-runtime requires --serial".to_string())?,
                package_name: package_name
                    .filter(|value| !value.trim().is_empty())
                    .ok_or_else(|| "launch-target-runtime requires --package".to_string())?,
                activity,
                json,
            })
        }
        "pull-target-session" => {
            let mut serial: Option<String> = None;
            let mut package_name: Option<String> = None;
            let mut remote_relative = device::DEFAULT_TARGET_SESSION_REMOTE_RELATIVE.to_string();
            let mut out: Option<PathBuf> = None;
            let mut json = false;
            while let Some(arg) = iter.next() {
                match arg.as_str() {
                    "--serial" => serial = Some(next_value(&mut iter, "--serial")?),
                    "--package" | "--package-name" => {
                        package_name = Some(next_value(&mut iter, "--package")?)
                    }
                    "--remote-relative" => {
                        remote_relative = next_value(&mut iter, "--remote-relative")?
                    }
                    "--out" => out = Some(PathBuf::from(next_value(&mut iter, "--out")?)),
                    "--json" => json = true,
                    "-h" | "--help" => return Ok(CliCommand::Help),
                    _ => return Err(format!("Unknown pull-target-session argument: {arg}")),
                }
            }
            Ok(CliCommand::PullTargetSession {
                serial: serial
                    .filter(|value| !value.trim().is_empty())
                    .ok_or_else(|| "pull-target-session requires --serial".to_string())?,
                package_name: package_name
                    .filter(|value| !value.trim().is_empty())
                    .ok_or_else(|| "pull-target-session requires --package".to_string())?,
                remote_relative,
                out: out.ok_or_else(|| "pull-target-session requires --out".to_string())?,
                json,
            })
        }
        "open-block" => {
            let mut endpoint = DEFAULT_ENDPOINT.to_string();
            let mut block: Option<u8> = None;
            let mut session_id: Option<String> = None;
            let mut participant_ref: Option<String> = None;
            let mut language_code = "en".to_string();
            let mut command_id: Option<String> = None;
            let mut debug_auto_submit = false;
            let mut debug_command_script: Option<String> = None;
            let mut debug_command_interval_ms: Option<u32> = None;
            while let Some(arg) = iter.next() {
                match arg.as_str() {
                    "--endpoint" => endpoint = next_value(&mut iter, "--endpoint")?,
                    "--block" => {
                        let raw = next_value(&mut iter, "--block")?;
                        block = Some(
                            raw.parse::<u8>()
                                .map_err(|_| "--block must be 1, 2, or 3".to_string())?,
                        );
                    }
                    "--session-id" | "--session" => {
                        session_id = Some(next_value(&mut iter, "--session-id")?);
                    }
                    "--participant-ref" | "--participant" => {
                        participant_ref = Some(next_value(&mut iter, "--participant-ref")?);
                    }
                    "--language-code" | "--language" => {
                        language_code = next_value(&mut iter, "--language-code")?;
                    }
                    "--command-id" => command_id = Some(next_value(&mut iter, "--command-id")?),
                    "--debug-auto-submit" => debug_auto_submit = true,
                    "--debug-command-script" => {
                        debug_command_script =
                            Some(next_value(&mut iter, "--debug-command-script")?);
                    }
                    "--debug-command-interval-ms" => {
                        let raw = next_value(&mut iter, "--debug-command-interval-ms")?;
                        debug_command_interval_ms = Some(raw.parse::<u32>().map_err(|_| {
                            "--debug-command-interval-ms must be an integer".to_string()
                        })?);
                    }
                    "-h" | "--help" => return Ok(CliCommand::Help),
                    _ => return Err(format!("Unknown open-block argument: {arg}")),
                }
            }
            Ok(CliCommand::OpenBlock {
                endpoint,
                block: block.ok_or_else(|| "open-block requires --block".to_string())?,
                session_id: session_id
                    .filter(|value| !value.trim().is_empty())
                    .ok_or_else(|| "open-block requires --session-id".to_string())?,
                participant_ref: participant_ref
                    .filter(|value| !value.trim().is_empty())
                    .ok_or_else(|| "open-block requires --participant-ref".to_string())?,
                language_code,
                command_id,
                debug_auto_submit,
                debug_command_script,
                debug_command_interval_ms,
            })
        }
        "dismiss" => {
            let mut endpoint = DEFAULT_ENDPOINT.to_string();
            let mut session_id: Option<String> = None;
            let mut command_id: Option<String> = None;
            while let Some(arg) = iter.next() {
                match arg.as_str() {
                    "--endpoint" => endpoint = next_value(&mut iter, "--endpoint")?,
                    "--session-id" | "--session" => {
                        session_id = Some(next_value(&mut iter, "--session-id")?);
                    }
                    "--command-id" => command_id = Some(next_value(&mut iter, "--command-id")?),
                    "-h" | "--help" => return Ok(CliCommand::Help),
                    _ => return Err(format!("Unknown dismiss argument: {arg}")),
                }
            }
            Ok(CliCommand::Dismiss {
                endpoint,
                session_id: session_id
                    .filter(|value| !value.trim().is_empty())
                    .ok_or_else(|| "dismiss requires --session-id".to_string())?,
                command_id,
            })
        }
        "start-session" => {
            let mut common = RuntimeCommonArgs::default();
            let mut session = RuntimeSessionArgs {
                language_code: "en".to_string(),
                ..RuntimeSessionArgs::default()
            };
            let mut provenance = RuntimeProvenanceArgs::default();
            while let Some(arg) = iter.next() {
                if parse_runtime_common_arg(&arg, &mut iter, &mut common)? {
                    continue;
                }
                match arg.as_str() {
                    "--study-id" => session.study_id = next_value(&mut iter, "--study-id")?,
                    "--session-id" | "--session" => {
                        session.session_id = next_value(&mut iter, "--session-id")?
                    }
                    "--participant-ref" | "--participant" => {
                        session.participant_ref = next_value(&mut iter, "--participant-ref")?
                    }
                    "--dataset-id" => session.dataset_id = next_value(&mut iter, "--dataset-id")?,
                    "--condition-id" => {
                        session.condition_id = next_value(&mut iter, "--condition-id")?
                    }
                    "--language-code" | "--language" => {
                        session.language_code = next_value(&mut iter, "--language-code")?
                    }
                    "--unity-project" => {
                        provenance.unity_project = next_value(&mut iter, "--unity-project")?
                    }
                    "--unity-editor" => {
                        provenance.unity_editor = next_value(&mut iter, "--unity-editor")?
                    }
                    "--apk-sha256" => {
                        provenance.apk_sha256 = next_value(&mut iter, "--apk-sha256")?
                    }
                    "--app-version-name" => {
                        provenance.app_version_name = next_value(&mut iter, "--app-version-name")?
                    }
                    "--app-version-code" => {
                        let raw = next_value(&mut iter, "--app-version-code")?;
                        provenance.app_version_code = raw
                            .parse::<i32>()
                            .map_err(|_| "--app-version-code must be an integer".to_string())?;
                    }
                    "--source-commit" => {
                        provenance.source_commit = next_value(&mut iter, "--source-commit")?
                    }
                    "-h" | "--help" => return Ok(CliCommand::Help),
                    _ => return Err(format!("Unknown start-session argument: {arg}")),
                }
            }
            if session.session_id.trim().is_empty() {
                return Err("start-session requires --session-id".to_string());
            }
            if session.participant_ref.trim().is_empty() {
                return Err("start-session requires --participant-ref".to_string());
            }
            Ok(CliCommand::StartSession {
                common,
                session,
                provenance,
            })
        }
        "stop-session" => {
            let mut common = RuntimeCommonArgs::default();
            let mut session_id: Option<String> = None;
            while let Some(arg) = iter.next() {
                if parse_runtime_common_arg(&arg, &mut iter, &mut common)? {
                    continue;
                }
                match arg.as_str() {
                    "--session-id" | "--session" => {
                        session_id = Some(next_value(&mut iter, "--session-id")?)
                    }
                    "-h" | "--help" => return Ok(CliCommand::Help),
                    _ => return Err(format!("Unknown stop-session argument: {arg}")),
                }
            }
            Ok(CliCommand::StopSession {
                common,
                session_id: session_id
                    .filter(|value| !value.trim().is_empty())
                    .ok_or_else(|| "stop-session requires --session-id".to_string())?,
            })
        }
        "pull-session" => {
            let mut common = RuntimeCommonArgs::default();
            let mut session_id: Option<String> = None;
            let mut export = RuntimeExportArgs::default();
            while let Some(arg) = iter.next() {
                if parse_runtime_common_arg(&arg, &mut iter, &mut common)? {
                    continue;
                }
                match arg.as_str() {
                    "--session-id" | "--session" => {
                        session_id = Some(next_value(&mut iter, "--session-id")?)
                    }
                    "--quest-package" => {
                        export.quest_package = next_value(&mut iter, "--quest-package")?
                    }
                    "--remote-relative" | "--quest-remote-relative" => {
                        export.quest_remote_relative = next_value(&mut iter, "--remote-relative")?
                    }
                    "--windows-device-pull-subfolder" | "--out-subfolder" => {
                        export.windows_device_pull_subfolder =
                            next_value(&mut iter, "--windows-device-pull-subfolder")?
                    }
                    "--expected-file" => {
                        let value = next_value(&mut iter, "--expected-file")?;
                        if !value.trim().is_empty() {
                            export.expected_files.push(value);
                        }
                    }
                    "--clear-expected-files" => export.expected_files.clear(),
                    "-h" | "--help" => return Ok(CliCommand::Help),
                    _ => return Err(format!("Unknown pull-session argument: {arg}")),
                }
            }
            Ok(CliCommand::PullSession {
                common,
                session_id: session_id
                    .filter(|value| !value.trim().is_empty())
                    .ok_or_else(|| "pull-session requires --session-id".to_string())?,
                export,
            })
        }
        "mark-timing-event" => {
            let mut common = RuntimeCommonArgs::default();
            let mut session_id: Option<String> = None;
            let mut marker_name: Option<String> = None;
            let mut marker_detail = String::new();
            while let Some(arg) = iter.next() {
                if parse_runtime_common_arg(&arg, &mut iter, &mut common)? {
                    continue;
                }
                match arg.as_str() {
                    "--session-id" | "--session" => {
                        session_id = Some(next_value(&mut iter, "--session-id")?)
                    }
                    "--marker-name" | "--marker" => {
                        marker_name = Some(next_value(&mut iter, "--marker-name")?)
                    }
                    "--marker-detail" | "--detail" => {
                        marker_detail = next_value(&mut iter, "--marker-detail")?
                    }
                    "-h" | "--help" => return Ok(CliCommand::Help),
                    _ => return Err(format!("Unknown mark-timing-event argument: {arg}")),
                }
            }
            Ok(CliCommand::MarkTimingEvent {
                common,
                session_id: session_id
                    .filter(|value| !value.trim().is_empty())
                    .ok_or_else(|| "mark-timing-event requires --session-id".to_string())?,
                marker_name: marker_name
                    .filter(|value| !value.trim().is_empty())
                    .ok_or_else(|| "mark-timing-event requires --marker-name".to_string())?,
                marker_detail,
            })
        }
        "open-questionnaire" => {
            let mut common = RuntimeCommonArgs::default();
            let mut panel_request = RuntimePanelLaunchSpec::default();
            let mut state = RuntimeQuestionnaireStateSpec {
                language_code: "en".to_string(),
                ..RuntimeQuestionnaireStateSpec::default()
            };
            while let Some(arg) = iter.next() {
                if parse_runtime_common_arg(&arg, &mut iter, &mut common)? {
                    continue;
                }
                match arg.as_str() {
                    "--panel-protocol-version" => {
                        panel_request.protocol_version =
                            next_value(&mut iter, "--panel-protocol-version")?
                    }
                    "--study-id" => panel_request.study_id = next_value(&mut iter, "--study-id")?,
                    "--session-id" | "--session" => {
                        panel_request.session_id = next_value(&mut iter, "--session-id")?
                    }
                    "--participant-ref" | "--participant" => {
                        panel_request.participant_ref = next_value(&mut iter, "--participant-ref")?
                    }
                    "--schema-id" => {
                        panel_request.schema_id = next_value(&mut iter, "--schema-id")?
                    }
                    "--questionnaire-id" => {
                        panel_request.questionnaire_id =
                            next_value(&mut iter, "--questionnaire-id")?
                    }
                    "--open-stage" => {
                        panel_request.open_stage = next_value(&mut iter, "--open-stage")?
                    }
                    "--screen" => {
                        let stage = next_value(&mut iter, "--screen")?;
                        if !stage.trim().is_empty() {
                            panel_request.screen_sequence.push(stage);
                        }
                    }
                    "--screen-sequence" => {
                        let raw = next_value(&mut iter, "--screen-sequence")?;
                        extend_screen_sequence(&raw, &mut panel_request.screen_sequence);
                    }
                    "--condition-number" => {
                        panel_request.condition_number = parse_i32(
                            &next_value(&mut iter, "--condition-number")?,
                            "--condition-number",
                        )?
                    }
                    "--caller-package-name" | "--caller-package" => {
                        panel_request.caller_package_name =
                            next_value(&mut iter, "--caller-package-name")?
                    }
                    "--caller-app-version" => {
                        panel_request.caller_app_version =
                            next_value(&mut iter, "--caller-app-version")?
                    }
                    "--language-code" | "--language" => {
                        state.language_code = next_value(&mut iter, "--language-code")?
                    }
                    "--condition-id" => {
                        state.condition_id = next_value(&mut iter, "--condition-id")?
                    }
                    "--condition-label" => {
                        state.condition_label = next_value(&mut iter, "--condition-label")?
                    }
                    "--operator-stage" => {
                        state.operator_stage = next_value(&mut iter, "--operator-stage")?
                    }
                    "--condition-index" => {
                        state.condition_index = parse_i32(
                            &next_value(&mut iter, "--condition-index")?,
                            "--condition-index",
                        )?
                    }
                    "-h" | "--help" => return Ok(CliCommand::Help),
                    _ => return Err(format!("Unknown open-questionnaire argument: {arg}")),
                }
            }
            if panel_request.protocol_version.trim().is_empty() {
                panel_request.protocol_version = PANEL_PROTOCOL_VERSION.to_string();
            }
            panel_request.questionnaire_state = Some(state);
            Ok(CliCommand::OpenQuestionnaire {
                common,
                panel_request,
            })
        }
        "post-command" => {
            let mut endpoint = DEFAULT_ENDPOINT.to_string();
            let mut file: Option<PathBuf> = None;
            let mut audit_dir: Option<PathBuf> = None;
            while let Some(arg) = iter.next() {
                match arg.as_str() {
                    "--endpoint" => endpoint = next_value(&mut iter, "--endpoint")?,
                    "--file" => file = Some(PathBuf::from(next_value(&mut iter, "--file")?)),
                    "--audit-dir" => {
                        audit_dir = Some(PathBuf::from(next_value(&mut iter, "--audit-dir")?))
                    }
                    "-h" | "--help" => return Ok(CliCommand::Help),
                    _ => return Err(format!("Unknown post-command argument: {arg}")),
                }
            }
            Ok(CliCommand::PostCommand {
                endpoint,
                file: file.ok_or_else(|| "post-command requires --file".to_string())?,
                audit_dir,
            })
        }
        "proof-run" => {
            let mut endpoint = DEFAULT_ENDPOINT.to_string();
            let mut block: Option<u8> = None;
            let mut session_id: Option<String> = None;
            let mut participant_ref: Option<String> = None;
            let mut language_code = "en".to_string();
            let mut out = PathBuf::from("artifacts").join("operator-proof-run");
            let mut timeout_ms = 12_000;
            let mut poll_interval_ms = 500;
            let mut debug_auto_submit = false;
            let mut debug_command_script: Option<String> = None;
            let mut debug_command_interval_ms: Option<u32> = None;
            while let Some(arg) = iter.next() {
                match arg.as_str() {
                    "--endpoint" => endpoint = next_value(&mut iter, "--endpoint")?,
                    "--block" => {
                        let raw = next_value(&mut iter, "--block")?;
                        block = Some(
                            raw.parse::<u8>()
                                .map_err(|_| "--block must be 1, 2, or 3".to_string())?,
                        );
                    }
                    "--session-id" | "--session" => {
                        session_id = Some(next_value(&mut iter, "--session-id")?);
                    }
                    "--participant-ref" | "--participant" => {
                        participant_ref = Some(next_value(&mut iter, "--participant-ref")?);
                    }
                    "--language-code" | "--language" => {
                        language_code = next_value(&mut iter, "--language-code")?;
                    }
                    "--out" => out = PathBuf::from(next_value(&mut iter, "--out")?),
                    "--timeout-ms" => {
                        timeout_ms =
                            parse_u64(&next_value(&mut iter, "--timeout-ms")?, "--timeout-ms")?
                    }
                    "--poll-interval-ms" => {
                        poll_interval_ms = parse_u64(
                            &next_value(&mut iter, "--poll-interval-ms")?,
                            "--poll-interval-ms",
                        )?
                    }
                    "--debug-auto-submit" => debug_auto_submit = true,
                    "--debug-command-script" => {
                        debug_command_script =
                            Some(next_value(&mut iter, "--debug-command-script")?);
                    }
                    "--debug-command-interval-ms" => {
                        let raw = next_value(&mut iter, "--debug-command-interval-ms")?;
                        debug_command_interval_ms = Some(raw.parse::<u32>().map_err(|_| {
                            "--debug-command-interval-ms must be an integer".to_string()
                        })?);
                    }
                    "-h" | "--help" => return Ok(CliCommand::Help),
                    _ => return Err(format!("Unknown proof-run argument: {arg}")),
                }
            }
            Ok(CliCommand::ProofRun {
                endpoint,
                block: block.ok_or_else(|| "proof-run requires --block".to_string())?,
                session_id: session_id
                    .filter(|value| !value.trim().is_empty())
                    .ok_or_else(|| "proof-run requires --session-id".to_string())?,
                participant_ref: participant_ref
                    .filter(|value| !value.trim().is_empty())
                    .ok_or_else(|| "proof-run requires --participant-ref".to_string())?,
                language_code,
                out,
                timeout_ms,
                poll_interval_ms,
                debug_auto_submit,
                debug_command_script,
                debug_command_interval_ms,
            })
        }
        _ => Err(format!("Unknown command: {command}\n\n{}", help_text())),
    }
}

fn parse_runtime_common_arg(
    arg: &str,
    iter: &mut impl Iterator<Item = String>,
    common: &mut RuntimeCommonArgs,
) -> Result<bool, String> {
    match arg {
        "--endpoint" => {
            common.endpoint = next_value(iter, "--endpoint")?;
            Ok(true)
        }
        "--protocol-version" => {
            common.protocol_version = next_value(iter, "--protocol-version")?;
            Ok(true)
        }
        "--target-runtime-kind" | "--runtime-kind" => {
            common.target_runtime_kind = next_value(iter, "--target-runtime-kind")?;
            Ok(true)
        }
        "--target-runtime-package" | "--runtime-package" => {
            common.target_runtime_package = next_value(iter, "--target-runtime-package")?;
            Ok(true)
        }
        "--bridge-endpoint" => {
            common.bridge_endpoint = next_value(iter, "--bridge-endpoint")?;
            Ok(true)
        }
        "--quest-selector" => {
            common.quest_selector = next_value(iter, "--quest-selector")?;
            Ok(true)
        }
        "--command-id" => {
            common.command_id = Some(next_value(iter, "--command-id")?);
            Ok(true)
        }
        "--command-name" => {
            common.command_name = Some(next_value(iter, "--command-name")?);
            Ok(true)
        }
        "--audit-dir" => {
            common.audit_dir = Some(PathBuf::from(next_value(iter, "--audit-dir")?));
            Ok(true)
        }
        _ => Ok(false),
    }
}

fn next_value(iter: &mut impl Iterator<Item = String>, flag: &str) -> Result<String, String> {
    iter.next()
        .filter(|value| !value.trim().is_empty())
        .ok_or_else(|| format!("{flag} requires a value"))
}

fn parse_u16(value: &str, flag: &str) -> Result<u16, String> {
    value
        .parse::<u16>()
        .ok()
        .filter(|port| *port > 0)
        .ok_or_else(|| format!("{flag} must be a port between 1 and 65535"))
}

fn parse_u64(value: &str, flag: &str) -> Result<u64, String> {
    value
        .parse::<u64>()
        .ok()
        .filter(|number| *number > 0)
        .ok_or_else(|| format!("{flag} must be a positive integer"))
}

fn parse_i32(value: &str, flag: &str) -> Result<i32, String> {
    value
        .parse::<i32>()
        .map_err(|_| format!("{flag} must be an integer"))
}

fn extend_screen_sequence(raw: &str, screen_sequence: &mut Vec<String>) {
    for stage in raw.split(',') {
        let stage = stage.trim();
        if !stage.is_empty() {
            screen_sequence.push(stage.to_string());
        }
    }
}

fn extend_comma_list(raw: &str, values: &mut Vec<String>) {
    for value in raw.split(',') {
        push_non_empty(values, value.to_string());
    }
}

fn push_non_empty(values: &mut Vec<String>, value: String) {
    let value = value.trim();
    if !value.is_empty() {
        values.push(value.to_string());
    }
}

fn http_request(method: &str, url: &str, body: Option<&str>) -> Result<String, String> {
    let parsed = HttpUrl::parse(url)?;
    let mut stream = TcpStream::connect((parsed.host.as_str(), parsed.port)).map_err(|err| {
        format!(
            "Could not connect to {}:{}: {err}",
            parsed.host, parsed.port
        )
    })?;
    let body = body.unwrap_or("");
    let request = if method == "POST" {
        format!(
            "POST {path} HTTP/1.1\r\nHost: {host}\r\nAccept: application/json\r\nContent-Type: application/json\r\nContent-Length: {length}\r\nConnection: close\r\n\r\n{body}",
            path = parsed.path,
            host = parsed.host_header(),
            length = body.len(),
        )
    } else {
        format!(
            "GET {path} HTTP/1.1\r\nHost: {host}\r\nAccept: application/json\r\nConnection: close\r\n\r\n",
            path = parsed.path,
            host = parsed.host_header(),
        )
    };
    stream
        .write_all(request.as_bytes())
        .map_err(|err| format!("Could not write HTTP request: {err}"))?;
    let mut response = String::new();
    stream
        .read_to_string(&mut response)
        .map_err(|err| format!("Could not read HTTP response: {err}"))?;
    parse_http_response(&response)
}

fn parse_http_response(response: &str) -> Result<String, String> {
    let (head, body) = response
        .split_once("\r\n\r\n")
        .ok_or_else(|| "Bridge returned a malformed HTTP response".to_string())?;
    let status_line = head
        .lines()
        .next()
        .ok_or_else(|| "Bridge returned an empty HTTP response".to_string())?;
    let status = status_line
        .split_whitespace()
        .nth(1)
        .ok_or_else(|| "Bridge returned a malformed HTTP status".to_string())?
        .parse::<u16>()
        .map_err(|_| "Bridge returned a malformed HTTP status code".to_string())?;
    let body = pretty_json_or_raw(body);
    if (200..300).contains(&status) {
        Ok(body)
    } else {
        Err(format!("Bridge returned HTTP {status}:\n{body}"))
    }
}

fn pretty_json_or_raw(body: &str) -> String {
    match serde_json::from_str::<serde_json::Value>(body) {
        Ok(value) => serde_json::to_string_pretty(&value).unwrap_or_else(|_| body.to_string()),
        Err(_) => body.to_string(),
    }
}

#[derive(Debug, PartialEq, Eq)]
struct HttpUrl {
    host: String,
    port: u16,
    path: String,
}

impl HttpUrl {
    fn parse(url: &str) -> Result<Self, String> {
        let rest = url
            .strip_prefix("http://")
            .ok_or_else(|| "Operator CLI HTTP client supports http:// endpoints".to_string())?;
        let (authority, path) = match rest.split_once('/') {
            Some((authority, path)) => (authority, format!("/{path}")),
            None => (rest, "/".to_string()),
        };
        if authority.trim().is_empty() {
            return Err("HTTP endpoint host is empty".to_string());
        }
        let (host, port) = match authority.rsplit_once(':') {
            Some((host, port)) if !host.contains(']') => {
                let parsed_port = port
                    .parse::<u16>()
                    .map_err(|_| format!("HTTP endpoint port is invalid: {port}"))?;
                (host.to_string(), parsed_port)
            }
            _ => (authority.to_string(), 80),
        };
        if host.trim().is_empty() {
            return Err("HTTP endpoint host is empty".to_string());
        }
        Ok(Self { host, port, path })
    }

    fn host_header(&self) -> String {
        if self.port == 80 {
            self.host.clone()
        } else {
            format!("{}:{}", self.host, self.port)
        }
    }
}

fn generated_command_id(prefix: &str) -> String {
    let millis = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|duration| duration.as_millis())
        .unwrap_or_default();
    format!("{prefix}-{millis}")
}

fn help_text() -> String {
    vec![
        "Quest questionnaire operator CLI".to_string(),
        String::new(),
        "Commands:".to_string(),
        "  status [--endpoint http://127.0.0.1:8787]".to_string(),
        "  preflight-runtime [--endpoint URL] [--protocol-version VERSION] [--runtime-kind KIND] [--runtime-package PACKAGE] [--require-actions A,B] [--require-action ACTION] [--require-app-private-session-bundle] [--require-explicit-pull] [--require-questionnaire-panel] [--require-questionnaire-result-callback] [--require-lsl-clock-alignment] [--json]".to_string(),
        "  tooling-status [--json]".to_string(),
        "  devices [--json]".to_string(),
        "  device-status --serial SERIAL [--json]".to_string(),
        "  bridge-forward --serial SERIAL [--host-port 8787] [--device-port 8787] [--json]"
            .to_string(),
        format!(
            "  install-panel --serial SERIAL [--apk {}] [--json]",
            device::DEFAULT_PANEL_APK_PATH
        ),
        "  install-target-apk --serial SERIAL --apk target.apk [--json]".to_string(),
        "  launch-target-runtime --serial SERIAL --package PACKAGE [--activity ACTIVITY] [--json]"
            .to_string(),
        "  pull-target-session --serial SERIAL --package PACKAGE --out FOLDER [--remote-relative files/runtime_csv] [--json]".to_string(),
        "  open-block --block 1|2|3 --session-id ID --participant-ref REF [--language-code en] [--endpoint URL] [--command-id ID] [--debug-auto-submit] [--debug-command-script SCRIPT] [--debug-command-interval-ms MS]".to_string(),
        "  dismiss --session-id ID [--endpoint URL] [--command-id ID]".to_string(),
        "  start-session --session-id ID --participant-ref REF [--endpoint URL] [--protocol-version VERSION] [--runtime-kind KIND] [--runtime-package PACKAGE] [--study-id ID] [--condition-id ID] [--language-code en] [--apk-sha256 SHA256] [--source-commit SHA] [--command-id ID] [--command-name NAME] [--audit-dir DIR]".to_string(),
        "  stop-session --session-id ID [--endpoint URL] [--protocol-version VERSION] [--runtime-kind KIND] [--runtime-package PACKAGE] [--command-id ID] [--command-name NAME] [--audit-dir DIR]".to_string(),
        "  pull-session --session-id ID [--remote-relative files/runtime_csv] [--quest-package PACKAGE] [--runtime-package PACKAGE] [--expected-file NAME] [--endpoint URL] [--protocol-version VERSION] [--runtime-kind KIND] [--command-id ID] [--command-name NAME] [--audit-dir DIR]".to_string(),
        "  mark-timing-event --session-id ID --marker-name NAME [--marker-detail TEXT] [--endpoint URL] [--protocol-version VERSION] [--runtime-kind KIND] [--runtime-package PACKAGE] [--command-id ID] [--command-name NAME] [--audit-dir DIR]".to_string(),
        "  open-questionnaire --session-id ID --participant-ref REF --study-id ID --questionnaire-id ID --open-stage STAGE [--screen-sequence A,B] [--condition-number N] [--language-code en] [--condition-id ID] [--endpoint URL] [--protocol-version VERSION] [--runtime-kind KIND] [--runtime-package PACKAGE] [--command-id ID] [--command-name NAME] [--audit-dir DIR]".to_string(),
        "  post-command --file command.json [--endpoint URL] [--audit-dir DIR]".to_string(),
        "  proof-run --block 1|2|3 --session-id ID --participant-ref REF [--language-code en] [--endpoint URL] [--out FOLDER] [--timeout-ms MS] [--poll-interval-ms MS]".to_string(),
    ]
    .join("\n")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_open_block_command() {
        let command = parse_args(vec![
            "open-block".to_string(),
            "--block".to_string(),
            "2".to_string(),
            "--session".to_string(),
            "session-1".to_string(),
            "--participant".to_string(),
            "P001".to_string(),
            "--language".to_string(),
            "de".to_string(),
        ])
        .unwrap();

        assert_eq!(
            command,
            CliCommand::OpenBlock {
                endpoint: DEFAULT_ENDPOINT.to_string(),
                block: 2,
                session_id: "session-1".to_string(),
                participant_ref: "P001".to_string(),
                language_code: "de".to_string(),
                command_id: None,
                debug_auto_submit: false,
                debug_command_script: None,
                debug_command_interval_ms: None,
            }
        );
    }

    #[test]
    fn parses_install_panel_command() {
        let command = parse_args(vec![
            "install-panel".to_string(),
            "--serial".to_string(),
            "QUEST_SERIAL".to_string(),
            "--apk".to_string(),
            "custom-panel.apk".to_string(),
            "--json".to_string(),
        ])
        .unwrap();

        assert_eq!(
            command,
            CliCommand::InstallPanel {
                serial: "QUEST_SERIAL".to_string(),
                apk: PathBuf::from("custom-panel.apk"),
                json: true,
            }
        );
    }

    #[test]
    fn parses_install_target_apk_command() {
        let command = parse_args(vec![
            "install-target-apk".to_string(),
            "--serial".to_string(),
            "QUEST_SERIAL".to_string(),
            "--apk".to_string(),
            "target-runtime.apk".to_string(),
            "--json".to_string(),
        ])
        .unwrap();

        assert_eq!(
            command,
            CliCommand::InstallTargetApk {
                serial: "QUEST_SERIAL".to_string(),
                apk: PathBuf::from("target-runtime.apk"),
                json: true,
            }
        );
    }

    #[test]
    fn parses_launch_target_runtime_command() {
        let command = parse_args(vec![
            "launch-target-runtime".to_string(),
            "--serial".to_string(),
            "QUEST_SERIAL".to_string(),
            "--package".to_string(),
            "io.github.example".to_string(),
            "--activity".to_string(),
            ".MainActivity".to_string(),
        ])
        .unwrap();

        assert_eq!(
            command,
            CliCommand::LaunchTargetRuntime {
                serial: "QUEST_SERIAL".to_string(),
                package_name: "io.github.example".to_string(),
                activity: Some(".MainActivity".to_string()),
                json: false,
            }
        );
    }

    #[test]
    fn parses_pull_target_session_command() {
        let command = parse_args(vec![
            "pull-target-session".to_string(),
            "--serial".to_string(),
            "QUEST_SERIAL".to_string(),
            "--package".to_string(),
            "io.github.example".to_string(),
            "--remote-relative".to_string(),
            "files/runtime_csv/participant-P001/session-001".to_string(),
            "--out".to_string(),
            "study-data/device-session-pull".to_string(),
            "--json".to_string(),
        ])
        .unwrap();

        assert_eq!(
            command,
            CliCommand::PullTargetSession {
                serial: "QUEST_SERIAL".to_string(),
                package_name: "io.github.example".to_string(),
                remote_relative: "files/runtime_csv/participant-P001/session-001".to_string(),
                out: PathBuf::from("study-data/device-session-pull"),
                json: true,
            }
        );
    }

    #[test]
    fn parses_preflight_runtime_command() {
        let command = parse_args(vec![
            "preflight-runtime".to_string(),
            "--endpoint".to_string(),
            "http://127.0.0.1:8787".to_string(),
            "--protocol-version".to_string(),
            "viscereality.peripersonal.operator.v1".to_string(),
            "--runtime-kind".to_string(),
            "unity_quest_apk".to_string(),
            "--runtime-package".to_string(),
            "com.example.peripersonal".to_string(),
            "--require-actions".to_string(),
            "start_session,open_questionnaire".to_string(),
            "--require-action".to_string(),
            "pull_session".to_string(),
            "--require-app-private-session-bundle".to_string(),
            "--require-explicit-pull".to_string(),
            "--require-questionnaire-panel".to_string(),
            "--require-questionnaire-result-callback".to_string(),
            "--require-lsl-clock-alignment".to_string(),
            "--json".to_string(),
        ])
        .unwrap();

        match command {
            CliCommand::PreflightRuntime {
                common,
                required_actions,
                require_app_private_session_bundle,
                require_explicit_pull,
                require_questionnaire_panel_launch,
                require_questionnaire_result_callback_ingest,
                require_lsl_clock_alignment,
                json,
            } => {
                assert_eq!(
                    common.protocol_version,
                    "viscereality.peripersonal.operator.v1"
                );
                assert_eq!(common.target_runtime_kind, "unity_quest_apk");
                assert_eq!(common.target_runtime_package, "com.example.peripersonal");
                assert_eq!(
                    required_actions,
                    vec![
                        "start_session".to_string(),
                        "open_questionnaire".to_string(),
                        "pull_session".to_string()
                    ]
                );
                assert!(require_app_private_session_bundle);
                assert!(require_explicit_pull);
                assert!(require_questionnaire_panel_launch);
                assert!(require_questionnaire_result_callback_ingest);
                assert!(require_lsl_clock_alignment);
                assert!(json);
            }
            other => panic!("unexpected command: {other:?}"),
        }
    }

    #[test]
    fn parses_http_url_with_path() {
        assert_eq!(
            HttpUrl::parse("http://127.0.0.1:8787/v1/status").unwrap(),
            HttpUrl {
                host: "127.0.0.1".to_string(),
                port: 8787,
                path: "/v1/status".to_string(),
            }
        );
    }

    #[test]
    fn rejects_https_for_stdlib_client() {
        assert!(HttpUrl::parse("https://127.0.0.1:8787/v1/status").is_err());
    }

    #[test]
    fn parses_start_session_command() {
        let command = parse_args(vec![
            "start-session".to_string(),
            "--session-id".to_string(),
            "session-1".to_string(),
            "--participant-ref".to_string(),
            "P001".to_string(),
            "--protocol-version".to_string(),
            "private.runtime.operator.v1".to_string(),
            "--runtime-kind".to_string(),
            "target_quest_apk".to_string(),
            "--condition-id".to_string(),
            "condition-a".to_string(),
            "--source-commit".to_string(),
            "abc123".to_string(),
            "--command-name".to_string(),
            "private.session.start".to_string(),
            "--audit-dir".to_string(),
            "audit-output".to_string(),
        ])
        .unwrap();

        match command {
            CliCommand::StartSession {
                common,
                session,
                provenance,
            } => {
                assert_eq!(common.protocol_version, "private.runtime.operator.v1");
                assert_eq!(common.target_runtime_kind, "target_quest_apk");
                assert_eq!(
                    common.command_name.as_deref(),
                    Some("private.session.start")
                );
                assert_eq!(common.audit_dir.as_deref(), Some(Path::new("audit-output")));
                assert_eq!(session.session_id, "session-1");
                assert_eq!(session.participant_ref, "P001");
                assert_eq!(session.condition_id, "condition-a");
                assert_eq!(provenance.source_commit, "abc123");
            }
            other => panic!("unexpected command: {other:?}"),
        }
    }

    #[test]
    fn parses_stop_session_command() {
        let command = parse_args(vec![
            "stop-session".to_string(),
            "--session".to_string(),
            "session-1".to_string(),
            "--command-name".to_string(),
            "private.session.stop".to_string(),
        ])
        .unwrap();

        match command {
            CliCommand::StopSession { common, session_id } => {
                assert_eq!(session_id, "session-1");
                assert_eq!(common.command_name.as_deref(), Some("private.session.stop"));
            }
            other => panic!("unexpected command: {other:?}"),
        }
    }

    #[test]
    fn parses_pull_session_command() {
        let command = parse_args(vec![
            "pull-session".to_string(),
            "--session-id".to_string(),
            "session-1".to_string(),
            "--runtime-package".to_string(),
            "io.github.example.target".to_string(),
            "--remote-relative".to_string(),
            "files/runtime_csv/participant-P001/session-1".to_string(),
            "--out-subfolder".to_string(),
            "device-session-pull".to_string(),
            "--clear-expected-files".to_string(),
            "--expected-file".to_string(),
            "questionnaire_results.jsonl".to_string(),
            "--command-name".to_string(),
            "target.session.pull".to_string(),
        ])
        .unwrap();

        match command {
            CliCommand::PullSession {
                common,
                session_id,
                export,
            } => {
                assert_eq!(session_id, "session-1");
                assert_eq!(common.target_runtime_package, "io.github.example.target");
                assert_eq!(common.command_name.as_deref(), Some("target.session.pull"));
                assert_eq!(
                    export.quest_remote_relative,
                    "files/runtime_csv/participant-P001/session-1"
                );
                assert_eq!(
                    export.expected_files,
                    vec!["questionnaire_results.jsonl".to_string()]
                );
            }
            other => panic!("unexpected command: {other:?}"),
        }
    }

    #[test]
    fn parses_mark_timing_event_command() {
        let command = parse_args(vec![
            "mark-timing-event".to_string(),
            "--session-id".to_string(),
            "session-1".to_string(),
            "--marker-name".to_string(),
            "condition_start".to_string(),
            "--marker-detail".to_string(),
            "operator marker".to_string(),
        ])
        .unwrap();

        match command {
            CliCommand::MarkTimingEvent {
                session_id,
                marker_name,
                marker_detail,
                ..
            } => {
                assert_eq!(session_id, "session-1");
                assert_eq!(marker_name, "condition_start");
                assert_eq!(marker_detail, "operator marker");
            }
            other => panic!("unexpected command: {other:?}"),
        }
    }

    #[test]
    fn parses_open_questionnaire_command() {
        let command = parse_args(vec![
            "open-questionnaire".to_string(),
            "--session-id".to_string(),
            "session-1".to_string(),
            "--participant-ref".to_string(),
            "P001".to_string(),
            "--study-id".to_string(),
            "target-study".to_string(),
            "--questionnaire-id".to_string(),
            "questionnaire-v1".to_string(),
            "--open-stage".to_string(),
            "target:intro".to_string(),
            "--screen-sequence".to_string(),
            "target:intro,target:rating".to_string(),
            "--condition-number".to_string(),
            "2".to_string(),
            "--condition-id".to_string(),
            "condition-b".to_string(),
            "--runtime-package".to_string(),
            "io.github.example.target".to_string(),
            "--command-name".to_string(),
            "target.questionnaire.open".to_string(),
        ])
        .unwrap();

        match command {
            CliCommand::OpenQuestionnaire {
                common,
                panel_request,
            } => {
                assert_eq!(common.target_runtime_package, "io.github.example.target");
                assert_eq!(
                    common.command_name.as_deref(),
                    Some("target.questionnaire.open")
                );
                assert_eq!(panel_request.session_id, "session-1");
                assert_eq!(panel_request.study_id, "target-study");
                assert_eq!(panel_request.questionnaire_id, "questionnaire-v1");
                assert_eq!(
                    panel_request.screen_sequence,
                    vec!["target:intro".to_string(), "target:rating".to_string()]
                );
                assert_eq!(panel_request.condition_number, 2);
                assert_eq!(
                    panel_request
                        .questionnaire_state
                        .as_ref()
                        .map(|state| state.condition_id.as_str()),
                    Some("condition-b")
                );
            }
            other => panic!("unexpected command: {other:?}"),
        }
    }

    #[test]
    fn parses_post_command_file() {
        let command = parse_args(vec![
            "post-command".to_string(),
            "--file".to_string(),
            "command.json".to_string(),
        ])
        .unwrap();

        assert_eq!(
            command,
            CliCommand::PostCommand {
                endpoint: DEFAULT_ENDPOINT.to_string(),
                file: PathBuf::from("command.json"),
                audit_dir: None,
            }
        );
    }

    #[test]
    fn writes_command_audit_jsonl() {
        let audit_dir = std::env::temp_dir().join(format!("quest-operator-audit-{}", unix_ms()));
        let result =
            Ok("{\"accepted\":true,\"command_id\":\"cmd-1\",\"message\":\"accepted\"}".to_string());

        write_command_audit(
            &audit_dir,
            "http://127.0.0.1:8787",
            "{\"command_id\":\"cmd-1\",\"command_name\":\"runtime.start\",\"action\":\"start_session\"}",
            &result,
            100,
            120,
        )
        .unwrap();

        let audit_path = audit_dir.join("command_audit.jsonl");
        let text = fs::read_to_string(&audit_path).unwrap();
        assert!(text.contains("\"command_id\":\"cmd-1\""));
        assert!(text.contains("\"accepted\":true"));
        fs::remove_dir_all(&audit_dir).unwrap();
    }
}
