use std::env;
use std::ffi::OsString;
use std::path::{Path, PathBuf};
use std::process::Command;
use std::time::{Duration, Instant};

use serde::{Deserialize, Serialize};

pub const DEFAULT_PANEL_APK_PATH: &str =
    "app/build/outputs/apk/minimal/debug/app-minimal-debug.apk";
pub const DEFAULT_TARGET_SESSION_REMOTE_RELATIVE: &str = "files/runtime_csv";

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct ToolProbe {
    pub name: String,
    pub available: bool,
    pub path: Option<String>,
    pub source: Option<String>,
    pub detail: String,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct ToolingStatus {
    pub adb: ToolProbe,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct QuestDevice {
    pub serial: String,
    pub state: String,
    pub model: Option<String>,
    pub product: Option<String>,
}

impl QuestDevice {
    pub fn label(&self) -> String {
        self.model
            .as_ref()
            .filter(|value| !value.trim().is_empty())
            .map(|model| format!("{model} ({})", self.serial))
            .unwrap_or_else(|| self.serial.clone())
    }
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct QuestSnapshot {
    pub serial: String,
    pub model: String,
    pub headset_battery_level: Option<u8>,
    pub headset_battery_status: String,
    pub wakefulness: String,
    pub interactive: Option<bool>,
    pub display_power_state: Option<String>,
    pub foreground_component: Option<String>,
    pub focused_window: Option<String>,
    pub proximity: Option<QuestProximityStatus>,
    pub controllers: Vec<QuestControllerStatus>,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct QuestControllerStatus {
    pub hand: String,
    pub battery_level: Option<u8>,
    pub connection_state: Option<String>,
    pub device_id: Option<String>,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct QuestProximityStatus {
    pub virtual_state: Option<String>,
    pub hold_active: Option<bool>,
    pub headset_state: Option<String>,
    pub detail: String,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct CommandRun {
    pub file: String,
    pub args: Vec<String>,
    pub exit_code: Option<i32>,
    pub stdout: String,
    pub stderr: String,
    pub duration_ms: u128,
}

impl CommandRun {
    pub fn succeeded(&self) -> bool {
        self.exit_code == Some(0)
    }

    pub fn condensed_output(&self) -> String {
        let text = [self.stdout.trim(), self.stderr.trim()]
            .into_iter()
            .filter(|value| !value.is_empty())
            .collect::<Vec<_>>()
            .join("\n");
        if text.len() > 800 {
            format!("{}...", &text[..797])
        } else {
            text
        }
    }
}

pub fn tooling_status() -> ToolingStatus {
    let adb = match locate_adb() {
        Some(locator) => match run_command(&locator.path, &["version"], Duration::from_secs(8)) {
            Ok(run) if run.succeeded() => ToolProbe {
                name: "Android Debug Bridge".to_string(),
                available: true,
                path: Some(locator.path.to_string_lossy().to_string()),
                source: Some(locator.source),
                detail: first_non_empty_line(&run.stdout)
                    .unwrap_or_else(|| "adb is available".to_string()),
            },
            Ok(run) => ToolProbe {
                name: "Android Debug Bridge".to_string(),
                available: false,
                path: Some(locator.path.to_string_lossy().to_string()),
                source: Some(locator.source),
                detail: run.condensed_output(),
            },
            Err(err) => ToolProbe {
                name: "Android Debug Bridge".to_string(),
                available: false,
                path: Some(locator.path.to_string_lossy().to_string()),
                source: Some(locator.source),
                detail: err,
            },
        },
        None => ToolProbe {
            name: "Android Debug Bridge".to_string(),
            available: false,
            path: None,
            source: None,
            detail: "adb.exe was not found. Set QUEST_QUESTIONNAIRE_ADB, RUSTY_XR_ADB, ANDROID_HOME, or put adb on PATH.".to_string(),
        },
    };
    ToolingStatus { adb }
}

pub fn list_devices() -> Result<Vec<QuestDevice>, String> {
    let adb = require_adb()?;
    let run = run_adb(&adb, &["devices", "-l"], Duration::from_secs(10))?;
    if !run.succeeded() {
        return Err(run.condensed_output());
    }
    Ok(parse_devices(&run.stdout))
}

pub fn get_device_snapshot(serial: &str) -> Result<QuestSnapshot, String> {
    let serial = serial.trim();
    if serial.is_empty() {
        return Err("Device serial is required.".to_string());
    }
    let adb = require_adb()?;
    let model = adb_shell_text(&adb, serial, &["getprop", "ro.product.model"])?;
    let battery = adb_shell_text(&adb, serial, &["dumpsys", "battery"])?;
    let power = adb_shell_text(&adb, serial, &["dumpsys", "power"])?;
    let tracking = adb_shell_optional(&adb, serial, &["dumpsys", "tracking"]);
    let proximity = adb_shell_optional(&adb, serial, &["dumpsys", "vrpowermanager"]);
    let window = adb_shell_optional(&adb, serial, &["dumpsys", "window"]);

    Ok(QuestSnapshot {
        serial: serial.to_string(),
        model: if model.trim().is_empty() {
            "unknown Quest device".to_string()
        } else {
            model.trim().to_string()
        },
        headset_battery_level: parse_battery_level(&battery),
        headset_battery_status: parse_battery_status(&battery),
        wakefulness: parse_wakefulness(&power),
        interactive: parse_interactive(&power),
        display_power_state: parse_display_power_state(&power),
        foreground_component: window.as_deref().and_then(parse_focused_app),
        focused_window: window.as_deref().and_then(parse_current_focus),
        proximity: proximity.as_deref().and_then(parse_proximity),
        controllers: tracking
            .as_deref()
            .map(parse_controller_statuses)
            .unwrap_or_default(),
    })
}

pub fn forward_bridge(
    serial: &str,
    host_port: u16,
    device_port: u16,
) -> Result<CommandRun, String> {
    let serial = serial.trim();
    if serial.is_empty() {
        return Err("Device serial is required.".to_string());
    }
    if host_port == 0 || device_port == 0 {
        return Err("Bridge ports must be between 1 and 65535.".to_string());
    }
    let adb = require_adb()?;
    let host = format!("tcp:{host_port}");
    let device = format!("tcp:{device_port}");
    let args = ["-s", serial, "forward", host.as_str(), device.as_str()];
    let run = run_adb(&adb, &args, Duration::from_secs(15))?;
    if run.succeeded() {
        Ok(run)
    } else {
        Err(run.condensed_output())
    }
}

pub fn install_apk(serial: &str, apk_path: &Path) -> Result<CommandRun, String> {
    let serial = serial.trim();
    if serial.is_empty() {
        return Err("Device serial is required.".to_string());
    }
    if apk_path.as_os_str().is_empty() {
        return Err("APK path is required.".to_string());
    }
    if !apk_path.is_file() {
        return Err(format!("APK file was not found: {}", apk_path.display()));
    }

    let adb = require_adb()?;
    let apk = apk_path.to_string_lossy().to_string();
    let args = ["-s", serial, "install", "-r", "-d", apk.as_str()];
    let run = run_adb(&adb, &args, Duration::from_secs(180))?;
    if run.succeeded() {
        Ok(run)
    } else {
        Err(run.condensed_output())
    }
}

pub fn launch_package(
    serial: &str,
    package_name: &str,
    activity: Option<&str>,
) -> Result<CommandRun, String> {
    let serial = serial.trim();
    if serial.is_empty() {
        return Err("Device serial is required.".to_string());
    }
    let package_name = package_name.trim();
    if package_name.is_empty() {
        return Err("Package name is required.".to_string());
    }

    let adb = require_adb()?;
    let args = launch_package_args(serial, package_name, activity);
    let arg_refs = args.iter().map(String::as_str).collect::<Vec<_>>();
    let run = run_adb(&adb, &arg_refs, Duration::from_secs(20))?;
    if run.succeeded() {
        Ok(run)
    } else {
        Err(run.condensed_output())
    }
}

pub fn pull_target_session(
    serial: &str,
    package_name: &str,
    remote_relative: &str,
    output_dir: &Path,
) -> Result<CommandRun, String> {
    let serial = serial.trim();
    if serial.is_empty() {
        return Err("Device serial is required.".to_string());
    }
    let package_name = package_name.trim();
    if package_name.is_empty() {
        return Err("Package name is required.".to_string());
    }
    if output_dir.as_os_str().is_empty() {
        return Err("Output folder is required.".to_string());
    }

    std::fs::create_dir_all(output_dir).map_err(|err| {
        format!(
            "Could not create output folder {}: {err}",
            output_dir.display()
        )
    })?;

    let adb = require_adb()?;
    let remote = target_session_remote_path(package_name, remote_relative)?;
    let output = output_dir.to_string_lossy().to_string();
    let args = ["-s", serial, "pull", remote.as_str(), output.as_str()];
    let run = run_adb(&adb, &args, Duration::from_secs(180))?;
    if run.succeeded() {
        Ok(run)
    } else {
        Err(run.condensed_output())
    }
}

fn target_session_remote_path(package_name: &str, remote_relative: &str) -> Result<String, String> {
    let package_name = package_name.trim();
    if package_name.is_empty() {
        return Err("Package name is required.".to_string());
    }

    let relative = remote_relative.trim().replace('\\', "/");
    let relative = relative.trim_matches('/');
    if relative.is_empty() {
        return Err("Remote relative path is required.".to_string());
    }
    if relative
        .split('/')
        .any(|segment| segment.is_empty() || segment == "." || segment == "..")
    {
        return Err(
            "Remote relative path must not contain empty, '.', or '..' segments.".to_string(),
        );
    }

    Ok(format!("/sdcard/Android/data/{package_name}/{relative}"))
}

fn launch_package_args(serial: &str, package_name: &str, activity: Option<&str>) -> Vec<String> {
    match activity.map(str::trim).filter(|value| !value.is_empty()) {
        Some(activity) => vec![
            "-s".to_string(),
            serial.to_string(),
            "shell".to_string(),
            "am".to_string(),
            "start".to_string(),
            "-n".to_string(),
            format!("{package_name}/{activity}"),
        ],
        None => vec![
            "-s".to_string(),
            serial.to_string(),
            "shell".to_string(),
            "monkey".to_string(),
            "-p".to_string(),
            package_name.to_string(),
            "-c".to_string(),
            "android.intent.category.LAUNCHER".to_string(),
            "1".to_string(),
        ],
    }
}

pub fn format_tooling_text(status: &ToolingStatus) -> String {
    if status.adb.available {
        format!(
            "ADB available ({})",
            status
                .adb
                .path
                .as_deref()
                .unwrap_or(status.adb.detail.as_str())
        )
    } else {
        format!("ADB unavailable: {}", status.adb.detail)
    }
}

pub fn format_devices_text(devices: &[QuestDevice]) -> String {
    if devices.is_empty() {
        "No ADB devices visible.".to_string()
    } else {
        devices
            .iter()
            .map(|device| format!("{} - {}", device.label(), device.state))
            .collect::<Vec<_>>()
            .join("\n")
    }
}

pub fn format_snapshot_text(snapshot: &QuestSnapshot) -> String {
    let battery = match snapshot.headset_battery_level {
        Some(level) if snapshot.headset_battery_status.is_empty() => format!("{level}%"),
        Some(level) => format!("{level}% {}", snapshot.headset_battery_status),
        None => "battery unknown".to_string(),
    };
    let foreground = snapshot
        .foreground_component
        .as_deref()
        .or(snapshot.focused_window.as_deref())
        .unwrap_or("foreground unknown");
    let controllers = if snapshot.controllers.is_empty() {
        "controllers unavailable".to_string()
    } else {
        snapshot
            .controllers
            .iter()
            .map(|controller| {
                let battery = controller
                    .battery_level
                    .map(|level| format!("{level}%"))
                    .unwrap_or_else(|| "n/a".to_string());
                let connection = controller
                    .connection_state
                    .as_deref()
                    .unwrap_or("connection unknown");
                format!("{} {battery} {connection}", controller.hand)
            })
            .collect::<Vec<_>>()
            .join("; ")
    };
    let proximity = snapshot
        .proximity
        .as_ref()
        .map(|value| value.detail.as_str())
        .unwrap_or("proximity unavailable");
    format!(
        "{}\n{}\nWake: {}; display: {}\nForeground: {}\nControllers: {}\n{}",
        snapshot.model,
        battery,
        snapshot.wakefulness,
        snapshot.display_power_state.as_deref().unwrap_or("unknown"),
        foreground,
        controllers,
        proximity
    )
}

fn require_adb() -> Result<PathBuf, String> {
    locate_adb().map(|locator| locator.path).ok_or_else(|| {
        "adb.exe was not found. Run tooling-status for discovery details.".to_string()
    })
}

#[derive(Clone, Debug)]
struct ToolLocator {
    path: PathBuf,
    source: String,
}

fn locate_adb() -> Option<ToolLocator> {
    for env_name in ["QUEST_QUESTIONNAIRE_ADB", "RUSTY_XR_ADB"] {
        if let Some(path) = env::var_os(env_name).map(PathBuf::from) {
            if path.is_file() {
                return Some(ToolLocator {
                    path,
                    source: format!("env:{env_name}"),
                });
            }
        }
    }

    if let Some(root) = env::var_os("ANDROID_HOME").or_else(|| env::var_os("ANDROID_SDK_ROOT")) {
        let path = PathBuf::from(root)
            .join("platform-tools")
            .join(adb_exe_name());
        if path.is_file() {
            return Some(ToolLocator {
                path,
                source: "android-sdk".to_string(),
            });
        }
    }

    if let Some(local_app_data) = env::var_os("LOCALAPPDATA") {
        let path = PathBuf::from(local_app_data)
            .join("RustyXrCompanion")
            .join("tooling")
            .join("platform-tools")
            .join("current")
            .join("platform-tools")
            .join(adb_exe_name());
        if path.is_file() {
            return Some(ToolLocator {
                path,
                source: "rusty-xr-companion-cache".to_string(),
            });
        }
    }

    if command_exists_on_path(adb_exe_name()) {
        return Some(ToolLocator {
            path: PathBuf::from(adb_exe_name()),
            source: "PATH".to_string(),
        });
    }

    None
}

fn command_exists_on_path(command: &str) -> bool {
    env::var_os("PATH")
        .map(|paths| env::split_paths(&paths).any(|path| path.join(command).is_file()))
        .unwrap_or(false)
}

fn adb_exe_name() -> &'static str {
    if cfg!(windows) {
        "adb.exe"
    } else {
        "adb"
    }
}

fn run_adb(adb: &PathBuf, args: &[&str], timeout: Duration) -> Result<CommandRun, String> {
    run_command(adb, args, timeout)
}

fn adb_shell_text(adb: &PathBuf, serial: &str, shell_args: &[&str]) -> Result<String, String> {
    let mut args = vec!["-s", serial, "shell"];
    args.extend_from_slice(shell_args);
    let run = run_adb(adb, &args, Duration::from_secs(20))?;
    if run.succeeded() {
        Ok(run.stdout.trim().to_string())
    } else {
        Err(run.condensed_output())
    }
}

fn adb_shell_optional(adb: &PathBuf, serial: &str, shell_args: &[&str]) -> Option<String> {
    adb_shell_text(adb, serial, shell_args).ok()
}

fn run_command(path: &PathBuf, args: &[&str], _timeout: Duration) -> Result<CommandRun, String> {
    let start = Instant::now();
    let output = Command::new(path)
        .args(args)
        .output()
        .map_err(|err| format!("Could not run {}: {err}", path.to_string_lossy()))?;
    Ok(CommandRun {
        file: path.to_string_lossy().to_string(),
        args: args.iter().map(|value| (*value).to_string()).collect(),
        exit_code: output.status.code(),
        stdout: String::from_utf8_lossy(&output.stdout).to_string(),
        stderr: String::from_utf8_lossy(&output.stderr).to_string(),
        duration_ms: start.elapsed().as_millis(),
    })
}

fn first_non_empty_line(text: &str) -> Option<String> {
    text.lines()
        .map(str::trim)
        .find(|line| !line.is_empty())
        .map(str::to_string)
}

fn parse_devices(output: &str) -> Vec<QuestDevice> {
    output
        .lines()
        .skip(1)
        .filter_map(|line| {
            let parts = line.split_whitespace().collect::<Vec<_>>();
            if parts.len() < 2 {
                return None;
            }
            Some(QuestDevice {
                serial: parts[0].to_string(),
                state: parts[1].to_string(),
                model: labeled_part(&parts, "model:"),
                product: labeled_part(&parts, "product:"),
            })
        })
        .collect()
}

fn labeled_part(parts: &[&str], prefix: &str) -> Option<String> {
    parts
        .iter()
        .find_map(|part| part.strip_prefix(prefix).map(str::to_string))
}

fn parse_battery_level(output: &str) -> Option<u8> {
    labeled_line(output, "level:").and_then(|value| value.parse::<u8>().ok())
}

fn parse_battery_status(output: &str) -> String {
    match labeled_line(output, "status:").as_deref() {
        Some("2") => "charging".to_string(),
        Some("3") => "discharging".to_string(),
        Some("4") => "not charging".to_string(),
        Some("5") => "full".to_string(),
        Some(value) if !value.is_empty() => value.to_string(),
        _ => "unknown".to_string(),
    }
}

fn parse_wakefulness(output: &str) -> String {
    labeled_line(output, "mWakefulness=")
        .or_else(|| parse_display_power_state(output))
        .unwrap_or_else(|| "unknown".to_string())
}

fn parse_interactive(output: &str) -> Option<bool> {
    labeled_line(output, "mInteractive=").and_then(|value| value.parse::<bool>().ok())
}

fn parse_display_power_state(output: &str) -> Option<String> {
    output.lines().find_map(|line| {
        let trimmed = line.trim();
        let (_, rest) = trimmed.split_once("Display Power:")?;
        let (_, state) = rest.split_once("state=")?;
        Some(state.trim().to_string())
    })
}

fn labeled_line(output: &str, label: &str) -> Option<String> {
    output.lines().find_map(|line| {
        let trimmed = line.trim();
        trimmed
            .strip_prefix(label)
            .map(str::trim)
            .filter(|value| !value.is_empty())
            .map(str::to_string)
    })
}

fn parse_controller_statuses(output: &str) -> Vec<QuestControllerStatus> {
    let mut statuses = Vec::new();
    let mut current_hand: Option<String> = None;
    for raw_line in output.lines() {
        let line = raw_line.trim();
        if starts_with_word(line, "Left") {
            current_hand = Some("Left".to_string());
        } else if starts_with_word(line, "Right") {
            current_hand = Some("Right".to_string());
        }

        let Some(hand) = current_hand.clone() else {
            continue;
        };
        let Some(entry_index) = line.find("[id:") else {
            continue;
        };
        let entry = &line[entry_index..];
        let battery = extract_segment(entry, "battery:")
            .trim_end_matches('%')
            .parse::<u8>()
            .ok();
        let connection = non_empty(extract_segment(entry, "conn:"));
        let device_id = non_empty(extract_segment(entry, "id:"));
        upsert_controller(
            &mut statuses,
            QuestControllerStatus {
                hand,
                battery_level: battery,
                connection_state: connection,
                device_id,
            },
        );
    }
    statuses.sort_by_key(|status| if status.hand == "Left" { 0 } else { 1 });
    statuses
}

fn upsert_controller(statuses: &mut Vec<QuestControllerStatus>, status: QuestControllerStatus) {
    if let Some(existing) = statuses.iter_mut().find(|item| item.hand == status.hand) {
        *existing = status;
    } else {
        statuses.push(status);
    }
}

fn starts_with_word(line: &str, word: &str) -> bool {
    line.eq_ignore_ascii_case(word)
        || line
            .strip_prefix(word)
            .map(|rest| rest.starts_with(' ') || rest.starts_with("--"))
            .unwrap_or(false)
}

fn extract_segment(value: &str, label: &str) -> String {
    let Some(index) = value.find(label) else {
        return String::new();
    };
    let start = index + label.len();
    let end = value[start..]
        .find([',', ']'])
        .map(|offset| start + offset)
        .unwrap_or(value.len());
    value[start..end].trim().to_string()
}

fn non_empty(value: String) -> Option<String> {
    if value.trim().is_empty() {
        None
    } else {
        Some(value)
    }
}

fn parse_focused_app(output: &str) -> Option<String> {
    output
        .lines()
        .filter(|line| line.contains("mFocusedApp="))
        .find_map(parse_component_from_line)
}

fn parse_current_focus(output: &str) -> Option<String> {
    output
        .lines()
        .filter(|line| line.contains("mCurrentFocus="))
        .find_map(parse_component_from_line)
}

fn parse_component_from_line(line: &str) -> Option<String> {
    line.split_whitespace()
        .rev()
        .map(|token| token.trim_matches(|c| c == '}' || c == '{' || c == ')' || c == '('))
        .find(|token| token.contains('/') && token.contains('.'))
        .map(str::to_string)
}

fn parse_proximity(output: &str) -> Option<QuestProximityStatus> {
    let virtual_state = labeled_line(output, "Virtual proximity state:");
    let headset_state =
        labeled_line(output, "Headset State:").or_else(|| labeled_line(output, "Headset state:"));
    let hold_active = virtual_state
        .as_deref()
        .map(|state| state.eq_ignore_ascii_case("CLOSE"));
    let parts = [
        virtual_state
            .as_ref()
            .map(|value| format!("proximity {value}")),
        headset_state
            .as_ref()
            .map(|value| format!("headset {value}")),
    ]
    .into_iter()
    .flatten()
    .collect::<Vec<_>>();
    if parts.is_empty() {
        None
    } else {
        Some(QuestProximityStatus {
            virtual_state,
            hold_active,
            headset_state,
            detail: parts.join("; "),
        })
    }
}

#[allow(dead_code)]
fn _os_string_to_path(value: OsString) -> PathBuf {
    PathBuf::from(value)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_devices_with_model() {
        let devices = parse_devices(
            "List of devices attached\nTEST_SERIAL device product:test_product model:Quest_Test device:foo\n",
        );

        assert_eq!(devices.len(), 1);
        assert_eq!(devices[0].serial, "TEST_SERIAL");
        assert_eq!(devices[0].model.as_deref(), Some("Quest_Test"));
    }

    #[test]
    fn parses_battery_status() {
        let output = "level: 72\nstatus: 3\n";

        assert_eq!(parse_battery_level(output), Some(72));
        assert_eq!(parse_battery_status(output), "discharging");
    }

    #[test]
    fn parses_controller_statuses() {
        let output = "Left controller\n[id: 12, conn: active, battery: 55%]\nRight controller\n[id: 13, conn: inactive, battery: 70%]\n";
        let controllers = parse_controller_statuses(output);

        assert_eq!(controllers.len(), 2);
        assert_eq!(controllers[0].hand, "Left");
        assert_eq!(controllers[0].battery_level, Some(55));
        assert_eq!(controllers[1].connection_state.as_deref(), Some("inactive"));
    }

    #[test]
    fn parses_window_focus_component() {
        let output = "mCurrentFocus=Window{abc u0 io.github.example/.MainActivity}\nmFocusedApp=ActivityRecord{def u0 io.github.other/.OtherActivity t1}";

        assert_eq!(
            parse_current_focus(output).as_deref(),
            Some("io.github.example/.MainActivity")
        );
        assert_eq!(
            parse_focused_app(output).as_deref(),
            Some("io.github.other/.OtherActivity")
        );
    }

    #[test]
    fn builds_launch_package_monkey_args() {
        assert_eq!(
            launch_package_args("SERIAL", "io.github.example", None),
            vec![
                "-s",
                "SERIAL",
                "shell",
                "monkey",
                "-p",
                "io.github.example",
                "-c",
                "android.intent.category.LAUNCHER",
                "1",
            ]
        );
    }

    #[test]
    fn builds_launch_package_activity_args() {
        assert_eq!(
            launch_package_args("SERIAL", "io.github.example", Some(".MainActivity")),
            vec![
                "-s",
                "SERIAL",
                "shell",
                "am",
                "start",
                "-n",
                "io.github.example/.MainActivity",
            ]
        );
    }

    #[test]
    fn builds_target_session_remote_path() {
        assert_eq!(
            target_session_remote_path(
                "io.github.example",
                "files/runtime_csv/participant-P001/session-001"
            )
            .unwrap(),
            "/sdcard/Android/data/io.github.example/files/runtime_csv/participant-P001/session-001"
        );
    }

    #[test]
    fn rejects_parent_segments_in_target_session_remote_path() {
        assert!(target_session_remote_path("io.github.example", "files/../secret").is_err());
    }
}
