use std::fs;
use std::io::{Read, Write};
use std::net::TcpStream;
use std::path::{Path, PathBuf};
use std::thread;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};

use crate::device;
use crate::protocol::{
    block_by_number, endpoint_url, BridgeStatusResponse, OperatorCommandRequest,
};

const DEFAULT_ENDPOINT: &str = "http://127.0.0.1:8787";

#[derive(Clone, Debug, PartialEq, Eq)]
pub enum CliCommand {
    Status {
        endpoint: String,
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

pub fn get_status(endpoint: &str) -> Result<String, String> {
    let url = endpoint_url(endpoint, "/v1/status")?;
    http_request("GET", &url, None)
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
    let url = endpoint_url(endpoint, "/v1/command")?;
    let body = serde_json::to_string_pretty(&body)
        .map_err(|err| format!("Could not encode command JSON: {err}"))?;
    http_request("POST", &url, Some(&body))
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
        "  tooling-status [--json]".to_string(),
        "  devices [--json]".to_string(),
        "  device-status --serial SERIAL [--json]".to_string(),
        "  bridge-forward --serial SERIAL [--host-port 8787] [--device-port 8787] [--json]"
            .to_string(),
        format!(
            "  install-panel --serial SERIAL [--apk {}] [--json]",
            device::DEFAULT_PANEL_APK_PATH
        ),
        "  open-block --block 1|2|3 --session-id ID --participant-ref REF [--language-code en] [--endpoint URL] [--command-id ID] [--debug-auto-submit] [--debug-command-script SCRIPT] [--debug-command-interval-ms MS]".to_string(),
        "  dismiss --session-id ID [--endpoint URL] [--command-id ID]".to_string(),
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
}
