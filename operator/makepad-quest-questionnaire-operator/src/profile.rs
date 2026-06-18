use std::fs;
use std::path::Path;

use serde::de::Error as DeError;
use serde::{Deserialize, Deserializer, Serialize};

#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct OperatorGuiProfile {
    #[serde(default)]
    pub protocol_version: String,
    #[serde(default)]
    pub profile_id: String,
    #[serde(default)]
    pub profile_scope: String,
    #[serde(default)]
    pub makepad_gui_fields: OperatorGuiProfileFields,
}

#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct OperatorGuiProfileFields {
    #[serde(default)]
    pub endpoint: String,
    #[serde(default)]
    pub session: String,
    #[serde(default)]
    pub participant: String,
    #[serde(default)]
    pub language: String,
    #[serde(default)]
    pub adb_serial: String,
    #[serde(default, deserialize_with = "deserialize_stringish")]
    pub host_port: String,
    #[serde(default, deserialize_with = "deserialize_stringish")]
    pub quest_port: String,
    #[serde(default)]
    pub runtime_protocol: String,
    #[serde(default)]
    pub runtime_kind: String,
    #[serde(default)]
    pub runtime_package: String,
    #[serde(default)]
    pub runtime_study: String,
    #[serde(default)]
    pub runtime_condition: String,
    #[serde(default)]
    pub runtime_build_tag: String,
    #[serde(default)]
    pub runtime_source_scene: String,
    #[serde(default)]
    pub runtime_questionnaire: String,
    #[serde(default)]
    pub runtime_stage: String,
    #[serde(default)]
    pub runtime_marker: String,
    #[serde(default)]
    pub runtime_remote: String,
}

pub fn load_operator_gui_profile(path: &Path) -> Result<OperatorGuiProfile, String> {
    let raw = fs::read_to_string(path)
        .map_err(|err| format!("Could not read operator profile {}: {err}", path.display()))?;
    parse_operator_gui_profile(&raw)
}

pub fn parse_operator_gui_profile(raw: &str) -> Result<OperatorGuiProfile, String> {
    serde_json::from_str(raw).map_err(|err| format!("Could not parse operator profile JSON: {err}"))
}

fn deserialize_stringish<'de, D>(deserializer: D) -> Result<String, D::Error>
where
    D: Deserializer<'de>,
{
    let value = Option::<serde_json::Value>::deserialize(deserializer)?;
    match value {
        None | Some(serde_json::Value::Null) => Ok(String::new()),
        Some(serde_json::Value::String(value)) => Ok(value),
        Some(serde_json::Value::Number(value)) => Ok(value.to_string()),
        Some(serde_json::Value::Bool(value)) => Ok(value.to_string()),
        Some(value) => Err(D::Error::custom(format!(
            "expected string, number, or bool, got {value}"
        ))),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_profile_fields_and_ignores_private_context() {
        let profile = parse_operator_gui_profile(
            r#"{
                "protocol_version": "example.operator_gui_profile.v1",
                "profile_id": "local-forwarded",
                "generated_from": {
                    "private": "ignored by public loader"
                },
                "makepad_gui_fields": {
                    "endpoint": "http://127.0.0.1:8787",
                    "session": "session-001",
                    "participant": "P001",
                    "language": "en",
                    "adb_serial": "<quest-serial>",
                    "host_port": 8787,
                    "quest_port": "8787",
                    "runtime_protocol": "example.runtime.operator.v1",
                    "runtime_kind": "unity_quest_apk",
                    "runtime_package": "io.github.example.target",
                    "runtime_study": "example-study",
                    "runtime_condition": "condition-a",
                    "runtime_build_tag": "apk-condition-a",
                    "runtime_source_scene": "Assets/Scenes/Space.unity",
                    "runtime_questionnaire": "questionnaire-v1",
                    "runtime_stage": "example:post_condition",
                    "runtime_marker": "condition_start",
                    "runtime_remote": "files/runtime_csv/participant-P001/session-001"
                }
            }"#,
        )
        .unwrap();

        assert_eq!(profile.profile_id, "local-forwarded");
        assert_eq!(profile.makepad_gui_fields.endpoint, "http://127.0.0.1:8787");
        assert_eq!(profile.makepad_gui_fields.host_port, "8787");
        assert_eq!(profile.makepad_gui_fields.quest_port, "8787");
        assert_eq!(
            profile.makepad_gui_fields.runtime_remote,
            "files/runtime_csv/participant-P001/session-001"
        );
        assert_eq!(
            profile.makepad_gui_fields.runtime_build_tag,
            "apk-condition-a"
        );
        assert_eq!(
            profile.makepad_gui_fields.runtime_source_scene,
            "Assets/Scenes/Space.unity"
        );
    }

    #[test]
    fn missing_fields_default_to_empty_strings() {
        let profile =
            parse_operator_gui_profile(r#"{"makepad_gui_fields":{"session":"s1"}}"#).unwrap();

        assert_eq!(profile.makepad_gui_fields.session, "s1");
        assert!(profile.makepad_gui_fields.endpoint.is_empty());
        assert!(profile.makepad_gui_fields.runtime_package.is_empty());
    }
}
