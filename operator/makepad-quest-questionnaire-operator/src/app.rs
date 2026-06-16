use std::path::PathBuf;

use makepad_widgets::*;

use crate::device;
use crate::protocol::{
    endpoint_url, BridgeCommandResponse, BridgeStatusResponse, OperatorCommandRequest, BLOCK1,
    BLOCK2, BLOCK3,
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
                                }

                                Panel{
                                    SectionTitle{text: "Quest"}

                                    FieldLabel{text: "ADB serial"}
                                    device_serial_input := Field{
                                        empty_text: "Quest serial"
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
    Command,
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
        match device::get_device_snapshot(&serial) {
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
                self.set_status(cx, "Quest status", "ADB snapshot refreshed.");
                self.set_last_response(
                    cx,
                    &serde_json::to_string_pretty(&snapshot)
                        .unwrap_or_else(|_| "Snapshot refreshed.".to_string()),
                );
            }
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

    fn send_command_request(
        &mut self,
        cx: &mut Cx,
        url: String,
        body: OperatorCommandRequest,
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

        self.set_status(cx, title, &format!("Posted {}", body.command_name));
        self.set_last_response(cx, &body_json);
    }

    fn apply_status_response(&self, cx: &mut Cx, response: BridgeStatusResponse, raw: &str) {
        self.render_foreground(cx, &response.foreground);
        let detail = response
            .message
            .as_deref()
            .unwrap_or("Bridge status received.");
        self.set_status(cx, "Bridge connected", detail);
        self.set_last_response(cx, raw);
    }

    fn apply_command_response(&self, cx: &mut Cx, response: BridgeCommandResponse, raw: &str) {
        self.render_foreground(cx, &response.foreground);
        let title = if response.accepted {
            "Command accepted"
        } else {
            "Command rejected"
        };
        let detail = response
            .message
            .as_deref()
            .unwrap_or("Bridge returned a command response.");
        self.set_status(cx, title, detail);
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

        if self.ui.button(cx, ids!(block1_button)).clicked(actions) {
            self.send_block_request(cx, &BLOCK1);
        }

        if self.ui.button(cx, ids!(block2_button)).clicked(actions) {
            self.send_block_request(cx, &BLOCK2);
        }

        if self.ui.button(cx, ids!(block3_button)).clicked(actions) {
            self.send_block_request(cx, &BLOCK3);
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
