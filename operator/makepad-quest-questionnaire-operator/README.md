# Makepad Quest Questionnaire Operator

Windows Makepad GUI for sending questionnaire commands to a Quest-side Rusty
Morphospace bridge. It also includes a passive Quest readiness lane for ADB
tooling, device selection, battery/controller/foreground snapshots, and bridge
port forwarding.

The operator app does not launch the Android panel directly. It sends bridge
commands over HTTP. The on-Quest Rusty Morphospace/caller app stays responsible
for launching the panel with the existing `quest.questionnaire.v1` explicit
intent, caller-owned `content://` result URI, and completion `PendingIntent`.
ADB support is for readiness and evidence only.

## Run

```powershell
cargo run --manifest-path operator\makepad-quest-questionnaire-operator\Cargo.toml
```

The default endpoint is `http://127.0.0.1:8787` for local bridge development.
For headset use, set the endpoint field to the Quest bridge URL, for example
`http://<quest-ip>:8787`.

If you want to build against a local Makepad/Morphospace checkout, add a local
Cargo patch outside the repository, for example in user-level Cargo config.
Do not commit machine-local paths to this public repo.

The committed Makepad source pin is recorded in `MakepadBaseline.toml`. This
operator uses the Morphospace Makepad 2.0 fork and script API. The reusable
field template opts into the fork's `TextInput` inner-alignment mode so text
can be centered without per-field vertical padding or `temp_y_shift` tweaks.

## CLI Equivalents

Every GUI action has an equivalent CLI command:

```powershell
cargo run --manifest-path operator\makepad-quest-questionnaire-operator\Cargo.toml --bin quest-questionnaire-operator-cli -- status --endpoint http://127.0.0.1:8787
cargo run --manifest-path operator\makepad-quest-questionnaire-operator\Cargo.toml --bin quest-questionnaire-operator-cli -- tooling-status --json
cargo run --manifest-path operator\makepad-quest-questionnaire-operator\Cargo.toml --bin quest-questionnaire-operator-cli -- devices --json
cargo run --manifest-path operator\makepad-quest-questionnaire-operator\Cargo.toml --bin quest-questionnaire-operator-cli -- device-status --serial <quest-serial> --json
cargo run --manifest-path operator\makepad-quest-questionnaire-operator\Cargo.toml --bin quest-questionnaire-operator-cli -- bridge-forward --serial <quest-serial> --host-port 8787 --device-port 8787
cargo run --manifest-path operator\makepad-quest-questionnaire-operator\Cargo.toml --bin quest-questionnaire-operator-cli -- install-target-apk --serial <quest-serial> --apk path\to\target-runtime.apk --json
cargo run --manifest-path operator\makepad-quest-questionnaire-operator\Cargo.toml --bin quest-questionnaire-operator-cli -- launch-target-runtime --serial <quest-serial> --package io.github.example.target --json
cargo run --manifest-path operator\makepad-quest-questionnaire-operator\Cargo.toml --bin quest-questionnaire-operator-cli -- pull-target-session --serial <quest-serial> --package io.github.example.target --remote-relative files/runtime_csv/participant-P001/session-001 --out artifacts\device-session-pull --json
cargo run --manifest-path operator\makepad-quest-questionnaire-operator\Cargo.toml --bin quest-questionnaire-operator-cli -- preflight-runtime --protocol-version target.runtime.operator.v1 --runtime-kind target_quest_apk --runtime-package io.github.example.target --require-actions start_session,open_questionnaire,pull_session --require-explicit-pull --json
cargo run --manifest-path operator\makepad-quest-questionnaire-operator\Cargo.toml --bin quest-questionnaire-operator-cli -- open-block --block 1 --session-id maia-spatial-session-001 --participant-ref P001 --language-code en --endpoint http://127.0.0.1:8787
cargo run --manifest-path operator\makepad-quest-questionnaire-operator\Cargo.toml --bin quest-questionnaire-operator-cli -- open-block --block 2 --session-id maia-spatial-session-001 --participant-ref P001 --language-code en --endpoint http://127.0.0.1:8787
cargo run --manifest-path operator\makepad-quest-questionnaire-operator\Cargo.toml --bin quest-questionnaire-operator-cli -- open-block --block 3 --session-id maia-spatial-session-001 --participant-ref P001 --language-code en --endpoint http://127.0.0.1:8787
cargo run --manifest-path operator\makepad-quest-questionnaire-operator\Cargo.toml --bin quest-questionnaire-operator-cli -- dismiss --session-id maia-spatial-session-001 --endpoint http://127.0.0.1:8787
cargo run --manifest-path operator\makepad-quest-questionnaire-operator\Cargo.toml --bin quest-questionnaire-operator-cli -- start-session --session-id target-session-001 --participant-ref P001 --protocol-version target.runtime.operator.v1 --runtime-kind target_quest_apk --endpoint http://127.0.0.1:8787 --runtime-build-tag peripersonal-apk-condition-a --source-scene-path Assets/Scenes/Space.unity --audit-dir artifacts\operator-audit
cargo run --manifest-path operator\makepad-quest-questionnaire-operator\Cargo.toml --bin quest-questionnaire-operator-cli -- mark-timing-event --session-id target-session-001 --marker-name condition_start --marker-detail "Operator marker" --protocol-version target.runtime.operator.v1 --runtime-kind target_quest_apk --endpoint http://127.0.0.1:8787 --audit-dir artifacts\operator-audit
cargo run --manifest-path operator\makepad-quest-questionnaire-operator\Cargo.toml --bin quest-questionnaire-operator-cli -- open-questionnaire --session-id target-session-001 --participant-ref P001 --study-id target-study --questionnaire-id target-questionnaire-v1 --open-stage target:intro --screen-sequence target:intro,target:rating --protocol-version target.runtime.operator.v1 --runtime-kind target_quest_apk --endpoint http://127.0.0.1:8787 --audit-dir artifacts\operator-audit
cargo run --manifest-path operator\makepad-quest-questionnaire-operator\Cargo.toml --bin quest-questionnaire-operator-cli -- stop-session --session-id target-session-001 --protocol-version target.runtime.operator.v1 --runtime-kind target_quest_apk --endpoint http://127.0.0.1:8787 --audit-dir artifacts\operator-audit
cargo run --manifest-path operator\makepad-quest-questionnaire-operator\Cargo.toml --bin quest-questionnaire-operator-cli -- pull-session --session-id target-session-001 --remote-relative files/runtime_csv/participant-P001/target-session-001 --protocol-version target.runtime.operator.v1 --runtime-kind target_quest_apk --endpoint http://127.0.0.1:8787 --audit-dir artifacts\operator-audit
cargo run --manifest-path operator\makepad-quest-questionnaire-operator\Cargo.toml --bin quest-questionnaire-operator-cli -- post-command --file path\to\private-command.json --endpoint http://127.0.0.1:8787 --audit-dir artifacts\operator-audit
cargo run --manifest-path operator\makepad-quest-questionnaire-operator\Cargo.toml --bin quest-questionnaire-operator-cli -- proof-run --block 2 --session-id maia-spatial-session-001 --participant-ref P001 --language-code en --endpoint http://127.0.0.1:8787 --out artifacts\operator-proof-run
```

