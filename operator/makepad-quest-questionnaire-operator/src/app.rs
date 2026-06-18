use std::path::PathBuf;

use makepad_widgets::*;
use serde::Serialize;

use crate::cli::{
    device_status as device_status_command, pull_target_session as pull_target_session_command,
    verify_target_apk_command,
};
use crate::device;
use crate::profile::{load_operator_gui_profile, OperatorGuiProfileFields};
use crate::protocol::{
    endpoint_url, runtime_session_remote_relative_from_session_dir, validate_runtime_status,
    BridgeCommandResponse, BridgeStatusResponse, OperatorCommandRequest, RuntimeExportRequestSpec,
    RuntimeOperatorCommandRequest, RuntimePanelLaunchSpec, RuntimeProvenanceSpec,
    RuntimeQuestionnaireStateSpec, RuntimeSessionSpec, RuntimeStatusExpectation, RuntimeTargetSpec,
    BLOCK1, BLOCK2, BLOCK3, DEFAULT_RUNTIME_KIND, DEFAULT_RUNTIME_OPERATOR_PROTOCOL_VERSION,
    PANEL_PROTOCOL_VERSION,
};

app_main!(App);

script_mod! {
    use mod.prelude.widgets.*
    use mod.widgets.*

    let FieldLabel = OperatorFieldLabel{}
    let Field = OperatorCenteredField{}
    let PrimaryButton = OperatorPrimaryButton{}
    let SecondaryButton = OperatorSecondaryButton{}
    let Panel = OperatorPanel{}
    let SectionTitle = OperatorSectionTitle{}
    let StatusValue = OperatorStatusValue{}
    let MonoValue = OperatorMonoValue{}

    let app = startup() do #(App::script_component(vm)){
        ui: Root{
            main_window := Window{
                window.title: "Quest Questionnaire Operator"
                window.inner_size: vec2(1180, 960)
                body +: {
                    SolidView{
                        width: Fill
                        height: Fill
                        flow: Down
                        spacing: 0.0
                        draw_bg.color: #xf4f6f8

                        SolidView{
                            width: Fill
                            height: Fit
                            flow: Right
                            spacing: 14.0
                            padding: Inset{top: 8.0 right: 14.0 bottom: 8.0 left: 14.0}
                            draw_bg.color: #xffffff

                            View{
                                width: Fill
                                height: Fit
                                flow: Down
                                spacing: 2.0
                                Label{
                                    text: "Quest Questionnaire Operator"
                                    draw_text.color: #x111827
                                    draw_text.text_style.font_size: 10.0
                                }
                                status_detail := StatusValue{
                                    text: "Waiting for bridge status."
                                }
                            }

                            status_title := Label{
                                width: 160
                                text: "Idle"
                                draw_text.color: #x0f766e
                                draw_text.text_style.font_size: 10.0
                            }
                        }

                        View{
                            width: Fill
                            height: Fill
                            flow: Right
                            spacing: 12.0
                            padding: Inset{top: 12.0 right: 14.0 bottom: 14.0 left: 14.0}

                            View{
                                width: 330
                                height: Fill
                                flow: Down
                                spacing: 0.0

                                Panel{
                                    SectionTitle{text: "Bridge"}

                                    FieldLabel{text: "Endpoint"}
                                    endpoint_input := Field{
                                        text: "http://127.0.0.1:8787"
                                        empty_text: "http://<quest-ip>:8787"
                                    }

                                    FieldLabel{text: "Session"}
                                    session_input := Field{
                                        text: "maia-spatial-session-001"
                                        empty_text: "session id"
                                    }

                                    View{
                                        width: Fill
                                        height: Fit
                                        flow: Right
                                        spacing: 8.0
                                        View{
                                            width: Fill
                                            height: Fit
                                            flow: Down
                                            FieldLabel{text: "Participant"}
                                            participant_input := Field{
                                                text: "P001"
                                                empty_text: "ref"
                                            }
                                        }
                                        View{
                                            width: 64
                                            height: Fit
                                            flow: Down
                                            FieldLabel{text: "Language"}
                                            language_input := Field{
                                                text: "en"
                                                empty_text: "en"
                                            }
                                        }
                                    }

                                    View{
                                        width: Fill
                                        height: Fit
                                        flow: Right
                                        spacing: 8.0
                                        connect_button := PrimaryButton{text: "Poll"}
                                        dismiss_button := SecondaryButton{text: "Dismiss"}
                                    }

                                    FieldLabel{text: "Profile"}
                                    View{
                                        width: Fill
                                        height: Fit
                                        flow: Right
                                        spacing: 8.0
                                        profile_path_input := Field{
                                            empty_text: "operator profile JSON"
                                        }
                                        load_profile_button := SecondaryButton{text: "Load"}
                                    }
                                }

                                Panel{
                                    SectionTitle{text: "Quest"}

                                    FieldLabel{text: "ADB serial"}
                                    device_serial_input := Field{
                                        empty_text: "Quest serial"
                                    }

                                    FieldLabel{text: "Status out"}
                                    device_status_out_input := Field{
                                        empty_text: "optional device-status JSON"
                                    }

                                    FieldLabel{text: "Panel APK"}
                                    panel_apk_input := Field{
                                        text: "app/build/outputs/apk/minimal/debug/app-minimal-debug.apk"
                                        empty_text: "panel APK path"
                                    }

                                    View{
                                        width: Fill
                                        height: Fit
                                        flow: Right
                                        spacing: 8.0
                                        install_panel_button := PrimaryButton{text: "Install Panel"}
                                    }

                                    View{
                                        width: Fill
                                        height: Fit
                                        flow: Right
                                        spacing: 8.0
                                        View{
                                            width: Fill
                                            height: Fit
                                            flow: Down
                                            FieldLabel{text: "Host port"}
                                            host_port_input := Field{text: "8787"}
                                        }
                                        View{
                                            width: Fill
                                            height: Fit
                                            flow: Down
                                            FieldLabel{text: "Quest port"}
                                            device_port_input := Field{text: "8787"}
                                        }
                                    }

                                    View{
                                        width: Fill
                                        height: Fit
                                        flow: Right
                                        spacing: 8.0
                                        tooling_button := SecondaryButton{text: "Tools"}
                                        devices_button := SecondaryButton{text: "Devices"}
                                        device_status_button := SecondaryButton{text: "Status"}
                                        forward_button := PrimaryButton{text: "Forward"}
                                    }

                                    tooling_value := StatusValue{
                                        text: "Tooling not checked."
                                    }
                                    devices_value := StatusValue{
                                        text: "Devices not checked."
                                    }
                                }

                                Panel{
                                    SectionTitle{text: "Target Runtime"}

                                    FieldLabel{text: "Protocol"}
                                    runtime_protocol_input := Field{
                                        text: "target.runtime.operator.v1"
                                    }

                                    FieldLabel{text: "Target APK"}
                                    runtime_apk_input := Field{
                                        empty_text: "target APK path"
                                    }

                                    FieldLabel{text: "APK SHA-256"}
                                    runtime_apk_sha256_input := Field{
                                        empty_text: "optional expected digest"
                                    }

                                    FieldLabel{text: "APK report"}
                                    View{
                                        width: Fill
                                        height: Fit
                                        flow: Right
                                        spacing: 8.0
                                        runtime_apk_report_input := Field{
                                            empty_text: "optional report JSON"
                                        }
                                        runtime_verify_apk_button := SecondaryButton{text: "Verify APK"}
                                    }

                                    View{
                                        width: Fill
                                        height: Fit
                                        flow: Right
                                        spacing: 8.0
                                        runtime_install_apk_button := SecondaryButton{text: "Install APK"}
                                        runtime_launch_button := SecondaryButton{text: "Launch"}
                                    }

                                    View{
                                        width: Fill
                                        height: Fit
                                        flow: Right
                                        spacing: 8.0
                                        View{
                                            width: Fill
                                            height: Fit
                                            flow: Down
                                            FieldLabel{text: "Runtime kind"}
                                            runtime_kind_input := Field{
                                                text: "target_quest_apk"
                                            }
                                        }
                                        View{
                                            width: Fill
                                            height: Fit
                                            flow: Down
                                            FieldLabel{text: "Package"}
                                            runtime_package_input := Field{
                                                empty_text: "runtime package"
                                            }
                                        }
                                    }

                                    View{
                                        width: Fill
                                        height: Fit
                                        flow: Right
                                        spacing: 8.0
                                        View{
                                            width: Fill
                                            height: Fit
                                            flow: Down
                                            FieldLabel{text: "Study"}
                                            runtime_study_input := Field{
                                                text: "target-study"
                                            }
                                        }
                                        View{
                                            width: Fill
                                            height: Fit
                                            flow: Down
                                            FieldLabel{text: "Condition"}
                                            runtime_condition_input := Field{
                                                empty_text: "condition id"
                                            }
                                        }
                                    }

                                    View{
                                        width: Fill
                                        height: Fit
                                        flow: Right
                                        spacing: 8.0
                                        View{
                                            width: Fill
                                            height: Fit
                                            flow: Down
                                            FieldLabel{text: "Build tag"}
                                            runtime_build_tag_input := Field{
                                                empty_text: "apk variant"
                                            }
                                        }
                                        View{
                                            width: Fill
                                            height: Fit
                                            flow: Down
                                            FieldLabel{text: "Source scene"}
                                            runtime_source_scene_input := Field{
                                                text: "Assets/Scenes/Space.unity"
                                            }
                                        }
                                    }

                                    View{
                                        width: Fill
                                        height: Fit
                                        flow: Right
                                        spacing: 8.0
                                        View{
                                            width: Fill
                                            height: Fit
                                            flow: Down
                                            FieldLabel{text: "Questionnaire"}
                                            runtime_questionnaire_input := Field{
                                                text: "target-questionnaire-v1"
                                            }
                                        }
                                        View{
                                            width: Fill
                                            height: Fit
                                            flow: Down
                                            FieldLabel{text: "Stage"}
                                            runtime_stage_input := Field{
                                                text: "target:intro"
                                            }
                                        }
                                    }

                                    View{
                                        width: Fill
                                        height: Fit
                                        flow: Right
                                        spacing: 8.0
                                        View{
                                            width: Fill
                                            height: Fit
                                            flow: Down
                                            FieldLabel{text: "Marker"}
                                            runtime_marker_input := Field{
                                                text: "operator_marker"
                                            }
                                        }
                                        View{
                                            width: Fill
                                            height: Fit
                                            flow: Down
                                            FieldLabel{text: "Remote"}
                                            runtime_remote_input := Field{
                                                text: "files/runtime_csv"
                                            }
                                        }
                                    }

                                    FieldLabel{text: "Pull out"}
                                    runtime_pull_out_input := Field{
                                        text: "artifacts/device-session-pull"
                                        empty_text: "local output folder"
                                    }

                                    View{
                                        width: Fill
                                        height: Fit
                                        flow: Right
                                        spacing: 8.0
                                        runtime_preflight_button := SecondaryButton{text: "Preflight"}
                                        runtime_start_button := PrimaryButton{text: "Start"}
                                        runtime_marker_button := SecondaryButton{text: "Mark"}
                                        runtime_open_button := PrimaryButton{text: "Open Q"}
                                    }

                                    View{
                                        width: Fill
                                        height: Fit
                                        flow: Right
                                        spacing: 8.0
                                        runtime_stop_button := SecondaryButton{text: "Stop"}
                                        runtime_pull_button := SecondaryButton{text: "Pull"}
                                        runtime_pull_files_button := SecondaryButton{text: "Pull Files"}
                                    }
                                }
                            }

                            View{
                                width: Fill
                                height: Fill
                                flow: Down
                                spacing: 0.0

                                Panel{
                                    SectionTitle{text: "Questionnaire"}
                                    View{
                                        width: Fill
                                        height: Fit
                                        flow: Down
                                        spacing: 8.0
                                        block1_button := PrimaryButton{
                                            width: 260
                                            text: "Open Block 1"
                                        }
                                        block2_button := PrimaryButton{
                                            width: 260
                                            text: "Open Block 2"
                                        }
                                        block3_button := PrimaryButton{
                                            width: 260
                                            text: "Open Block 3"
                                        }
                                    }
                                }

                                View{
                                    width: Fill
                                    height: Fit
                                    flow: Down
                                    spacing: 0.0

                                    Panel{
                                        width: Fill
                                        SectionTitle{text: "Foreground"}
                                        foreground_value := StatusValue{
                                            text: "Foreground: unknown"
                                        }
                                        panel_value := StatusValue{
                                            text: "Panel foreground: unknown"
                                        }
                                        xr_value := StatusValue{
                                            text: "XR app foreground: unknown"
                                        }
                                        stage_value := StatusValue{
                                            text: "Open stage: unknown"
                                        }
                                    }

                                    Panel{
                                        width: Fill
                                        SectionTitle{text: "Device Snapshot"}
                                        device_snapshot_value := StatusValue{
                                            text: "No Quest snapshot yet."
                                        }
                                    }
                                }

                                Panel{
                                    height: Fill
                                    SectionTitle{text: "Last Response"}
                                    last_response_value := MonoValue{
                                        height: Fill
                                        text: "No response yet."
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    app
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum PendingRequest {
    Status,
    RuntimePreflight,
    Command,
}

#[derive(Clone, Debug, PartialEq, Eq)]
struct RuntimePreflightApproval {
    protocol_version: String,
    runtime_kind: String,
    runtime_package: String,
    source_scene_path: String,
}

#[derive(Script, ScriptHook)]
pub struct App {
    #[live]
    ui: WidgetRef,
    #[rust]
    request_counter: u64,
    #[rust]
    active_request_id: Option<LiveId>,
    #[rust]
    pending_request: Option<PendingRequest>,
    #[rust]
    runtime_preflight_approval: Option<RuntimePreflightApproval>,
}

impl App {
    fn field_text(&self, cx: &Cx, path: &[LiveId]) -> String {
        self.ui.text_input(cx, path).text().trim().to_string()
    }

    fn set_field_text(&self, cx: &mut Cx, path: &[LiveId], text: &str) {
        self.ui.text_input(cx, path).set_text(cx, text);
    }

    fn set_status(&self, cx: &mut Cx, title: &str, detail: &str) {
        self.ui.label(cx, ids!(status_title)).set_text(cx, title);
        self.ui.label(cx, ids!(status_detail)).set_text(cx, detail);
    }

    fn set_last_response(&self, cx: &mut Cx, text: &str) {
        self.ui
            .label(cx, ids!(last_response_value))
            .set_text(cx, text);
    }

    fn refresh_tooling(&self, cx: &mut Cx) {
        let status = device::tooling_status();
        self.ui
            .label(cx, ids!(tooling_value))
            .set_text(cx, &device::format_tooling_text(&status));
        let raw = serde_json::to_string_pretty(&status)
            .unwrap_or_else(|_| "Tooling checked.".to_string());
        self.set_status(
            cx,
            "Tooling",
            if status.adb.available {
                "ADB available."
            } else {
                "ADB unavailable."
            },
        );
        self.set_last_response(cx, &raw);
    }

    fn refresh_devices(&self, cx: &mut Cx) {
        match device::list_devices() {
            Ok(devices) => {
                self.ui
                    .label(cx, ids!(devices_value))
                    .set_text(cx, &device::format_devices_text(&devices));
                if self.field_text(cx, ids!(device_serial_input)).is_empty() {
                    if let Some(device) = devices.iter().find(|device| device.state == "device") {
                        self.set_field_text(cx, ids!(device_serial_input), &device.serial);
                    }
                }
                self.set_status(cx, "Devices", "ADB device list refreshed.");
                self.set_last_response(
                    cx,
                    &serde_json::to_string_pretty(&devices)
                        .unwrap_or_else(|_| "Devices refreshed.".to_string()),
                );
            }
            Err(err) => {
                self.ui.label(cx, ids!(devices_value)).set_text(cx, &err);
                self.set_status(cx, "Device error", &err);
                self.set_last_response(cx, &err);
            }
        }
    }

    fn refresh_device_status(&self, cx: &mut Cx) {
        let serial = self.field_text(cx, ids!(device_serial_input));
        let out_text = self.field_text(cx, ids!(device_status_out_input));
        let out_path = if out_text.is_empty() {
            None
        } else {
            Some(PathBuf::from(out_text))
        };
        match device_status_command(&serial, out_path.as_deref(), true) {
            Ok(raw) => match serde_json::from_str::<device::QuestSnapshot>(&raw) {
                Ok(snapshot) => {
                    self.ui
                        .label(cx, ids!(device_snapshot_value))
                        .set_text(cx, &device::format_snapshot_text(&snapshot));
                    if let Some(component) = snapshot
                        .foreground_component
                        .as_ref()
                        .or(snapshot.focused_window.as_ref())
                    {
                        self.ui
                            .label(cx, ids!(foreground_value))
                            .set_text(cx, &format!("Foreground: {component}"));
                    }
                    let detail = out_path
                        .as_ref()
                        .map(|path| format!("ADB snapshot written to {}.", path.display()))
                        .unwrap_or_else(|| "ADB snapshot refreshed.".to_string());
                    self.set_status(cx, "Quest status", &detail);
                    self.set_last_response(cx, &raw);
                }
                Err(err) => {
                    self.set_status(
                        cx,
                        "Quest error",
                        &format!("Could not parse snapshot: {err}"),
                    );
                    self.set_last_response(cx, &raw);
                }
            },
            Err(err) => {
                self.ui
                    .label(cx, ids!(device_snapshot_value))
                    .set_text(cx, &err);
                self.set_status(cx, "Quest error", &err);
                self.set_last_response(cx, &err);
            }
        }
    }

    fn forward_bridge(&self, cx: &mut Cx) {
        let serial = self.field_text(cx, ids!(device_serial_input));
        let host_port = match parse_port(&self.field_text(cx, ids!(host_port_input)), "host port") {
            Ok(port) => port,
            Err(err) => {
                self.set_status(cx, "Port error", &err);
                return;
            }
        };
        let device_port =
            match parse_port(&self.field_text(cx, ids!(device_port_input)), "Quest port") {
                Ok(port) => port,
                Err(err) => {
                    self.set_status(cx, "Port error", &err);
                    return;
                }
            };
        match device::forward_bridge(&serial, host_port, device_port) {
            Ok(run) => {
                let endpoint = format!("http://127.0.0.1:{host_port}");
                self.set_field_text(cx, ids!(endpoint_input), &endpoint);
                self.set_status(cx, "Bridge forwarded", &endpoint);
                self.set_last_response(
                    cx,
                    &serde_json::to_string_pretty(&run)
                        .unwrap_or_else(|_| "Bridge forwarded.".to_string()),
                );
            }
            Err(err) => {
                self.set_status(cx, "Forward error", &err);
                self.set_last_response(cx, &err);
            }
        }
    }

    fn install_panel(&self, cx: &mut Cx) {
        let serial = self.field_text(cx, ids!(device_serial_input));
        let apk = PathBuf::from(self.field_text(cx, ids!(panel_apk_input)));
        match device::install_apk(&serial, &apk) {
            Ok(run) => {
                self.set_status(cx, "Panel installed", &format!("{}", apk.display()));
                self.set_last_response(
                    cx,
                    &serde_json::to_string_pretty(&run)
                        .unwrap_or_else(|_| "Panel APK installed.".to_string()),
                );
            }
            Err(err) => {
                self.set_status(cx, "Install error", &err);
                self.set_last_response(cx, &err);
            }
        }
    }

    fn runtime_apk_verification_inputs(
        &self,
        cx: &Cx,
    ) -> Result<(PathBuf, Option<String>, Option<PathBuf>), String> {
        let apk_text = self.field_text(cx, ids!(runtime_apk_input));
        if apk_text.is_empty() {
            return Err("Target APK path is empty.".to_string());
        }

        let expected_text = self.field_text(cx, ids!(runtime_apk_sha256_input));
        let expected_sha256 = if expected_text.is_empty() {
            None
        } else {
            Some(expected_text)
        };
        let report_text = self.field_text(cx, ids!(runtime_apk_report_input));
        let report_path = if report_text.is_empty() {
            None
        } else {
            Some(PathBuf::from(report_text))
        };

        Ok((PathBuf::from(apk_text), expected_sha256, report_path))
    }

    fn verify_runtime_apk(&self, cx: &mut Cx) {
        let (apk, expected_sha256, report_path) = match self.runtime_apk_verification_inputs(cx) {
            Ok(inputs) => inputs,
            Err(err) => {
                self.set_status(cx, "APK verify error", &err);
                return;
            }
        };

        match verify_target_apk_command(
            &apk,
            expected_sha256.as_deref(),
            report_path.as_deref(),
            true,
        ) {
            Ok(output) => {
                self.set_status(cx, "APK verified", &format!("{}", apk.display()));
                self.set_last_response(cx, &output);
            }
            Err(err) => {
                self.set_status(
                    cx,
                    "APK verify error",
                    "Verification failed; see Last Response.",
                );
                self.set_last_response(cx, &err);
            }
        }
    }

    fn install_runtime_apk(&self, cx: &mut Cx) {
        let serial = self.field_text(cx, ids!(device_serial_input));
        let (apk, expected_sha256, report_path) = match self.runtime_apk_verification_inputs(cx) {
            Ok(inputs) => inputs,
            Err(err) => {
                self.set_status(cx, "Install error", &err);
                return;
            }
        };

        let verification = match verify_target_apk_command(
            &apk,
            expected_sha256.as_deref(),
            report_path.as_deref(),
            true,
        ) {
            Ok(output) => output,
            Err(err) => {
                self.set_status(
                    cx,
                    "APK verify error",
                    "Verification failed before install.",
                );
                self.set_last_response(cx, &err);
                return;
            }
        };

        match device::install_apk(&serial, &apk) {
            Ok(run) => {
                self.set_status(cx, "Target installed", &format!("{}", apk.display()));
                let install_json = serde_json::to_string_pretty(&run)
                    .unwrap_or_else(|_| "Target APK installed.".to_string());
                self.set_last_response(cx, &format!("{verification}\n\n{install_json}"));
            }
            Err(err) => {
                self.set_status(cx, "Install error", &err);
                self.set_last_response(cx, &err);
            }
        }
    }

    fn launch_runtime(&self, cx: &mut Cx) {
        let serial = self.field_text(cx, ids!(device_serial_input));
        let package = self.field_text(cx, ids!(runtime_package_input));
        match device::launch_package(&serial, &package, None) {
            Ok(run) => {
                self.set_status(cx, "Runtime launch", &package);
                self.set_last_response(
                    cx,
                    &serde_json::to_string_pretty(&run)
                        .unwrap_or_else(|_| "Target runtime launch requested.".to_string()),
                );
            }
            Err(err) => {
                self.set_status(cx, "Launch error", &err);
                self.set_last_response(cx, &err);
            }
        }
    }

    fn pull_runtime_files(&self, cx: &mut Cx) {
        let serial = self.field_text(cx, ids!(device_serial_input));
        let package = self.field_text(cx, ids!(runtime_package_input));
        let remote_relative = self.field_text(cx, ids!(runtime_remote_input));
        let output_dir = PathBuf::from(self.field_text(cx, ids!(runtime_pull_out_input)));
        let expected_files = Vec::new();
        match pull_target_session_command(
            &serial,
            &package,
            &remote_relative,
            &output_dir,
            true,
            None,
            &expected_files,
            true,
            None,
            true,
        ) {
            Ok(report) => {
                self.set_status(
                    cx,
                    "Runtime files pulled",
                    &format!("{}", output_dir.display()),
                );
                self.set_last_response(cx, &report);
            }
            Err(err) => {
                self.set_status(cx, "Pull error", &err);
                self.set_last_response(cx, &err);
            }
        }
    }

    fn load_operator_profile(&self, cx: &mut Cx) {
        let profile_path = self.field_text(cx, ids!(profile_path_input));
        if profile_path.is_empty() {
            self.set_status(cx, "Profile error", "Profile path is empty.");
            return;
        }

        match load_operator_gui_profile(&PathBuf::from(&profile_path)) {
            Ok(profile) => {
                self.apply_profile_fields(cx, &profile.makepad_gui_fields);
                let detail = if profile.profile_id.trim().is_empty() {
                    profile_path
                } else {
                    format!("Loaded {}", profile.profile_id)
                };
                self.set_status(cx, "Profile loaded", &detail);
                self.set_last_response(
                    cx,
                    &serde_json::to_string_pretty(&profile)
                        .unwrap_or_else(|_| "Profile loaded.".to_string()),
                );
            }
            Err(err) => {
                self.set_status(cx, "Profile error", &err);
                self.set_last_response(cx, &err);
            }
        }
    }

    fn apply_profile_fields(&self, cx: &mut Cx, fields: &OperatorGuiProfileFields) {
        self.set_profile_field(cx, ids!(endpoint_input), &fields.endpoint);
        self.set_profile_field(cx, ids!(session_input), &fields.session);
        self.set_profile_field(cx, ids!(participant_input), &fields.participant);
        self.set_profile_field(cx, ids!(language_input), &fields.language);
        self.set_profile_field(cx, ids!(device_serial_input), &fields.adb_serial);
        self.set_profile_field(cx, ids!(device_status_out_input), &fields.device_status_out);
        self.set_profile_field(cx, ids!(host_port_input), &fields.host_port);
        self.set_profile_field(cx, ids!(device_port_input), &fields.quest_port);
        self.set_profile_field(cx, ids!(runtime_apk_input), &fields.target_apk_path);
        self.set_profile_field(
            cx,
            ids!(runtime_apk_sha256_input),
            &fields.target_apk_sha256,
        );
        self.set_profile_field(
            cx,
            ids!(runtime_apk_report_input),
            &fields.target_apk_report,
        );
        self.set_profile_field(cx, ids!(runtime_pull_out_input), &fields.target_pull_out);
        self.set_profile_field(cx, ids!(runtime_protocol_input), &fields.runtime_protocol);
        self.set_profile_field(cx, ids!(runtime_kind_input), &fields.runtime_kind);
        self.set_profile_field(cx, ids!(runtime_package_input), &fields.runtime_package);
        self.set_profile_field(cx, ids!(runtime_study_input), &fields.runtime_study);
        self.set_profile_field(cx, ids!(runtime_condition_input), &fields.runtime_condition);
        self.set_profile_field(cx, ids!(runtime_build_tag_input), &fields.runtime_build_tag);
        self.set_profile_field(
            cx,
            ids!(runtime_source_scene_input),
            &fields.runtime_source_scene,
        );
        self.set_profile_field(
            cx,
            ids!(runtime_questionnaire_input),
            &fields.runtime_questionnaire,
        );
        self.set_profile_field(cx, ids!(runtime_stage_input), &fields.runtime_stage);
        self.set_profile_field(cx, ids!(runtime_marker_input), &fields.runtime_marker);
        self.set_profile_field(cx, ids!(runtime_remote_input), &fields.runtime_remote);
    }

    fn set_profile_field(&self, cx: &mut Cx, path: &[LiveId], value: &str) {
        if !value.trim().is_empty() {
            self.set_field_text(cx, path, value);
        }
    }

    fn send_status_request(&mut self, cx: &mut Cx) {
        if self.active_request_id.is_some() {
            self.set_status(cx, "Busy", "A bridge request is already in flight.");
            return;
        }

        let endpoint = self.field_text(cx, ids!(endpoint_input));
        let url = match endpoint_url(&endpoint, "/v1/status") {
            Ok(url) => url,
            Err(message) => {
                self.set_status(cx, "Endpoint error", &message);
                return;
            }
        };

        self.request_counter += 1;
        let request_id = LiveId::from_str_num("quest_questionnaire_status", self.request_counter);
        self.active_request_id = Some(request_id);
        self.pending_request = Some(PendingRequest::Status);

        let mut request = HttpRequest::new(url, HttpMethod::GET);
        request.set_header("Accept".to_string(), "application/json".to_string());
        cx.http_request(request_id, request);

        self.set_status(cx, "Polling", "Waiting for bridge status.");
    }

    fn send_runtime_preflight_request(&mut self, cx: &mut Cx) {
        if self.active_request_id.is_some() {
            self.set_status(cx, "Busy", "A bridge request is already in flight.");
            return;
        }

        let endpoint = self.field_text(cx, ids!(endpoint_input));
        let url = match endpoint_url(&endpoint, "/v1/status") {
            Ok(url) => url,
            Err(message) => {
                self.set_status(cx, "Endpoint error", &message);
                return;
            }
        };

        self.request_counter += 1;
        let request_id = LiveId::from_str_num("target_runtime_preflight", self.request_counter);
        self.active_request_id = Some(request_id);
        self.pending_request = Some(PendingRequest::RuntimePreflight);

        let mut request = HttpRequest::new(url, HttpMethod::GET);
        request.set_header("Accept".to_string(), "application/json".to_string());
        cx.http_request(request_id, request);

        self.set_status(cx, "Runtime preflight", "Checking target runtime status.");
    }

    fn send_block_request(&mut self, cx: &mut Cx, block: &'static crate::protocol::BlockSpec) {
        let endpoint = self.field_text(cx, ids!(endpoint_input));
        let url = match endpoint_url(&endpoint, "/v1/command") {
            Ok(url) => url,
            Err(message) => {
                self.set_status(cx, "Endpoint error", &message);
                return;
            }
        };

        self.request_counter += 1;
        let command_id = format!("operator-{:06}", self.request_counter);
        let body = OperatorCommandRequest::open_block(
            command_id,
            block,
            self.field_text(cx, ids!(session_input)),
            self.field_text(cx, ids!(participant_input)),
            self.field_text(cx, ids!(language_input)),
        );

        self.send_command_request(cx, url, body, &format!("Sending {}", block.label));
    }

    fn send_dismiss_request(&mut self, cx: &mut Cx) {
        let endpoint = self.field_text(cx, ids!(endpoint_input));
        let url = match endpoint_url(&endpoint, "/v1/command") {
            Ok(url) => url,
            Err(message) => {
                self.set_status(cx, "Endpoint error", &message);
                return;
            }
        };

        self.request_counter += 1;
        let command_id = format!("operator-{:06}", self.request_counter);
        let body =
            OperatorCommandRequest::dismiss(command_id, self.field_text(cx, ids!(session_input)));
        self.send_command_request(cx, url, body, "Sending dismiss");
    }

    fn send_runtime_start_request(&mut self, cx: &mut Cx) {
        if !self.require_matching_runtime_preflight(cx) {
            return;
        }

        let url = match self.command_url(cx) {
            Some(url) => url,
            None => return,
        };

        self.request_counter += 1;
        let body = RuntimeOperatorCommandRequest::start_session(
            format!("runtime-start-{:06}", self.request_counter),
            self.runtime_protocol(cx),
            self.runtime_target(cx),
            self.runtime_session(cx),
            self.runtime_provenance(cx),
        );
        self.send_command_request(cx, url, body, "Starting runtime session");
    }

    fn send_runtime_marker_request(&mut self, cx: &mut Cx) {
        if !self.require_matching_runtime_preflight(cx) {
            return;
        }

        let url = match self.command_url(cx) {
            Some(url) => url,
            None => return,
        };

        self.request_counter += 1;
        let marker_name = self.field_text(cx, ids!(runtime_marker_input));
        let body = RuntimeOperatorCommandRequest::mark_timing_event(
            format!("runtime-marker-{:06}", self.request_counter),
            self.runtime_protocol(cx),
            self.runtime_target(cx),
            self.field_text(cx, ids!(session_input)),
            marker_name,
            "operator GUI marker",
        );
        self.send_command_request(cx, url, body, "Marking runtime event");
    }

    fn send_runtime_open_request(&mut self, cx: &mut Cx) {
        if !self.require_matching_runtime_preflight(cx) {
            return;
        }

        let url = match self.command_url(cx) {
            Some(url) => url,
            None => return,
        };

        self.request_counter += 1;
        let body = RuntimeOperatorCommandRequest::open_questionnaire(
            format!("runtime-open-{:06}", self.request_counter),
            self.runtime_protocol(cx),
            self.runtime_target(cx),
            self.runtime_panel_request(cx),
        );
        self.send_command_request(cx, url, body, "Opening runtime questionnaire");
    }

    fn send_runtime_stop_request(&mut self, cx: &mut Cx) {
        if !self.require_matching_runtime_preflight(cx) {
            return;
        }

        let url = match self.command_url(cx) {
            Some(url) => url,
            None => return,
        };

        self.request_counter += 1;
        let body = RuntimeOperatorCommandRequest::stop_session(
            format!("runtime-stop-{:06}", self.request_counter),
            self.runtime_protocol(cx),
            self.runtime_target(cx),
            self.field_text(cx, ids!(session_input)),
        );
        self.send_command_request(cx, url, body, "Stopping runtime session");
    }

    fn send_runtime_pull_request(&mut self, cx: &mut Cx) {
        if !self.require_matching_runtime_preflight(cx) {
            return;
        }

        let url = match self.command_url(cx) {
            Some(url) => url,
            None => return,
        };

        self.request_counter += 1;
        let body = RuntimeOperatorCommandRequest::pull_session(
            format!("runtime-pull-{:06}", self.request_counter),
            self.runtime_protocol(cx),
            self.runtime_target(cx),
            self.field_text(cx, ids!(session_input)),
            RuntimeExportRequestSpec {
                pull_device_session: true,
                quest_storage_policy: "app_private_only".to_string(),
                windows_storage_policy: "explicit_pull_only".to_string(),
                quest_package: self.field_text(cx, ids!(runtime_package_input)),
                quest_remote_relative: self.field_text(cx, ids!(runtime_remote_input)),
                windows_device_pull_subfolder: "device-session-pull".to_string(),
                expected_files: Vec::new(),
            },
        );
        self.send_command_request(cx, url, body, "Requesting runtime export");
    }

    fn command_url(&self, cx: &mut Cx) -> Option<String> {
        let endpoint = self.field_text(cx, ids!(endpoint_input));
        match endpoint_url(&endpoint, "/v1/command") {
            Ok(url) => Some(url),
            Err(message) => {
                self.set_status(cx, "Endpoint error", &message);
                None
            }
        }
    }

    fn runtime_protocol(&self, cx: &Cx) -> String {
        let value = self.field_text(cx, ids!(runtime_protocol_input));
        if value.is_empty() {
            DEFAULT_RUNTIME_OPERATOR_PROTOCOL_VERSION.to_string()
        } else {
            value
        }
    }

    fn runtime_kind(&self, cx: &Cx) -> String {
        let value = self.field_text(cx, ids!(runtime_kind_input));
        if value.is_empty() {
            DEFAULT_RUNTIME_KIND.to_string()
        } else {
            value
        }
    }

    fn runtime_target(&self, cx: &Cx) -> RuntimeTargetSpec {
        RuntimeTargetSpec::new(
            self.runtime_kind(cx),
            self.field_text(cx, ids!(runtime_package_input)),
            self.field_text(cx, ids!(endpoint_input)),
            self.field_text(cx, ids!(device_serial_input)),
        )
    }

    fn runtime_preflight_key(&self, cx: &Cx) -> RuntimePreflightApproval {
        RuntimePreflightApproval {
            protocol_version: self.runtime_protocol(cx),
            runtime_kind: self.runtime_kind(cx),
            runtime_package: self.field_text(cx, ids!(runtime_package_input)),
            source_scene_path: self.field_text(cx, ids!(runtime_source_scene_input)),
        }
    }

    fn has_matching_runtime_preflight(&self, cx: &Cx) -> bool {
        self.runtime_preflight_approval
            .as_ref()
            .map(|approval| approval == &self.runtime_preflight_key(cx))
            .unwrap_or(false)
    }

    fn require_matching_runtime_preflight(&self, cx: &mut Cx) -> bool {
        if self.has_matching_runtime_preflight(cx) {
            return true;
        }

        self.set_status(
            cx,
            "Runtime preflight required",
            "Run Target Runtime Preflight after Forward/Poll and before Start/Mark/Open Q/Stop/Pull.",
        );
        false
    }

    fn runtime_session(&self, cx: &Cx) -> RuntimeSessionSpec {
        RuntimeSessionSpec {
            study_id: self.field_text(cx, ids!(runtime_study_input)),
            session_id: self.field_text(cx, ids!(session_input)),
            participant_ref: self.field_text(cx, ids!(participant_input)),
            condition_id: self.field_text(cx, ids!(runtime_condition_input)),
            language_code: self.field_text(cx, ids!(language_input)),
            ..RuntimeSessionSpec::default()
        }
    }

    fn runtime_provenance(&self, cx: &Cx) -> RuntimeProvenanceSpec {
        RuntimeProvenanceSpec {
            runtime_build_tag: self.field_text(cx, ids!(runtime_build_tag_input)),
            source_scene_path: self.field_text(cx, ids!(runtime_source_scene_input)),
            ..RuntimeProvenanceSpec::default()
        }
    }

    fn runtime_panel_request(&self, cx: &Cx) -> RuntimePanelLaunchSpec {
        let open_stage = self.field_text(cx, ids!(runtime_stage_input));
        let questionnaire_id = self.field_text(cx, ids!(runtime_questionnaire_input));
        RuntimePanelLaunchSpec {
            protocol_version: PANEL_PROTOCOL_VERSION.to_string(),
            session_id: self.field_text(cx, ids!(session_input)),
            study_id: self.field_text(cx, ids!(runtime_study_input)),
            schema_id: questionnaire_id.clone(),
            questionnaire_id,
            open_stage: open_stage.clone(),
            screen_sequence: if open_stage.is_empty() {
                Vec::new()
            } else {
                vec![open_stage.clone()]
            },
            condition_number: -1,
            participant_ref: self.field_text(cx, ids!(participant_input)),
            caller_package_name: self.field_text(cx, ids!(runtime_package_input)),
            caller_app_version: String::new(),
            questionnaire_state: Some(RuntimeQuestionnaireStateSpec {
                language_code: self.field_text(cx, ids!(language_input)),
                condition_id: self.field_text(cx, ids!(runtime_condition_input)),
                operator_stage: open_stage,
                ..RuntimeQuestionnaireStateSpec::default()
            }),
        }
    }

    fn runtime_status_expectation(&self, cx: &Cx) -> RuntimeStatusExpectation {
        RuntimeStatusExpectation {
            runtime_kind: Some(self.runtime_kind(cx)),
            runtime_package: Some(self.field_text(cx, ids!(runtime_package_input))),
            source_scene_path: Some(self.field_text(cx, ids!(runtime_source_scene_input))),
            operator_protocol: Some(self.runtime_protocol(cx)),
            required_actions: vec![
                "start_session".to_string(),
                "stop_session".to_string(),
                "mark_timing_event".to_string(),
                "open_questionnaire".to_string(),
                "pull_session".to_string(),
            ],
            require_app_private_session_bundle: true,
            require_explicit_pull: true,
            require_questionnaire_panel_launch: true,
            require_questionnaire_result_callback_ingest: true,
            require_lsl_clock_alignment: true,
        }
    }

    fn send_command_request<T: Serialize>(
        &mut self,
        cx: &mut Cx,
        url: String,
        body: T,
        title: &str,
    ) {
        if self.active_request_id.is_some() {
            self.set_status(cx, "Busy", "A bridge request is already in flight.");
            return;
        }

        let body_json = match serde_json::to_string_pretty(&body) {
            Ok(json) => json,
            Err(err) => {
                self.set_status(
                    cx,
                    "Command error",
                    &format!("Could not encode JSON: {err}"),
                );
                return;
            }
        };

        self.request_counter += 1;
        let request_id = LiveId::from_str_num("quest_questionnaire_command", self.request_counter);
        self.active_request_id = Some(request_id);
        self.pending_request = Some(PendingRequest::Command);

        let mut request = HttpRequest::new(url, HttpMethod::POST);
        request.set_header("Accept".to_string(), "application/json".to_string());
        request.set_header("Content-Type".to_string(), "application/json".to_string());
        request.set_body_string(&body_json);
        cx.http_request(request_id, request);

        let command_name = serde_json::from_str::<serde_json::Value>(&body_json)
            .ok()
            .and_then(|value| {
                value
                    .get("command_name")
                    .and_then(serde_json::Value::as_str)
                    .map(str::to_string)
            })
            .unwrap_or_else(|| "command".to_string());
        self.set_status(cx, title, &format!("Posted {command_name}"));
        self.set_last_response(cx, &body_json);
    }

    fn autofill_runtime_remote_from_session_dir(
        &self,
        cx: &mut Cx,
        session_dir: Option<&str>,
    ) -> Option<String> {
        let remote = runtime_session_remote_relative_from_session_dir(session_dir?)?;
        if self.field_text(cx, ids!(runtime_remote_input)) != remote {
            self.set_field_text(cx, ids!(runtime_remote_input), &remote);
        }
        Some(remote)
    }

    fn apply_status_response(&self, cx: &mut Cx, response: BridgeStatusResponse, raw: &str) {
        self.render_foreground(cx, &response.foreground);
        let mut detail = response
            .message
            .as_deref()
            .unwrap_or("Bridge status received.")
            .to_string();
        if let Some(runtime_summary) = response.runtime_summary() {
            detail.push_str(" | ");
            detail.push_str(&runtime_summary);
        }
        if let Some(remote) =
            self.autofill_runtime_remote_from_session_dir(cx, response.session_dir.as_deref())
        {
            detail.push_str(" | remote: ");
            detail.push_str(&remote);
        }
        self.set_status(cx, "Bridge connected", &detail);
        self.set_last_response(cx, raw);
    }

    fn apply_runtime_preflight_response(
        &mut self,
        cx: &mut Cx,
        response: BridgeStatusResponse,
        raw: &str,
    ) {
        self.render_foreground(cx, &response.foreground);
        let issues = validate_runtime_status(&response, &self.runtime_status_expectation(cx));
        if issues.is_empty() {
            self.runtime_preflight_approval = Some(self.runtime_preflight_key(cx));
            let mut detail =
                "Target runtime matches expected protocol and capabilities.".to_string();
            if let Some(runtime_summary) = response.runtime_summary() {
                detail.push_str(" | ");
                detail.push_str(&runtime_summary);
            }
            if let Some(remote) =
                self.autofill_runtime_remote_from_session_dir(cx, response.session_dir.as_deref())
            {
                detail.push_str(" | remote: ");
                detail.push_str(&remote);
            }
            self.set_status(cx, "Runtime preflight passed", &detail);
        } else {
            self.runtime_preflight_approval = None;
            self.set_status(
                cx,
                "Runtime preflight failed",
                &format!("{} issue(s): {}", issues.len(), issues.join("; ")),
            );
        }
        self.set_last_response(cx, raw);
    }

    fn apply_command_response(&self, cx: &mut Cx, response: BridgeCommandResponse, raw: &str) {
        self.render_foreground(cx, &response.foreground);
        let title = if response.accepted {
            "Command accepted"
        } else {
            "Command rejected"
        };
        let mut detail = response
            .message
            .as_deref()
            .unwrap_or("Bridge returned a command response.")
            .to_string();
        if let Some(remote) =
            self.autofill_runtime_remote_from_session_dir(cx, response.session_dir.as_deref())
        {
            detail.push_str(" | remote: ");
            detail.push_str(&remote);
        }
        self.set_status(cx, title, &detail);
        self.set_last_response(cx, raw);
    }

    fn render_foreground(&self, cx: &mut Cx, foreground: &crate::protocol::ForegroundState) {
        let package = foreground
            .foreground_package
            .as_deref()
            .unwrap_or("unknown");
        let activity = foreground
            .foreground_activity
            .as_deref()
            .unwrap_or("unknown");
        self.ui
            .label(cx, ids!(foreground_value))
            .set_text(cx, &format!("Foreground: {package} / {activity}"));
        self.ui.label(cx, ids!(panel_value)).set_text(
            cx,
            &format!(
                "Panel foreground: {}",
                format_bool(foreground.panel_foreground)
            ),
        );
        self.ui.label(cx, ids!(xr_value)).set_text(
            cx,
            &format!(
                "XR app foreground: {}",
                format_bool(foreground.xr_app_foreground)
            ),
        );
        self.ui.label(cx, ids!(stage_value)).set_text(
            cx,
            &format!(
                "Open stage: {}",
                foreground.open_stage.as_deref().unwrap_or("unknown")
            ),
        );
    }

    fn fail(&mut self, cx: &mut Cx, message: String) {
        self.active_request_id = None;
        self.pending_request = None;
        self.set_status(cx, "Bridge error", &message);
        self.set_last_response(cx, &message);
    }
}

impl MatchEvent for App {
    fn handle_actions(&mut self, cx: &mut Cx, actions: &Actions) {
        if self.ui.button(cx, ids!(connect_button)).clicked(actions) {
            self.send_status_request(cx);
        }

        if self.ui.button(cx, ids!(dismiss_button)).clicked(actions) {
            self.send_dismiss_request(cx);
        }

        if self.ui.button(cx, ids!(tooling_button)).clicked(actions) {
            self.refresh_tooling(cx);
        }

        if self.ui.button(cx, ids!(devices_button)).clicked(actions) {
            self.refresh_devices(cx);
        }

        if self
            .ui
            .button(cx, ids!(device_status_button))
            .clicked(actions)
        {
            self.refresh_device_status(cx);
        }

        if self.ui.button(cx, ids!(forward_button)).clicked(actions) {
            self.forward_bridge(cx);
        }

        if self
            .ui
            .button(cx, ids!(install_panel_button))
            .clicked(actions)
        {
            self.install_panel(cx);
        }

        if self
            .ui
            .button(cx, ids!(load_profile_button))
            .clicked(actions)
        {
            self.load_operator_profile(cx);
        }

        if self
            .ui
            .button(cx, ids!(runtime_verify_apk_button))
            .clicked(actions)
        {
            self.verify_runtime_apk(cx);
        }

        if self
            .ui
            .button(cx, ids!(runtime_install_apk_button))
            .clicked(actions)
        {
            self.install_runtime_apk(cx);
        }

        if self
            .ui
            .button(cx, ids!(runtime_launch_button))
            .clicked(actions)
        {
            self.launch_runtime(cx);
        }

        if self.ui.button(cx, ids!(block1_button)).clicked(actions) {
            self.send_block_request(cx, &BLOCK1);
        }

        if self.ui.button(cx, ids!(block2_button)).clicked(actions) {
            self.send_block_request(cx, &BLOCK2);
        }

        if self.ui.button(cx, ids!(block3_button)).clicked(actions) {
            self.send_block_request(cx, &BLOCK3);
        }

        if self
            .ui
            .button(cx, ids!(runtime_preflight_button))
            .clicked(actions)
        {
            self.send_runtime_preflight_request(cx);
        }

        if self
            .ui
            .button(cx, ids!(runtime_start_button))
            .clicked(actions)
        {
            self.send_runtime_start_request(cx);
        }

        if self
            .ui
            .button(cx, ids!(runtime_marker_button))
            .clicked(actions)
        {
            self.send_runtime_marker_request(cx);
        }

        if self
            .ui
            .button(cx, ids!(runtime_open_button))
            .clicked(actions)
        {
            self.send_runtime_open_request(cx);
        }

        if self
            .ui
            .button(cx, ids!(runtime_stop_button))
            .clicked(actions)
        {
            self.send_runtime_stop_request(cx);
        }

        if self
            .ui
            .button(cx, ids!(runtime_pull_button))
            .clicked(actions)
        {
            self.send_runtime_pull_request(cx);
        }

        if self
            .ui
            .button(cx, ids!(runtime_pull_files_button))
            .clicked(actions)
        {
            self.pull_runtime_files(cx);
        }
    }

    fn handle_http_response(&mut self, cx: &mut Cx, request_id: LiveId, response: &HttpResponse) {
        if Some(request_id) != self.active_request_id {
            return;
        }

        let pending = self.pending_request.take();
        self.active_request_id = None;

        let raw = response
            .get_string_body()
            .unwrap_or_else(|| "<empty response body>".to_string());

        if !(200..300).contains(&response.status_code) {
            self.fail(
                cx,
                format!("HTTP {} from bridge: {}", response.status_code, raw),
            );
            return;
        }

        match pending {
            Some(PendingRequest::Status) => {
                match serde_json::from_str::<BridgeStatusResponse>(&raw) {
                    Ok(status) => self.apply_status_response(cx, status, &raw),
                    Err(err) => self.fail(cx, format!("Could not parse status JSON: {err}\n{raw}")),
                }
            }
            Some(PendingRequest::RuntimePreflight) => {
                match serde_json::from_str::<BridgeStatusResponse>(&raw) {
                    Ok(status) => self.apply_runtime_preflight_response(cx, status, &raw),
                    Err(err) => self.fail(cx, format!("Could not parse status JSON: {err}\n{raw}")),
                }
            }
            Some(PendingRequest::Command) => {
                match serde_json::from_str::<BridgeCommandResponse>(&raw) {
                    Ok(command) => self.apply_command_response(cx, command, &raw),
                    Err(err) => {
                        self.fail(cx, format!("Could not parse command JSON: {err}\n{raw}"))
                    }
                }
            }
            None => self.fail(cx, format!("Unexpected bridge response: {raw}")),
        }
    }

    fn handle_http_request_error(&mut self, cx: &mut Cx, request_id: LiveId, err: &HttpError) {
        if Some(request_id) != self.active_request_id {
            return;
        }
        self.fail(cx, err.message.clone());
    }
}

impl AppMain for App {
    fn script_mod(vm: &mut ScriptVm) -> ScriptValue {
        crate::makepad_widgets::script_mod(vm);
        crate::ui::script_mod(vm);
        self::script_mod(vm)
    }

    fn handle_event(&mut self, cx: &mut Cx, event: &Event) {
        self.match_event(cx, event);
        self.ui.handle_event(cx, event, &mut Scope::empty());
    }
}

fn parse_port(raw: &str, label: &str) -> Result<u16, String> {
    raw.trim()
        .parse::<u16>()
        .ok()
        .filter(|port| *port > 0)
        .ok_or_else(|| format!("{label} must be between 1 and 65535"))
}

fn format_bool(value: Option<bool>) -> &'static str {
    match value {
        Some(true) => "yes",
        Some(false) => "no",
        None => "unknown",
    }
}