The GUI's endpoint, session, participant, and language fields map to the
matching CLI flags. `status` is the CLI equivalent of **Poll**. The GUI's
Quest controls map to `tooling-status`, `devices`, `device-status`, and
`bridge-forward`.

The GUI's **Target Runtime** panel sends the same downstream-runtime command
envelopes as `start-session`, `mark-timing-event`, `open-questionnaire`,
`stop-session`, and `pull-session`. It also has a **Preflight** button that
polls `GET /v1/status` and applies the same target-runtime checks as the
`preflight-runtime` CLI helper. Fill in the target runtime protocol, kind,
package, study, condition, questionnaire, stage, marker, and remote session
folder fields for the selected target APK, then run **Preflight** after
`Forward`/`Poll` and before mutating runtime commands. The GUI runtime command
buttons require a passed preflight for the current protocol/kind/package fields.
These controls post to the same low-rate `POST /v1/command` bridge route
without launching the questionnaire panel directly from Windows. Keep private
runtime protocol ids, package names, APK hashes, and study-specific stage maps
in local/private fixtures rather than in this public operator repo.

For peripersonal APK sessions, pass `--runtime-build-tag` and
`--source-scene-path Assets/Scenes/Space.unity` on `start-session` so Unity
persists the exact APK variant label in the pulled session bundle. These are
recording provenance fields only; they do not select conditions inside the
Unity app.

`preflight-runtime` is the CLI-only target-runtime safety check. It polls
`GET /v1/status` and fails if the advertised runtime kind, package, operator
protocol, required actions, or requested capabilities do not match the expected
target before you send mutating runtime commands.

When the target runtime reports an Android app-specific `session_dir` in
`GET /v1/status` or a command response, the GUI derives the ADB pull-relative
path such as `files/runtime_csv/participant-P001/session-001` and fills the
**Remote** field. This is only a convenience for the explicit `pull-session` /
`pull-target-session` workflow; the operator still chooses when to export.

The GUI can also load a local operator profile JSON by path. The loader only
reads the generic top-level `makepad_gui_fields` object, with keys matching the
GUI field names such as `endpoint`, `session`, `participant`, `adb_serial`,
`host_port`, `quest_port`, `runtime_protocol`, `runtime_kind`,
`runtime_package`, `runtime_study`, `runtime_condition`,
`runtime_build_tag`, `runtime_source_scene`, `runtime_questionnaire`,
`runtime_stage`, `runtime_marker`, and `runtime_remote`.

`post-command` remains CLI-only for replaying reviewed private fixture JSON.

The `install-target-apk` and `launch-target-runtime` helpers are setup and
foregrounding tools only. They do not launch the questionnaire panel and do not
replace the caller-owned result URI flow.

The `pull-target-session` helper is the explicit export step for target runtime
session evidence. It pulls from the target app's app-specific Quest storage
under `/sdcard/Android/data/<package>/...` into an operator-selected local
folder; do not use it for implicit background collection.

The `pull-session` HTTP helper is separate from that ADB copy step. It sends a
low-rate runtime command with an `export_request` section so the on-Quest
runtime can declare or prepare the app-private `files/runtime_csv` bundle. It
does not pull files by itself.

The default runtime export expected-file list covers the additive Unity session
bundle, including `clock_alignment_samples.csv` for LSL clock-probe echo
evidence. Timing samples should stay in LSL/CSV rather than HTTP command
payloads.

When `--audit-dir` is provided, runtime command helpers append a local
`command_audit.jsonl` file with request, response, timing, acceptance, and error
fields. Keep real audit folders and participant session exports out of source
control.

The GUI/CLI command surface is also listed in `src/command_surface.rs` and
covered by a unit test so new GUI actions do not silently lose CLI parity.

## Bridge Contract

The Quest-side bridge should expose:

```text
GET  /v1/status
POST /v1/command
```

`GET /v1/status` returns:

```json
{
  "protocol_version": "quest.questionnaire.operator.v1",
  "bridge": {
    "app": "rusty-morphospace",
    "version": "0.1.0",
    "device_label": "quest-lab"
  },
  "foreground": {
    "xr_app_foreground": true,
    "panel_foreground": false,
    "foreground_package": "io.github.mesmerprism.rustymorphospace",
    "foreground_activity": ".MainActivity",
    "questionnaire_id": null,
    "open_stage": null
  },
  "message": "ready"
}
```

Target-runtime bridges may additionally advertise their downstream runtime
contract and capabilities. The GUI parses these fields when present and shows a
runtime summary after **Poll**:

```json
{
  "protocol_version": "viscereality.peripersonal.operator.v1",
  "target": {
    "runtime_kind": "unity_quest_apk",
    "runtime_package": "com.example.target",
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
    "timing_transport": "lsl_sussex_clock_probe"
  },
  "capabilities": {
    "command_actions": [
      "start_session",
      "stop_session",
      "mark_timing_event",
      "open_questionnaire",
      "pull_session"
    ],
    "additive_recording": true,
    "app_private_session_bundle": true,
    "explicit_pull_required": true,
    "questionnaire_panel_launch": true,
    "questionnaire_result_callback_ingest": true,
    "lsl_clock_alignment": true
  }
}
```

`POST /v1/command` receives a command body with a `panel_request` section that
maps directly to the existing panel launch fields:

```json
{
  "protocol_version": "quest.questionnaire.operator.v1",
  "command_id": "operator-000001",
  "action": "open_questionnaire",
  "command_name": "maia_spatial.block2",
  "panel_request": {
    "protocol_version": "quest.questionnaire.v1",
    "session_id": "maia-spatial-session-001",
    "study_id": "maia-spatial",
    "schema_id": "maia2-spatial-frame-questionnaire-v1",
    "open_stage": "maia_spatial:spatial_frame_reference_1",
    "screen_sequence": ["maia_spatial:spatial_frame_reference_1"],
    "participant_ref": "P001",
    "questionnaire_state": {
      "language_code": "en"
    },
    "caller_hint": {
      "engine": "rusty-morphospace",
      "transport": "makepad-windows-operator"
    }
  }
}
```

The response shape mirrors status and adds whether the command was accepted:

```json
{
  "protocol_version": "quest.questionnaire.operator.v1",
  "accepted": true,
  "message": "launch requested",
  "foreground": {
    "xr_app_foreground": false,
    "panel_foreground": true,
    "foreground_package": "io.github.mesmerprism.questquestionnaire",
    "foreground_activity": ".panel.QuestionnaireActivity",
    "questionnaire_id": "maia2-spatial-frame-questionnaire-v1",
    "open_stage": "maia_spatial:spatial_frame_reference_1"
  }
}
```
